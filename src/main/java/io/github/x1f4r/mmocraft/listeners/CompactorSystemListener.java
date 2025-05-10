package io.github.x1f4r.mmocraft.listeners;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.logging.Logger;

// Added for recipe access
import io.github.x1f4r.mmocraft.crafting.RecipeManager;
import io.github.x1f4r.mmocraft.crafting.models.CustomRecipe;
import io.github.x1f4r.mmocraft.crafting.models.RequiredItem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class CompactorSystemListener implements Listener {

    private final MMOCore core;
    private final Logger log;
    // private static final int MAX_FILTER_SLOTS = 3; // No longer needed, read from NBT

    public CompactorSystemListener(MMOCore core) {
        this.core = core;
        this.log = MMOPlugin.getMMOLogger();
    }

    @EventHandler
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        ItemStack pickedUpItemStack = event.getItem().getItemStack(); // Renamed for clarity
        Material pickedUpMaterial = pickedUpItemStack.getType();

        PlayerInventory playerInventory = player.getInventory();
        RecipeManager recipeManager = core.getRecipeManager();

        for (ItemStack item : playerInventory.getContents()) { 
            if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
                continue;
            }

            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            if (pdc.has(NBTKeys.UTILITY_ID_KEY, PersistentDataType.STRING)) {
                String utilityId = pdc.get(NBTKeys.UTILITY_ID_KEY, PersistentDataType.STRING);
                if (utilityId != null && utilityId.startsWith("personal_compactor_")) {
                    int compactorSlotCount = pdc.getOrDefault(NBTKeys.COMPACTOR_SLOT_COUNT_KEY, PersistentDataType.INTEGER, 1);
                    log.finer("Compactor " + utilityId + " has " + compactorSlotCount + " filter slots.");

                    for (int filterKeyIndex = 0; filterKeyIndex < compactorSlotCount; filterKeyIndex++) {
                        NamespacedKey filterNbtKey = NBTKeys.getCompactorFilterKey(filterKeyIndex);
                        if (pdc.has(filterNbtKey, PersistentDataType.STRING)) {
                            String filterMaterialName = pdc.get(filterNbtKey, PersistentDataType.STRING);
                            try {
                                Material filterMaterialEnum = Material.valueOf(filterMaterialName); 
                                if (filterMaterialEnum == pickedUpMaterial) {
                                    log.fine("[CompactorSystem] Player " + player.getName() +
                                            " picked up " + pickedUpMaterial.name() +
                                            " which matches filter slotKey " + filterKeyIndex + " (" + filterMaterialName +")" +
                                            " in compactor " + utilityId + ". Attempting to find compacting recipe.");

                                    CustomRecipe compactingRecipe = recipeManager.findCompactingRecipeForItem(pickedUpMaterial);
                                    if (compactingRecipe != null) {
                                        ItemStack resultItemTemplate = compactingRecipe.getResult();
                                        String resultItemId = "Unknown";
                                        if (resultItemTemplate.hasItemMeta() && resultItemTemplate.getItemMeta().getPersistentDataContainer().has(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING)) {
                                            resultItemId = resultItemTemplate.getItemMeta().getPersistentDataContainer().get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);
                                        }
                                        log.finer("[CompactorSystem] Found recipe for " + pickedUpMaterial.name() + ". Output: " + resultItemTemplate.getType() + " (ID: " + resultItemId + ") needing " + compactingRecipe.getShapelessIngredients().get(0).getAmount());

                                        RequiredItem requiredIngredient = compactingRecipe.getShapelessIngredients().get(0); 
                                        int amountNeeded = requiredIngredient.getAmount();
                                        int itemsAlreadyInInventory = countPlayerItems(player, pickedUpMaterial);
                                        int itemsInPickedUpStack = pickedUpItemStack.getAmount();
                                        int totalPotentiallyAvailable = itemsAlreadyInInventory + itemsInPickedUpStack;

                                        log.finer("[CompactorSystem] " + player.getName() + ": Needs " + amountNeeded + " " + pickedUpMaterial.name() + ". Has " + itemsAlreadyInInventory + " in inv, picking up " + itemsInPickedUpStack + ". Total available: " + totalPotentiallyAvailable);

                                        if (totalPotentiallyAvailable >= amountNeeded) {
                                            event.setCancelled(true); // Assume we manage the item fully now
                                            log.finer("[CompactorSystem] Total items available (" + totalPotentiallyAvailable + ") >= amount needed (" + amountNeeded + "). Proceeding with compaction.");

                                            int consumedFromInventory = 0;
                                            int consumedFromGroundStack = 0;

                                            // How much can we take from inventory first?
                                            int canTakeFromInventory = Math.min(itemsAlreadyInInventory, amountNeeded);
                                            if (canTakeFromInventory > 0) {
                                                if (removePlayerItems(player, pickedUpMaterial, canTakeFromInventory)) {
                                                    consumedFromInventory = canTakeFromInventory;
                                                    log.finer("[CompactorSystem] Consumed " + consumedFromInventory + " " + pickedUpMaterial.name() + " from player inventory.");
                                                } else {
                                                    log.warning("[CompactorSystem] Failed to remove " + canTakeFromInventory + " " + pickedUpMaterial.name() + " from inventory for " + player.getName() + ", though count indicated availability. Aborting compaction for this item.");
                                                    event.setCancelled(false); // Let original pickup happen
                                                    continue; // Try next filter or compactor
                                                }
                                            }

                                            int remainingNeeded = amountNeeded - consumedFromInventory;
                                            if (remainingNeeded > 0) {
                                                if (itemsInPickedUpStack >= remainingNeeded) {
                                                    consumedFromGroundStack = remainingNeeded;
                                                    log.finer("[CompactorSystem] Consumed " + consumedFromGroundStack + " " + pickedUpMaterial.name() + " from ground stack.");
                                                } else {
                                                    // This case should ideally not be reached if totalPotentiallyAvailable was sufficient.
                                                    log.warning("[CompactorSystem] Logic error: Not enough items in ground stack (" + itemsInPickedUpStack + ") to cover remaining needed (" + remainingNeeded + "). Aborting.");
                                                    // Rollback inventory removal? Too complex for now. Pickup was already cancelled.
                                                    // Best to just let the original event be cancelled and items taken from inv are lost if this branch is hit.
                                                    // Or, try to give back what was taken from inventory.
                                                    if (consumedFromInventory > 0) playerInventory.addItem(new ItemStack(pickedUpMaterial, consumedFromInventory)); // Crude rollback
                                                    event.setCancelled(false); // Let original pickup proceed
                                                    continue;
                                                }
                                            }

                                            // Give compacted item
                                            ItemStack finalResultItem = resultItemTemplate.clone();
                                            playerInventory.addItem(finalResultItem);
                                            player.sendMessage(Component.text("Compacted " + amountNeeded + " " + pickedUpMaterial.name() + " into " + finalResultItem.getAmount() + " " + resultItemId + "!").color(NamedTextColor.GREEN));
                                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
                                            log.fine("Successfully compacted " + pickedUpMaterial.name() + " for player " + player.getName() + " using compactor " + utilityId);

                                            // Adjust the on-ground item stack if partially consumed
                                            if (consumedFromGroundStack > 0 && consumedFromGroundStack < itemsInPickedUpStack) {
                                                ItemStack remainingOnGround = pickedUpItemStack.clone();
                                                remainingOnGround.setAmount(itemsInPickedUpStack - consumedFromGroundStack);
                                                event.getItem().setItemStack(remainingOnGround);
                                                event.setCancelled(false); // Allow the modified pickup to continue
                                                log.finer("Compactor partially consumed picked up stack. Remaining on ground: " + remainingOnGround.getAmount());
                                            } else if (consumedFromGroundStack == itemsInPickedUpStack && itemsInPickedUpStack > 0) {
                                                // Ground stack fully consumed, event remains cancelled. item entity will be removed by default if event is cancelled.
                                                log.finer("Compactor fully consumed picked up stack of " + itemsInPickedUpStack + ". Event cancelled.");
                                            } else if (consumedFromGroundStack == 0 && consumedFromInventory == amountNeeded) {
                                               // Consumed entirely from inventory, ground pickup was not needed for this compaction
                                               // Event was cancelled at the start, so if ground items were not touched, they are effectively voided
                                               // if we don't un-cancel it. So, if no ground items were consumed, let the original pickup happen.
                                               event.setCancelled(false);
                                               log.finer("Compaction used only inventory items. Original pickup event for " + itemsInPickedUpStack + " " + pickedUpMaterial.name() + " uncancelled.");
                                            }
                                            // If event is still cancelled at this point, the ground item is gone.

                                            break; 
                                        } else {
                                            log.finer("[CompactorSystem] Player " + player.getName() + " does not have enough total " + pickedUpMaterial.name() + " (" + totalPotentiallyAvailable + "/" + amountNeeded + ") for compaction with " + utilityId + " and filter " + filterMaterialName);
                                        }
                                    } else {
                                         log.fine("[CompactorSystem] No suitable compacting recipe found for " + pickedUpMaterial.name() + " (filter in " + utilityId + ").");
                                    }
                                }
                            } catch (IllegalArgumentException e) {
                                log.warning("[CompactorSystem] Invalid material name '" + filterMaterialName + "' in NBT for compactor " + utilityId + " slotKey " + filterKeyIndex);
                            }
                        }
                    }
                }
            }
        }
    }

    private int countPlayerItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) { // getStorageContents for main inv
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private boolean removePlayerItems(Player player, Material material, int amountToRemove) {
        if (countPlayerItems(player, material) < amountToRemove) {
            return false; // Not enough items
        }
        int remainingToRemove = amountToRemove;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                if (item.getAmount() > remainingToRemove) {
                    item.setAmount(item.getAmount() - remainingToRemove);
                    remainingToRemove = 0;
                    player.getInventory().setItem(i, item);
                    break;
                } else {
                    remainingToRemove -= item.getAmount();
                    player.getInventory().setItem(i, null); // Remove stack
                    if (remainingToRemove == 0) {
                        break;
                    }
                }
            }
        }
        return remainingToRemove == 0;
    }
} 