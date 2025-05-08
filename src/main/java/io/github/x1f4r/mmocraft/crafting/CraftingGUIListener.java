package io.github.x1f4r.mmocraft.crafting;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.crafting.models.CustomRecipe;
import io.github.x1f4r.mmocraft.crafting.models.RequiredItem;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CraftingGUIListener implements Listener {

    private final MMOCraft plugin;
    private final RecipeManager recipeManager;
    public static final String GUI_TITLE = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "MMO Crafter";
    private static final Logger logger = MMOCraft.getPlugin(MMOCraft.class).getLogger();

    // GUI Layout Constants (for a 36-slot inventory - 4 rows)
    private static final int[] INPUT_SLOTS_ARRAY = {
            10, 11, 12, // Row 2, Columns 2-4
            19, 20, 21, // Row 3, Columns 2-4
            28, 29, 30  // Row 4, Columns 2-4
    };
    public static final Set<Integer> INPUT_SLOTS = Arrays.stream(INPUT_SLOTS_ARRAY).boxed().collect(Collectors.toSet());

    public static final int RESULT_SLOT = 16; // Row 2, Column 8
    public static final int ARROW_SLOT = 14;  // Row 2, Column 6 (decorative arrow)

    private static final ItemStack FILLER_PANE;
    private static final ItemStack ARROW_ITEM;

    static {
        FILLER_PANE = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = FILLER_PANE.getItemMeta();
        if (paneMeta != null) {
            paneMeta.setDisplayName(ChatColor.BLACK + ""); // No name
            FILLER_PANE.setItemMeta(paneMeta);
        }

        ARROW_ITEM = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta arrowMeta = ARROW_ITEM.getItemMeta();
        if (arrowMeta != null) {
            arrowMeta.setDisplayName(ChatColor.YELLOW + "Craft ->");
            ARROW_ITEM.setItemMeta(arrowMeta);
        }
    }

    public static final Set<Integer> DECORATIVE_SLOTS = new HashSet<>();
    static {
        Set<Integer> functionalSlots = new HashSet<>(INPUT_SLOTS);
        functionalSlots.add(RESULT_SLOT);
        functionalSlots.add(ARROW_SLOT);
        for (int i = 0; i < 36; i++) {
            if (!functionalSlots.contains(i)) {
                DECORATIVE_SLOTS.add(i);
            }
        }
    }


    public CraftingGUIListener(MMOCraft plugin) {
        this.plugin = plugin;
        this.recipeManager = plugin.getRecipeManager();
    }

    public void openCustomCraftingGUI(Player player) {
        Inventory customCrafter = Bukkit.createInventory(null, 36, GUI_TITLE);

        for (int slot : DECORATIVE_SLOTS) {
            customCrafter.setItem(slot, FILLER_PANE.clone());
        }
        customCrafter.setItem(ARROW_SLOT, ARROW_ITEM.clone());

        player.openInventory(customCrafter);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        Inventory topInventory = event.getView().getTopInventory();
        int slot = event.getSlot();


        if (clickedInventory != null && clickedInventory.equals(topInventory)) {
            if (DECORATIVE_SLOTS.contains(slot) || slot == ARROW_SLOT) {
                event.setCancelled(true);
                return;
            }
        }

        boolean updateScheduled = false;

        if (clickedInventory != null && clickedInventory.equals(topInventory) && slot == RESULT_SLOT) {
            event.setCancelled(true);
            ItemStack resultItem = topInventory.getItem(RESULT_SLOT);
            if (resultItem != null && resultItem.getType() != Material.AIR) {
                if (event.getClick().isShiftClick()) {
                    handleShiftClickResult(player, topInventory);
                } else if (event.getClick().isLeftClick() || event.getClick().isRightClick()) {
                    handleNormalClickResult(event, player, topInventory);
                }
                scheduleUpdate(topInventory, player); updateScheduled = true;
            }
        }
        else if ( (clickedInventory != null && clickedInventory.equals(topInventory) && INPUT_SLOTS.contains(slot)) ||
                (clickedInventory != null && clickedInventory.equals(event.getView().getBottomInventory())) ) {

            if (event.getClick().isShiftClick() && clickedInventory.equals(event.getView().getBottomInventory())) {
                event.setCancelled(true);
                handleShiftClickIntoGrid(event, player, topInventory);
                scheduleUpdate(topInventory, player); updateScheduled = true;
            }
            else if (event.getClick().isShiftClick() && clickedInventory.equals(topInventory) && INPUT_SLOTS.contains(slot)) {
                event.setCancelled(true);
                handleShiftClickOutOfGrid(event, player, topInventory);
                scheduleUpdate(topInventory, player); updateScheduled = true;
            }
            else {
                scheduleUpdate(topInventory, player); updateScheduled = true;
            }
        }

        if (!updateScheduled && clickedInventory != null) {
            if (clickedInventory.equals(topInventory) || clickedInventory.equals(event.getView().getBottomInventory())) {
                scheduleUpdate(topInventory, player);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory topInventory = event.getView().getTopInventory();

        boolean affectsCraftingArea = false;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topInventory.getSize()) {
                if (INPUT_SLOTS.contains(rawSlot) || rawSlot == RESULT_SLOT) {
                    affectsCraftingArea = true;
                    break;
                }
                if (DECORATIVE_SLOTS.contains(rawSlot) || rawSlot == ARROW_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        if (affectsCraftingArea) {
            scheduleUpdate(topInventory, player);
        }
    }


    private void scheduleUpdate(Inventory inventory, Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().equals(inventory)) {
                updateResultSlot(inventory, player);
            }
        }, 1L);
    }

    private ItemStack[] getMatrix(Inventory guiInventory) {
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < INPUT_SLOTS_ARRAY.length; i++) {
            matrix[i] = guiInventory.getItem(INPUT_SLOTS_ARRAY[i]);
        }
        return matrix;
    }

    private void updateResultSlot(Inventory guiInventory, Player player) {
        ItemStack[] currentMatrix = getMatrix(guiInventory);
        CustomRecipe customRecipe = recipeManager.findMatchingRecipe(currentMatrix);

        if (customRecipe != null) {
            guiInventory.setItem(RESULT_SLOT, customRecipe.getResult());
        } else {
            // Check for vanilla recipes if no custom recipe matches
            Recipe vanillaRecipe = Bukkit.getServer().getCraftingRecipe(currentMatrix, player.getWorld());
            if (vanillaRecipe != null) {
                guiInventory.setItem(RESULT_SLOT, vanillaRecipe.getResult());
            } else {
                guiInventory.setItem(RESULT_SLOT, null); // No custom or vanilla recipe found
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        Player player = (Player) event.getPlayer();
        Inventory topInventory = event.getInventory();

        for (int slot : INPUT_SLOTS_ARRAY) {
            ItemStack item = topInventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                player.getInventory().addItem(item.clone()).forEach((index, remaining) ->
                        player.getWorld().dropItemNaturally(player.getLocation(), remaining)
                );
                topInventory.setItem(slot, null);
            }
        }
        if (topInventory.getItem(RESULT_SLOT) != null) {
            topInventory.setItem(RESULT_SLOT, null);
        }
    }

    private void handleShiftClickResult(Player player, Inventory topInventory) {
        ItemStack resultTemplate = topInventory.getItem(RESULT_SLOT);
        if (resultTemplate == null || resultTemplate.getType() == Material.AIR) return;

        int maxCrafts = calculateMaxCrafts(topInventory, player);
        if (maxCrafts <= 0) {
            ItemStack[] currentMatrix = getMatrix(topInventory);
            CustomRecipe customRecipe = recipeManager.findMatchingRecipe(currentMatrix);
            if(customRecipe == null && Bukkit.getServer().getCraftingRecipe(currentMatrix, player.getWorld()) != null) {
                maxCrafts = 1;
            } else if (customRecipe != null) {
                maxCrafts = 1;
            } else {
                return;
            }
        }

        ItemStack baseCraftedItem = null;
        int totalCraftedAmount = 0;

        for (int i = 0; i < maxCrafts; i++) {
            ItemStack currentResultCheck = topInventory.getItem(RESULT_SLOT); // Re-check result slot
            if (currentResultCheck == null || !currentResultCheck.isSimilar(resultTemplate)) break;

            ItemStack craftedNow = attemptSingleCraft(player, topInventory);
            if (craftedNow != null) {
                if (baseCraftedItem == null) baseCraftedItem = craftedNow.clone();
                totalCraftedAmount += craftedNow.getAmount();
                // updateResultSlot(topInventory, player); // Update result slot after each craft - handled by scheduleUpdate from attemptSingleCraft
            } else {
                break;
            }
        }

        if (baseCraftedItem != null && totalCraftedAmount > 0) {
            baseCraftedItem.setAmount(totalCraftedAmount);
            player.getInventory().addItem(baseCraftedItem.clone()).forEach((idx, item) -> {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                player.sendMessage(ChatColor.YELLOW + "Your inventory was full, some items dropped!");
            });
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.8f);
        }
        // Schedule a final update after all shift-click operations
        scheduleUpdate(topInventory, player);
    }

    private void handleNormalClickResult(InventoryClickEvent event, Player player, Inventory topInventory) {
        ItemStack cursorItem = event.getCursor();
        ItemStack resultItemInSlot = topInventory.getItem(RESULT_SLOT);
        if (resultItemInSlot == null || resultItemInSlot.getType() == Material.AIR) return;

        ItemStack craftedItem = attemptSingleCraft(player, topInventory);

        if (craftedItem != null) {
            if (cursorItem == null || cursorItem.getType() == Material.AIR) {
                event.getView().setCursor(craftedItem);
            } else if (cursorItem.isSimilar(craftedItem)) {
                int canAdd = Math.min(craftedItem.getAmount(), cursorItem.getMaxStackSize() - cursorItem.getAmount());
                if (canAdd > 0) {
                    cursorItem.setAmount(cursorItem.getAmount() + canAdd);
                    craftedItem.setAmount(craftedItem.getAmount() - canAdd);
                }
                if (craftedItem.getAmount() > 0) {
                    player.getInventory().addItem(craftedItem.clone()).forEach((idx, item) -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                }
                event.getView().setCursor(cursorItem);
            } else {
                player.getInventory().addItem(craftedItem.clone()).forEach((idx, item) -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                player.sendMessage(ChatColor.RED + "You can't stack that item with what's on your cursor!");
            }
        }
        // Schedule an update after a normal click on the result.
        scheduleUpdate(topInventory, player);
    }

    private ItemStack attemptSingleCraft(Player player, Inventory guiInventory) {
        ItemStack resultItemFromSlot = guiInventory.getItem(RESULT_SLOT);
        if (resultItemFromSlot == null || resultItemFromSlot.getType().isAir()) return null;

        ItemStack[] currentMatrix = getMatrix(guiInventory);
        CustomRecipe customRecipe = recipeManager.findMatchingRecipe(currentMatrix);

        if (customRecipe != null && customRecipe.getResult().isSimilar(resultItemFromSlot)) {
            if (customRecipe.matches(currentMatrix)) {
                if (customRecipe.consumeIngredients(guiInventory, INPUT_SLOTS_ARRAY)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
                    return customRecipe.getResult().clone();
                }
            }
        } else {
            Recipe vanillaRecipe = Bukkit.getServer().getCraftingRecipe(currentMatrix, player.getWorld());
            if (vanillaRecipe != null && vanillaRecipe.getResult().isSimilar(resultItemFromSlot)) {
                for (int i = 0; i < currentMatrix.length; i++) {
                    if (currentMatrix[i] != null && currentMatrix[i].getType() != Material.AIR) {
                        int guiSlotIndex = INPUT_SLOTS_ARRAY[i];
                        ItemStack itemInGuiSlot = guiInventory.getItem(guiSlotIndex);

                        if (itemInGuiSlot != null && itemInGuiSlot.getAmount() > 0) {
                            Material typeInSlot = itemInGuiSlot.getType();
                            itemInGuiSlot.setAmount(itemInGuiSlot.getAmount() - 1);

                            if (itemInGuiSlot.getAmount() <= 0) {
                                if (typeInSlot == Material.WATER_BUCKET || typeInSlot == Material.LAVA_BUCKET || typeInSlot == Material.MILK_BUCKET) {
                                    guiInventory.setItem(guiSlotIndex, new ItemStack(Material.BUCKET));
                                } else if (typeInSlot.toString().contains("POTION") || typeInSlot == Material.HONEY_BOTTLE || typeInSlot == Material.DRAGON_BREATH) {
                                    guiInventory.setItem(guiSlotIndex, new ItemStack(Material.GLASS_BOTTLE));
                                } else if (typeInSlot == Material.MUSHROOM_STEW || typeInSlot == Material.RABBIT_STEW || typeInSlot == Material.BEETROOT_SOUP || typeInSlot == Material.SUSPICIOUS_STEW) {
                                    guiInventory.setItem(guiSlotIndex, new ItemStack(Material.BOWL));
                                }
                                else {
                                    guiInventory.setItem(guiSlotIndex, null);
                                }
                            } else {
                                guiInventory.setItem(guiSlotIndex, itemInGuiSlot);
                            }
                        }
                    }
                }
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
                return vanillaRecipe.getResult().clone();
            }
        }
        return null;
    }


    private void handleShiftClickIntoGrid(InventoryClickEvent event, Player player, Inventory topInventory) {
        ItemStack sourceItem = event.getCurrentItem();
        if (sourceItem == null || sourceItem.getType() == Material.AIR) return;

        ItemStack remainingToMove = sourceItem.clone();

        for (int targetSlot : INPUT_SLOTS_ARRAY) {
            if (remainingToMove.getAmount() <= 0) break;
            ItemStack itemInGrid = topInventory.getItem(targetSlot);
            if (itemInGrid != null && itemInGrid.isSimilar(remainingToMove)) {
                int spaceAvailable = itemInGrid.getMaxStackSize() - itemInGrid.getAmount();
                int amountToTransfer = Math.min(remainingToMove.getAmount(), spaceAvailable);
                if (amountToTransfer > 0) {
                    itemInGrid.setAmount(itemInGrid.getAmount() + amountToTransfer);
                    remainingToMove.setAmount(remainingToMove.getAmount() - amountToTransfer);
                }
            }
        }
        if (remainingToMove.getAmount() > 0) {
            for (int targetSlot : INPUT_SLOTS_ARRAY) {
                if (remainingToMove.getAmount() <= 0) break;
                if (topInventory.getItem(targetSlot) == null || topInventory.getItem(targetSlot).getType() == Material.AIR) {
                    topInventory.setItem(targetSlot, remainingToMove.clone());
                    remainingToMove.setAmount(0);
                    break;
                }
            }
        }
        if (remainingToMove.getAmount() == 0) {
            event.setCurrentItem(null);
        } else if (remainingToMove.getAmount() < sourceItem.getAmount()){
            event.getCurrentItem().setAmount(remainingToMove.getAmount());
        }
    }

    private void handleShiftClickOutOfGrid(InventoryClickEvent event, Player player, Inventory topInventory) {
        ItemStack sourceItem = event.getCurrentItem();
        if (sourceItem == null || sourceItem.getType() == Material.AIR) return;

        ItemStack itemToMove = sourceItem.clone();
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(itemToMove);

        if (leftovers.isEmpty()) {
            topInventory.setItem(event.getSlot(), null);
        } else {
            int amountNotMoved = 0;
            for (ItemStack leftoverStack : leftovers.values()) {
                amountNotMoved += leftoverStack.getAmount();
            }
            sourceItem.setAmount(amountNotMoved);
            topInventory.setItem(event.getSlot(), sourceItem);
            player.sendMessage(ChatColor.YELLOW + "Inventory full!");
        }
    }

    private int calculateMaxCrafts(Inventory guiInventory, Player player) {
        ItemStack resultItem = guiInventory.getItem(RESULT_SLOT);
        if (resultItem == null || resultItem.getType().isAir()) return 0;

        int maxPossibleCrafts = Integer.MAX_VALUE;
        ItemStack[] currentMatrix = getMatrix(guiInventory);

        CustomRecipe customRecipe = recipeManager.findMatchingRecipe(currentMatrix);
        if (customRecipe != null && customRecipe.getResult().isSimilar(resultItem)) {
            if (customRecipe.getType() == CustomRecipe.RecipeType.SHAPED) {
                boolean possible = true;
                for (int i = 0; i < 9; i++) {
                    RequiredItem required = customRecipe.getRequirementForMatrixIndex(i);
                    if (required != null) {
                        ItemStack itemInGrid = currentMatrix[i];
                        if (itemInGrid == null || !required.matches(itemInGrid) || itemInGrid.getAmount() < required.getAmount()) {
                            possible = false;
                            break;
                        }
                        maxPossibleCrafts = Math.min(maxPossibleCrafts, itemInGrid.getAmount() / required.getAmount());
                    }
                }
                return possible ? Math.max(0, maxPossibleCrafts) : 0;
            } else { return 1; }
        } else {
            Recipe vanillaRecipe = Bukkit.getServer().getCraftingRecipe(currentMatrix, player.getWorld());
            if (vanillaRecipe != null && vanillaRecipe.getResult().isSimilar(resultItem)) {
                // Simplified: if a vanilla recipe matches, assume we can craft at least one for shift-click purposes.
                // True max craft calculation for vanilla is complex and not implemented here.
                return 1;
            }
        }
        return 0;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null && clickedBlock.getType() == Material.CRAFTING_TABLE) {
            event.setCancelled(true);
            openCustomCraftingGUI(event.getPlayer());
        }
    }
}