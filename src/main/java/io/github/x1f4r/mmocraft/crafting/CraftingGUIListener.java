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

    // Slots that are not part of the functional UI (input, result, arrow)
    // All other slots in a 36-slot inventory (0-35)
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
        // updateResultSlot(customCrafter, player); // Update once GUI is open and stable
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory(); // The inventory that was clicked
        Inventory topInventory = event.getView().getTopInventory(); // The custom crafter GUI
        int slot = event.getSlot(); // The slot number in the clickedInventory

        // logger.fine("Click: Slot=" + slot + " RawSlot=" + event.getRawSlot() + " Action=" + event.getAction() + " ClickType=" + event.getClick() + " CurrentItem=" + event.getCurrentItem() + " Cursor=" + event.getCursor());


        // Handle decorative slots first: always cancel
        if (clickedInventory != null && clickedInventory.equals(topInventory)) {
            if (DECORATIVE_SLOTS.contains(slot) || slot == ARROW_SLOT) {
                event.setCancelled(true);
                return;
            }
        }

        boolean updateScheduled = false;

        // 1. Clicked on RESULT_SLOT
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
        // 2. Clicked in one of the INPUT_SLOTS or Player Inventory that could affect the grid
        else if ( (clickedInventory != null && clickedInventory.equals(topInventory) && INPUT_SLOTS.contains(slot)) || // Click in input slot
                (clickedInventory != null && clickedInventory.equals(event.getView().getBottomInventory())) ) {       // Click in player inventory

            // For shift clicks from player inventory to grid
            if (event.getClick().isShiftClick() && clickedInventory.equals(event.getView().getBottomInventory())) {
                event.setCancelled(true); // Manual handling
                handleShiftClickIntoGrid(event, player, topInventory);
                scheduleUpdate(topInventory, player); updateScheduled = true;
            }
            // For shift clicks from grid to player inventory
            else if (event.getClick().isShiftClick() && clickedInventory.equals(topInventory) && INPUT_SLOTS.contains(slot)) {
                event.setCancelled(true); // Manual handling
                handleShiftClickOutOfGrid(event, player, topInventory);
                scheduleUpdate(topInventory, player); updateScheduled = true;
            }
            // For other clicks (place, pickup, swap) within input slots or player inv that might lead to grid change
            else {
                // Allow the default action for now, and schedule an update
                // Bukkit handles the item movement for normal clicks. We just react.
                scheduleUpdate(topInventory, player); updateScheduled = true;
            }
        }

        // If no specific logic handled the update scheduling, but it was a relevant inventory
        if (!updateScheduled && clickedInventory != null) {
            if (clickedInventory.equals(topInventory) || clickedInventory.equals(event.getView().getBottomInventory())) {
                // This catches cases like dropping an item on an empty slot from cursor, etc.
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

        // Check if any part of the drag involves the top inventory's functional slots
        boolean affectsCraftingArea = false;
        for (int rawSlot : event.getRawSlots()) {
            // Raw slots are for the combined view. Top inventory slots are 0 to topInventory.getSize() - 1
            if (rawSlot < topInventory.getSize()) { // If the drag is in the top inventory
                if (INPUT_SLOTS.contains(rawSlot) || rawSlot == RESULT_SLOT) {
                    affectsCraftingArea = true;
                    break;
                }
                if (DECORATIVE_SLOTS.contains(rawSlot) || rawSlot == ARROW_SLOT) {
                    event.setCancelled(true); // Prevent dragging onto decorative slots
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
            if (player.getOpenInventory().getTopInventory().equals(inventory)) { // Check if still same inventory
                updateResultSlot(inventory, player);
                // player.updateInventory(); // Bukkit often handles this implicitly after item changes,
                // but can be called if visual glitches occur.
                // Let's try without first to avoid over-updating.
                // If issues, add: Bukkit.getScheduler().runTaskLater(plugin, player::updateInventory, 1L);
            }
        }, 1L); // 1 tick delay
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
            // Optional: Check for vanilla recipes if you want this table to also do vanilla.
            // Recipe vanillaRecipe = Bukkit.getCraftingRecipe(currentMatrix, player.getWorld());
            // guiInventory.setItem(RESULT_SLOT, (vanillaRecipe != null) ? vanillaRecipe.getResult() : null);
            // For a purely custom crafter, just clear if no custom recipe matches:
            guiInventory.setItem(RESULT_SLOT, null);
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
                topInventory.setItem(slot, null); // Clear the slot
            }
        }
        // Also clear result slot if anything is there (shouldn't be if not taken)
        if (topInventory.getItem(RESULT_SLOT) != null) {
            topInventory.setItem(RESULT_SLOT, null);
        }
    }

    private void handleShiftClickResult(Player player, Inventory topInventory) {
        ItemStack resultTemplate = topInventory.getItem(RESULT_SLOT);
        if (resultTemplate == null || resultTemplate.getType() == Material.AIR) return;

        int maxCrafts = calculateMaxCrafts(topInventory, player);
        if (maxCrafts <= 0) return;

        ItemStack baseCraftedItem = null;
        int totalCraftedAmount = 0;

        for (int i = 0; i < maxCrafts; i++) {
            ItemStack currentResultCheck = topInventory.getItem(RESULT_SLOT);
            if (currentResultCheck == null || !currentResultCheck.isSimilar(resultTemplate)) break;

            ItemStack craftedNow = attemptSingleCraft(player, topInventory);
            if (craftedNow != null) {
                if (baseCraftedItem == null) baseCraftedItem = craftedNow.clone(); // Use first craft as base for meta
                totalCraftedAmount += craftedNow.getAmount();
                // After a craft, ingredients are consumed. The recipe matching will be re-evaluated
                // by the scheduled updateResultSlot. If we craft multiple, we must ensure
                // ingredients are available for each.
                updateResultSlot(topInventory, player); // Re-check recipe validity for next iteration if needed
            } else {
                break; // Stop if a craft attempt fails
            }
        }

        if (baseCraftedItem != null && totalCraftedAmount > 0) {
            baseCraftedItem.setAmount(totalCraftedAmount);
            player.getInventory().addItem(baseCraftedItem.clone()).forEach((idx, item) -> {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                player.sendMessage(ChatColor.YELLOW + "Your inventory was full, some items dropped!");
            });
        }
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
            } else { // Cursor has a different item
                player.getInventory().addItem(craftedItem.clone()).forEach((idx, item) -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
        }
    }

    private ItemStack attemptSingleCraft(Player player, Inventory guiInventory) {
        ItemStack resultItemFromSlot = guiInventory.getItem(RESULT_SLOT);
        if (resultItemFromSlot == null || resultItemFromSlot.getType().isAir()) return null;

        ItemStack[] currentMatrix = getMatrix(guiInventory);
        CustomRecipe customRecipe = recipeManager.findMatchingRecipe(currentMatrix);

        if (customRecipe != null && customRecipe.getResult().isSimilar(resultItemFromSlot)) {
            if (customRecipe.matches(currentMatrix)) {
                if (customRecipe.consumeIngredients(guiInventory, INPUT_SLOTS_ARRAY)) {
                    return customRecipe.getResult().clone();
                }
            }
        }
        // Add vanilla recipe handling here if desired, similar to customRecipe logic
        return null;
    }

    private void handleShiftClickIntoGrid(InventoryClickEvent event, Player player, Inventory topInventory) {
        ItemStack sourceItem = event.getCurrentItem(); // Item from player's inventory
        if (sourceItem == null || sourceItem.getType() == Material.AIR) return;

        ItemStack remainingToMove = sourceItem.clone(); // Work with a clone

        // Try to merge with existing stacks in the input grid
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
        // Try to place in empty slots in the input grid
        if (remainingToMove.getAmount() > 0) {
            for (int targetSlot : INPUT_SLOTS_ARRAY) {
                if (remainingToMove.getAmount() <= 0) break;
                if (topInventory.getItem(targetSlot) == null || topInventory.getItem(targetSlot).getType() == Material.AIR) {
                    topInventory.setItem(targetSlot, remainingToMove.clone()); // Place a clone of what's left
                    remainingToMove.setAmount(0); // All placed
                    break;
                }
            }
        }
        // Update the source item in player's inventory based on how much was moved
        if (remainingToMove.getAmount() == 0) {
            event.setCurrentItem(null); // All moved
        } else if (remainingToMove.getAmount() < sourceItem.getAmount()){
            event.getCurrentItem().setAmount(remainingToMove.getAmount()); // Partially moved
        }
        // If remainingToMove.getAmount() == sourceItem.getAmount(), nothing moved, currentItem remains as is.
    }

    private void handleShiftClickOutOfGrid(InventoryClickEvent event, Player player, Inventory topInventory) {
        ItemStack sourceItem = event.getCurrentItem(); // Item from one of the INPUT_SLOTS
        if (sourceItem == null || sourceItem.getType() == Material.AIR) return;

        ItemStack itemToMove = sourceItem.clone();
        // Try to add to player's inventory
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(itemToMove);

        if (leftovers.isEmpty()) {
            topInventory.setItem(event.getSlot(), null); // All moved successfully, clear from grid
        } else {
            // Not all items could be moved, update the stack in the grid
            int amountNotMoved = 0;
            for (ItemStack leftoverStack : leftovers.values()) {
                amountNotMoved += leftoverStack.getAmount();
            }
            sourceItem.setAmount(amountNotMoved); // Set grid item to what couldn't be moved
            topInventory.setItem(event.getSlot(), sourceItem); // Place it back
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
            // Iterate through the recipe's requirements (defined by its shape and ingredients map)
            if (customRecipe.getType() == CustomRecipe.RecipeType.SHAPED) {
                boolean possible = true;
                for (int i = 0; i < 9; i++) { // Iterate through the conceptual 3x3 matrix
                    // Get the RequiredItem for this part of the recipe's shape
                    // This requires CustomRecipe to have a method that maps a matrix index (0-8)
                    // to a RequiredItem based on its internal shape and ingredients.
                    RequiredItem required = customRecipe.getRequirementForMatrixIndex(i); // NEW METHOD NEEDED IN CUSTOMRECIPE

                    if (required != null) { // If the recipe expects an item here
                        ItemStack itemInGrid = currentMatrix[i]; // Get the actual item from our GUI's matrix
                        if (itemInGrid == null || !required.matches(itemInGrid) || itemInGrid.getAmount() < required.getAmount()) {
                            possible = false; // Requirement not met
                            break;
                        }
                        maxPossibleCrafts = Math.min(maxPossibleCrafts, itemInGrid.getAmount() / required.getAmount());
                    }
                }
                return possible ? Math.max(0, maxPossibleCrafts) : 0;
            } else { return 1; } // Shapeless or other types, default to 1
        }
        // Add vanilla recipe max craft calculation here if you support them
        return 0; // No matching recipe or not enough ingredients
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