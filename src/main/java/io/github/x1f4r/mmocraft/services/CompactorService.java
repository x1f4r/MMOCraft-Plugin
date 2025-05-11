package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.compactors.listeners.CompactorInteractionListener;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import io.github.x1f4r.mmocraft.items.CustomItem;
import io.github.x1f4r.mmocraft.items.RequiredIngredient;
import io.github.x1f4r.mmocraft.recipes.CustomRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class CompactorService implements Service {
    private MMOCore core;
    private LoggingService logging;
    private ItemService itemService;
    private RecipeService recipeService;
    // NBTService accessed via static keys
    private MMOCraft plugin; // For BukkitScheduler if needed

    // GUI Constants
    public static final String COMPACTOR_GUI_TITLE_PREFIX = "§8Personal Compactor - "; // Legacy color codes for Bukkit.createInventory
    public static final Component COMPACTOR_GUI_TITLE_COMPONENT_PREFIX = Component.text("Personal Compactor - ", NamedTextColor.DARK_GRAY);

    public static final int MAX_DISPLAYABLE_FILTER_SLOTS = 7; // e.g., for a 27-slot GUI, middle row has 9, use 7
    public static final int[] FILTER_GUI_SLOTS_IN_27_SLOT_GUI = {10, 11, 12, 13, 14, 15, 16}; // Indices for a 27-slot inv

    private static final ItemStack GUI_PLACEHOLDER_PANE;
    private static final ItemStack GUI_EMPTY_FILTER_ICON;

    static {
        GUI_PLACEHOLDER_PANE = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = GUI_PLACEHOLDER_PANE.getItemMeta();
        if (paneMeta != null) {
            paneMeta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
            GUI_PLACEHOLDER_PANE.setItemMeta(paneMeta);
        }

        GUI_EMPTY_FILTER_ICON = new ItemStack(Material.BARRIER);
        ItemMeta emptyMeta = GUI_EMPTY_FILTER_ICON.getItemMeta();
        if (emptyMeta != null) {
            emptyMeta.displayName(Component.text("Empty Filter Slot", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            emptyMeta.lore(List.of(Component.text("Click with an item to set filter.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            GUI_EMPTY_FILTER_ICON.setItemMeta(emptyMeta);
        }
    }


    public CompactorService(MMOCore core) {
        this.core = core;
    }

    @Override
    public void initialize(MMOCore core) {
        this.plugin = core.getPlugin();
        this.logging = core.getService(LoggingService.class);
        this.itemService = core.getService(ItemService.class);
        this.recipeService = core.getService(RecipeService.class);
        // NBTService is ready

        core.registerListener(new CompactorInteractionListener(this));
        logging.info(getServiceName() + " initialized.");
    }

    @Override
    public void shutdown() {
        // No specific shutdown tasks for CompactorService
    }

    public void openCompactorGUI(Player player, ItemStack compactorItem) {
        ItemMeta compactorMeta = compactorItem.getItemMeta();
        if (compactorMeta == null) {
            logging.warn("Compactor item for " + player.getName() + " has no ItemMeta. Cannot open GUI.");
            return;
        }
        PersistentDataContainer compactorPdc = compactorMeta.getPersistentDataContainer();

        String utilityId = NBTService.get(compactorPdc, NBTService.COMPACTOR_UTILITY_ID_KEY, PersistentDataType.STRING, "UnknownCompactor");
        int actualSupportedFilterSlots = NBTService.get(compactorPdc, NBTService.COMPACTOR_SLOT_COUNT_KEY, PersistentDataType.INTEGER, 1);
        int displayableSlots = Math.min(actualSupportedFilterSlots, MAX_DISPLAYABLE_FILTER_SLOTS);

        // Use Adventure Component for view title checks later, Bukkit string for creation
        Component guiTitleComponent = COMPACTOR_GUI_TITLE_COMPONENT_PREFIX.append(Component.text(utilityId, NamedTextColor.YELLOW));
        String guiTitleString = LegacyComponentSerializer.legacySection().serialize(guiTitleComponent);

        Inventory gui = Bukkit.createInventory(player, 27, guiTitleString); // Owner is player

        // Fill background with placeholders
        for (int i = 0; i < gui.getSize(); i++) {
            boolean isFilterSlotGUIPosition = false;
            for (int j=0; j<displayableSlots; j++){
                if(FILTER_GUI_SLOTS_IN_27_SLOT_GUI[j] == i) {
                    isFilterSlotGUIPosition = true;
                    break;
                }
            }
            if(!isFilterSlotGUIPosition){
                gui.setItem(i, GUI_PLACEHOLDER_PANE.clone());
            }
        }

        // Populate filter slots
        for (int filterDisplayIndex = 0; filterDisplayIndex < displayableSlots; filterDisplayIndex++) {
            int guiSlotIndex = FILTER_GUI_SLOTS_IN_27_SLOT_GUI[filterDisplayIndex];
            NamespacedKey filterNbtKey = NBTService.getCompactorFilterKey(filterDisplayIndex); // 0-indexed filter key

            String materialName = NBTService.get(compactorPdc, filterNbtKey, PersistentDataType.STRING, null);
            if (materialName != null) {
                try {
                    Material filterMaterial = Material.valueOf(materialName.toUpperCase());
                    ItemStack filterDisplayItem = new ItemStack(filterMaterial);
                    ItemMeta filterDisplayMeta = filterDisplayItem.getItemMeta();
                    filterDisplayMeta.displayName(Component.text(formatMaterialName(filterMaterial), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.text("Filter Slot " + (filterDisplayIndex + 1) + ": ", NamedTextColor.YELLOW)
                            .append(Component.text(formatMaterialName(filterMaterial), NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("Click with an item to change filter.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("Click with empty hand to clear.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                    filterDisplayMeta.lore(lore);
                    filterDisplayItem.setItemMeta(filterDisplayMeta);
                    gui.setItem(guiSlotIndex, filterDisplayItem);
                } catch (IllegalArgumentException e) {
                    logging.warn("[CompactorGUI] Invalid material '" + materialName + "' in NBT for " + utilityId + " filter " + filterDisplayIndex);
                    setEmptyFilterSlotDisplay(gui, guiSlotIndex, filterDisplayIndex, "Invalid Filter Data!");
                }
            } else {
                setEmptyFilterSlotDisplay(gui, guiSlotIndex, filterDisplayIndex, "Empty Filter Slot " + (filterDisplayIndex + 1));
            }
        }
        player.openInventory(gui);
    }

    private void setEmptyFilterSlotDisplay(Inventory gui, int guiSlotIndex, int filterStorageIndex, String displayName) {
        ItemStack emptyIcon = GUI_EMPTY_FILTER_ICON.clone();
        ItemMeta meta = emptyIcon.getItemMeta(); // Should not be null
        if (meta != null) {
            meta.displayName(Component.text(displayName, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            // Lore is already set on GUI_EMPTY_FILTER_ICON
            emptyIcon.setItemMeta(meta);
        }
        gui.setItem(guiSlotIndex, emptyIcon);
    }

    private String formatMaterialName(Material material) {
        if (material == null) return "Unknown";
        String name = material.name().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
    }


    public void handleCompactorGUIClick(Player player, Inventory clickedTopInventory, int clickedGuiSlot, ItemStack cursorItem, ItemStack compactorItemInHand, EquipmentSlot handHoldingCompactor) {
        int filterStorageIndex = -1; // The 0-indexed key for NBT
        for (int i = 0; i < FILTER_GUI_SLOTS_IN_27_SLOT_GUI.length; i++) {
            if (FILTER_GUI_SLOTS_IN_27_SLOT_GUI[i] == clickedGuiSlot) {
                filterStorageIndex = i;
                break;
            }
        }
        if (filterStorageIndex == -1) return; // Clicked a non-filter slot like a placeholder

        ItemMeta compactorMeta = compactorItemInHand.getItemMeta();
        if (compactorMeta == null) return; // Should not happen if item is a compactor
        PersistentDataContainer compactorPdc = compactorMeta.getPersistentDataContainer();
        int maxFiltersOnThisCompactor = NBTService.get(compactorPdc, NBTService.COMPACTOR_SLOT_COUNT_KEY, PersistentDataType.INTEGER, 0);

        if (filterStorageIndex >= maxFiltersOnThisCompactor) {
            // Clicked a GUI slot that this compactor doesn't actually support (e.g., GUI shows 7, compactor has 3)
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1f);
            return;
        }

        NamespacedKey nbtFilterKey = NBTService.getCompactorFilterKey(filterStorageIndex);
        boolean changed = false;

        if (cursorItem != null && !cursorItem.getType().isAir() && cursorItem.getType().isItem()) {
            // Setting/Changing the filter
            Material newFilterMaterial = cursorItem.getType();
            NBTService.set(compactorPdc, nbtFilterKey, PersistentDataType.STRING, newFilterMaterial.name());
            compactorItemInHand.setItemMeta(compactorMeta); // Apply updated PDC to meta
            player.getInventory().setItem(handHoldingCompactor, compactorItemInHand); // Update item in player's hand

            // Update GUI display for this slot
            ItemStack displayItem = new ItemStack(newFilterMaterial);
            ItemMeta displayMeta = displayItem.getItemMeta();
            displayMeta.displayName(Component.text(formatMaterialName(newFilterMaterial), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            // Add full lore here
            displayItem.setItemMeta(displayMeta);
            clickedTopInventory.setItem(clickedGuiSlot, displayItem);
            changed = true;
        } else if (cursorItem == null || cursorItem.getType().isAir()) {
            // Clearing the filter (clicked with empty cursor)
            if (NBTService.has(compactorPdc, nbtFilterKey, PersistentDataType.STRING)) {
                NBTService.remove(compactorPdc, nbtFilterKey);
                compactorItemInHand.setItemMeta(compactorMeta);
                player.getInventory().setItem(handHoldingCompactor, compactorItemInHand);
                setEmptyFilterSlotDisplay(clickedTopInventory, clickedGuiSlot, filterStorageIndex, "Empty Filter Slot " + (filterStorageIndex + 1));
                changed = true;
            }
        }

        if (changed) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            player.updateInventory(); // Ensure client sees change to item in hand if meta changed
        }
    }

    public void attemptAutoCompact(Player player, ItemStack pickedUpItemStackInstance, EntityPickupItemEvent event) {
        Material pickedUpMaterial = pickedUpItemStackInstance.getType();
        PlayerInventory playerInventory = player.getInventory();

        // Iterate all compactors in player's inventory
        for (ItemStack compactorStack : playerInventory.getContents()) {
            if (compactorStack == null || !compactorStack.hasItemMeta()) continue;
            ItemMeta compactorMeta = compactorStack.getItemMeta();
            PersistentDataContainer compactorPdc = compactorMeta.getPersistentDataContainer();

            String utilityId = NBTService.get(compactorPdc, NBTService.COMPACTOR_UTILITY_ID_KEY, PersistentDataType.STRING, null);
            if (utilityId == null || !utilityId.startsWith("personal_compactor_")) continue;

            int compactorSlotCount = NBTService.get(compactorPdc, NBTService.COMPACTOR_SLOT_COUNT_KEY, PersistentDataType.INTEGER, 0);

            for (int filterKeyIndex = 0; filterKeyIndex < compactorSlotCount; filterKeyIndex++) {
                NamespacedKey filterNbtKey = NBTService.getCompactorFilterKey(filterKeyIndex);
                String filterMaterialName = NBTService.get(compactorPdc, filterNbtKey, PersistentDataType.STRING, null);

                if (filterMaterialName != null && filterMaterialName.equalsIgnoreCase(pickedUpMaterial.name())) {
                    if (logging.isDebugMode()) logging.debug("[Compactor] " + player.getName() + " picked up " + pickedUpMaterial.name() + ", matches filter " + filterKeyIndex + " in " + utilityId);
                    CustomRecipe compactingRecipe = recipeService.findCompactingRecipeForItem(pickedUpMaterial);

                    if (compactingRecipe != null) {
                        RequiredIngredient ingredient = compactingRecipe.shapelessIngredients().get(0);
                        int amountNeededForOneCraft = ingredient.amount();
                        ItemStack resultTemplate = compactingRecipe.getResult(itemService);
                        String resultItemName = LegacyComponentSerializer.plain().serialize(
                                resultTemplate.getItemMeta() != null && resultTemplate.getItemMeta().hasDisplayName() ?
                                        Objects.requireNonNull(resultTemplate.getItemMeta().displayName()) : Component.text(formatMaterialName(resultTemplate.getType()))
                        ).trim();


                        int itemsCurrentlyInInventory = countPlayerItemsOfType(playerInventory, pickedUpMaterial);
                        int itemsInPickedUpStackEntity = event.getItem().getItemStack().getAmount(); // Current amount on ground item
                        int totalAvailable = itemsCurrentlyInInventory + itemsInPickedUpStackEntity;

                        if (logging.isDebugMode()) logging.debug(String.format("[Compactor] Recipe %s needs %d of %s. Player inv: %d, pickup: %d. Total: %d",
                                compactingRecipe.id(), amountNeededForOneCraft, pickedUpMaterial.name(), itemsCurrentlyInInventory, itemsInPickedUpStackEntity, totalAvailable));

                        if (totalAvailable >= amountNeededForOneCraft) {
                            int itemsToConsumeFromPickup = Math.min(itemsInPickedUpStackEntity, amountNeededForOneCraft);
                            int itemsToConsumeFromInventory = amountNeededForOneCraft - itemsToConsumeFromPickup;

                            if (itemsToConsumeFromInventory > 0) {
                                if (!removePlayerItemsOfType(playerInventory, pickedUpMaterial, itemsToConsumeFromInventory)) {
                                    logging.warn("[Compactor] Failed to remove " + itemsToConsumeFromInventory + " " + pickedUpMaterial.name() + " from inventory for " + player.getName() + " during compaction. Aborting this craft attempt for this compactor.");
                                    continue; // Skip to next filter or compactor
                                }
                            }

                            // Give compacted item
                            playerInventory.addItem(resultTemplate.clone());
                            player.sendMessage(Component.text("Compacted " + amountNeededForOneCraft + " " + formatMaterialName(pickedUpMaterial).toLowerCase() +
                                    " into " + resultItemName + "!", NamedTextColor.GREEN));
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.5f);
                            if (logging.isInfoEnabled()) logging.info("Compacted " + amountNeededForOneCraft + " " + pickedUpMaterial.name() + " for " + player.getName() + " via " + utilityId + " to " + resultItemName);

                            // Adjust the picked-up item stack entity
                            int remainingInPickupStack = itemsInPickedUpStackEntity - itemsToConsumeFromPickup;
                            if (remainingInPickupStack <= 0) {
                                event.getItem().remove(); // Item entity fully consumed
                                event.setCancelled(true); // Prevent Bukkit from trying to give (now non-existent) item
                            } else {
                                ItemStack updatedGroundStack = event.getItem().getItemStack();
                                updatedGroundStack.setAmount(remainingInPickupStack);
                                event.getItem().setItemStack(updatedGroundStack); // Update the item on ground
                                // Event is NOT cancelled, player picks up remainder.
                            }
                            return; // Compaction successful for this pickup by this compactor.
                        }
                    }
                }
            }
        }
    }

    private int countPlayerItemsOfType(PlayerInventory inventory, Material material) {
        int count = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        // Consider offhand too if compactors can use items from there
        // ItemStack offhand = inventory.getItemInOffHand();
        // if (offhand != null && offhand.getType() == material) count += offhand.getAmount();
        return count;
    }

    private boolean removePlayerItemsOfType(PlayerInventory inventory, Material material, int amountToRemove) {
        if (countPlayerItemsOfType(inventory, material) < amountToRemove) return false;
        // Bukkit's removeItem is a bit basic, might need manual iteration for more control
        // or if items have custom NBT that needs to be ignored for removal (not the case here for simple materials)
        inventory.removeItemAnySlot(new ItemStack(material, amountToRemove)); // Paper API, more robust
        return true;
    }
}