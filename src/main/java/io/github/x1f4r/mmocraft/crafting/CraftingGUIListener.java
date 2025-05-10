package io.github.x1f4r.mmocraft.crafting;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import io.github.x1f4r.mmocraft.crafting.models.CustomRecipe;
import io.github.x1f4r.mmocraft.crafting.models.RequiredItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.Recipe;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CraftingGUIListener implements Listener {

    private final MMOPlugin plugin;
    private final RecipeManager recipeManager;
    private final Logger log;

    public static final Component GUI_TITLE_COMPONENT = Component.text("Custom Crafter", NamedTextColor.DARK_GRAY);
    public static final String GUI_TITLE = LegacyComponentSerializer.legacySection().serialize(GUI_TITLE_COMPONENT);

    // GUI Layout Constants (5 rows = 45 slots) - Adjusted for more space maybe? Or keep 36? Let's use 54 (6 rows)
    private static final int GUI_SIZE = 54;
    private static final int[] INPUT_SLOTS_ARRAY = { // Centered 3x3 grid
            11, 12, 13,
            20, 21, 22,
            29, 30, 31
    };
    public static final Set<Integer> INPUT_SLOTS = Arrays.stream(INPUT_SLOTS_ARRAY).boxed().collect(Collectors.toSet());

    public static final int RESULT_SLOT = 25; // Right of the grid, middle row
    public static final int ARROW_SLOT = 23;  // Between grid and result

    private static final ItemStack FILLER_PANE;
    private static final ItemStack ARROW_ITEM;
    private static final ItemStack BARRIER_ITEM; // For blocking result slot when invalid

    static {
        FILLER_PANE = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = FILLER_PANE.getItemMeta();
        if (paneMeta != null) { paneMeta.displayName(Component.text("", NamedTextColor.BLACK)); FILLER_PANE.setItemMeta(paneMeta); }

        ARROW_ITEM = new ItemStack(Material.SPECTRAL_ARROW); // Or other arrow type
        ItemMeta arrowMeta = ARROW_ITEM.getItemMeta();
        if (arrowMeta != null) { arrowMeta.displayName(Component.text("Craft ->", NamedTextColor.YELLOW)); ARROW_ITEM.setItemMeta(arrowMeta); }

        BARRIER_ITEM = new ItemStack(Material.BARRIER);
        ItemMeta barrierMeta = BARRIER_ITEM.getItemMeta();
        if (barrierMeta != null) { barrierMeta.displayName(Component.text("Invalid Recipe", NamedTextColor.RED)); BARRIER_ITEM.setItemMeta(barrierMeta); }
    }

    // Calculate decorative slots based on functional slots and GUI size
    public static final Set<Integer> DECORATIVE_SLOTS = new HashSet<>();
    static {
        Set<Integer> functionalSlots = new HashSet<>(INPUT_SLOTS);
        functionalSlots.add(RESULT_SLOT);
        functionalSlots.add(ARROW_SLOT);
        for (int i = 0; i < GUI_SIZE; i++) {
            if (!functionalSlots.contains(i)) {
                DECORATIVE_SLOTS.add(i);
            }
        }
    }


    public CraftingGUIListener(MMOCore core) {
        this.plugin = core.getPlugin();
        this.recipeManager = core.getRecipeManager(); // Get from core
        this.log = MMOPlugin.getMMOLogger();
    }

    public void openCustomCraftingGUI(Player player) {
        Inventory customCrafter = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE_COMPONENT);

        // Fill decorative slots
        for (int slot : DECORATIVE_SLOTS) {
            customCrafter.setItem(slot, FILLER_PANE.clone());
        }
        // Place arrow
        customCrafter.setItem(ARROW_SLOT, ARROW_ITEM.clone());
        // Result slot initially empty or barrier? Let's start empty.

        player.openInventory(customCrafter);
        // Initial update check when opening
        scheduleUpdate(customCrafter, player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(GUI_TITLE_COMPONENT)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory(); // Inv clicked IN
        Inventory topInventory = event.getView().getTopInventory(); // The GUI itself
        int slot = event.getSlot(); // Raw slot index in the view

        // Prevent interaction with decorative/arrow slots in the GUI
        if (clickedInventory != null && clickedInventory.equals(topInventory)) {
            if (DECORATIVE_SLOTS.contains(slot) || slot == ARROW_SLOT) {
                event.setCancelled(true);
                return;
            }
        }

        boolean updateNeeded = false;

        // --- Handling clicks on the RESULT SLOT ---
        if (clickedInventory != null && clickedInventory.equals(topInventory) && slot == RESULT_SLOT) {
            event.setCancelled(true); // Always cancel direct interaction with result slot
            ItemStack resultItem = topInventory.getItem(RESULT_SLOT);
            // Allow taking item only if it's not the barrier/placeholder
            if (resultItem != null && resultItem.getType() != Material.AIR && resultItem.getType() != Material.BARRIER) {
                if (event.getClick().isShiftClick()) {
                    handleShiftClickResult(player, topInventory);
                } else if (event.getClick().isLeftClick() || event.getClick().isRightClick()) {
                    handleNormalClickResult(event, player, topInventory);
                }
                updateNeeded = true; // Update after taking result
            }
        }
        // --- Handling clicks within the INPUT SLOTS or Player Inventory ---
        else if (clickedInventory != null) {
             // If clicking inside the input grid OR the player's inventory
             if (INPUT_SLOTS.contains(slot) || clickedInventory.equals(event.getView().getBottomInventory())) {
                 updateNeeded = true; // Any click here might change the recipe
             }
             // Handle shift-clicking items INTO the grid from player inventory
             if (event.isShiftClick() && clickedInventory.equals(event.getView().getBottomInventory())) {
                 // Default shift-click behavior might work if slots are empty,
                 // but custom handling might be needed for specific placement.
                 // For now, just mark for update. Bukkit might handle the move.
                 updateNeeded = true;
                 // event.setCancelled(true); // Optionally cancel and handle move manually
                 // handleShiftClickIntoGrid(event, player, topInventory);
             }
             // Handle shift-clicking items OUT of the grid to player inventory
             else if (event.isShiftClick() && clickedInventory.equals(topInventory) && INPUT_SLOTS.contains(slot)) {
                  // Default shift-click should move to player inventory. Mark for update.
                  updateNeeded = true;
             }
        }

        // Schedule an update if needed
        if (updateNeeded) {
            scheduleUpdate(topInventory, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getView().title().equals(GUI_TITLE_COMPONENT)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory topInventory = event.getView().getTopInventory();

        boolean affectsCraftingArea = false;
        // Check if any dragged-over slots are within the GUI's functional area
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topInventory.getSize()) { // Only consider slots in the top inventory
                if (INPUT_SLOTS.contains(rawSlot)) {
                    affectsCraftingArea = true;
                    break; // No need to check further
                }
                // Prevent dragging into decorative/result slots
                if (DECORATIVE_SLOTS.contains(rawSlot) || rawSlot == ARROW_SLOT || rawSlot == RESULT_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // If the drag affects the input slots, schedule an update
        if (affectsCraftingArea) {
            scheduleUpdate(topInventory, player);
        }
    }


    private void scheduleUpdate(Inventory inventory, Player player) {
        // Use Bukkit scheduler to run the update on the next tick
        // This prevents issues with inventory state during the event handling
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Ensure the player still has the same inventory open
            if (player.getOpenInventory().getTopInventory().equals(inventory)) {
                updateResultSlot(inventory, player);
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
            // Fallback to vanilla recipes when no custom recipe matches
            Recipe vanillaRecipe = Bukkit.getServer().getCraftingRecipe(currentMatrix, player.getWorld());
            if (vanillaRecipe != null) {
                guiInventory.setItem(RESULT_SLOT, vanillaRecipe.getResult());
            } else {
                guiInventory.setItem(RESULT_SLOT, null);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().title().equals(GUI_TITLE_COMPONENT)) return;
        Player player = (Player) event.getPlayer();
        Inventory topInventory = event.getInventory();

        // Return items from input slots to player inventory
        for (int slot : INPUT_SLOTS_ARRAY) {
            ItemStack item = topInventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                player.getInventory().addItem(item.clone()).forEach((index, remaining) ->
                        player.getWorld().dropItemNaturally(player.getLocation(), remaining)
                );
                topInventory.setItem(slot, null); // Clear the slot in the GUI
            }
        }
        // Clear result slot just in case
        if (topInventory.getItem(RESULT_SLOT) != null) {
            topInventory.setItem(RESULT_SLOT, null);
        }
    }

    private void handleShiftClickResult(Player player, Inventory topInventory) {
        ItemStack resultTemplate = topInventory.getItem(RESULT_SLOT);
        if (resultTemplate == null || resultTemplate.getType() == Material.AIR || resultTemplate.getType() == Material.BARRIER) return;

        int maxCrafts = calculateMaxCrafts(topInventory); // Calculate how many times we can craft with current ingredients
        if (maxCrafts <= 0) return; // Cannot craft even once

        ItemStack baseCraftedItem = resultTemplate.clone(); // Use the template for the base item
        int totalCraftedAmount = 0;
        int itemsMovedToInv = 0;

        for (int i = 0; i < maxCrafts; i++) {
            ItemStack currentResultCheck = topInventory.getItem(RESULT_SLOT);
            // Ensure the recipe is still valid before consuming ingredients
            if (currentResultCheck == null || !currentResultCheck.isSimilar(resultTemplate)) {
                 log.warning("Recipe became invalid during shift-click crafting for " + player.getName());
                 break; // Stop crafting if recipe changes mid-way
            }

            // Attempt to craft one item and consume ingredients
            if (attemptSingleCraft(player, topInventory)) {
                totalCraftedAmount += baseCraftedItem.getAmount(); // Add amount of one craft result
            } else {
                 log.warning("Failed to consume ingredients during shift-click craft #" + (i+1) + " for " + player.getName());
                break; // Stop if ingredient consumption fails
            }
            // Update the result slot immediately after consuming ingredients
            updateResultSlot(topInventory, player);
        }

        // If items were crafted, try to add them to the player's inventory
        if (totalCraftedAmount > 0) {
            ItemStack finalStack = baseCraftedItem.clone();
            finalStack.setAmount(totalCraftedAmount);

            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(finalStack);

            // Calculate how many were actually added
            if (!leftovers.isEmpty()) {
                itemsMovedToInv = totalCraftedAmount - leftovers.get(0).getAmount();
                player.getWorld().dropItemNaturally(player.getLocation(), leftovers.get(0));
                player.sendMessage(Component.text("Inventory full, some crafted items dropped!", NamedTextColor.YELLOW));
            } else {
                itemsMovedToInv = totalCraftedAmount;
            }

            if (itemsMovedToInv > 0) {
                 player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.8f);
                 // Optionally update statistics or trigger achievements
            }
        }
        // Final update check after shift-click is complete
        scheduleUpdate(topInventory, player);
    }

    private void handleNormalClickResult(InventoryClickEvent event, Player player, Inventory topInventory) {
        ItemStack cursorItem = event.getCursor(); // Item on cursor
        ItemStack resultItemInSlot = topInventory.getItem(RESULT_SLOT); // Item currently in result slot

        // Check if crafting is possible
        if (resultItemInSlot == null || resultItemInSlot.getType() == Material.AIR || resultItemInSlot.getType() == Material.BARRIER) return;

        // Check if the cursor can accept the result item
        if (cursorItem != null && cursorItem.getType() != Material.AIR) {
            if (!cursorItem.isSimilar(resultItemInSlot)) {
                // Cursor has a different item, cannot take result
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 1.0f); // Error sound
                return;
            }
            if (cursorItem.getAmount() >= cursorItem.getMaxStackSize()) {
                 // Cursor stack is full
                 player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 1.0f);
                 return;
            }
        }

        // Attempt to craft one item and consume ingredients
        if (attemptSingleCraft(player, topInventory)) {
            ItemStack craftedItem = resultItemInSlot.clone(); // Get a clone of the result *before* potential update

            if (cursorItem == null || cursorItem.getType() == Material.AIR) {
                event.getView().setCursor(craftedItem);
            } else { // Cursor has a similar item
                 int canAdd = Math.min(craftedItem.getAmount(), cursorItem.getMaxStackSize() - cursorItem.getAmount());
                 cursorItem.setAmount(cursorItem.getAmount() + canAdd);
                 event.getView().setCursor(cursorItem);
                 // If craftedItem had more than could fit, drop the remainder (shouldn't happen with standard recipes)
                 if (craftedItem.getAmount() > canAdd) {
                     ItemStack remainder = craftedItem.clone();
                     remainder.setAmount(craftedItem.getAmount() - canAdd);
                     player.getWorld().dropItemNaturally(player.getLocation(), remainder);
                 }
            }
             player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
        } else {
             log.warning("Failed to consume ingredients on normal click craft for " + player.getName());
             // Optionally play an error sound
             player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 1.0f);
        }

        // Schedule an update after the click action
        scheduleUpdate(topInventory, player);
    }

    /**
     * Attempts to consume ingredients for the currently displayed recipe.
     * Assumes the recipe in the result slot is valid and matches the grid.
     * @return true if ingredients were successfully consumed, false otherwise.
     */
    private boolean attemptSingleCraft(Player player, Inventory guiInventory) {
        ItemStack resultItemFromSlot = guiInventory.getItem(RESULT_SLOT);
        if (resultItemFromSlot == null || resultItemFromSlot.getType().isAir()) return false;

        ItemStack[] currentMatrix = getMatrix(guiInventory);
        CustomRecipe customRecipe = recipeManager.findMatchingRecipe(currentMatrix);

        // Ensure the found recipe actually produces the item currently in the result slot
        if (customRecipe != null && customRecipe.getResult().isSimilar(resultItemFromSlot)) {
            // Consume ingredients based on the custom recipe definition
            if (customRecipe.consumeIngredients(guiInventory, INPUT_SLOTS_ARRAY)) {
                return true;
            } else {
                 log.severe("Ingredient consumption failed for custom recipe " + customRecipe.getId() + " even though it matched!");
                 return false; // Consumption failed
            }
        }

        log.warning("Attempted craft, but no matching recipe found for result: " + resultItemFromSlot.getType());
        return false; // No matching recipe found for the item in the result slot
    }


    /**
     * Calculates the maximum number of times the current recipe can be crafted
     * based on the minimum stack size of the ingredients in the grid.
     */
    private int calculateMaxCrafts(Inventory guiInventory) {
        ItemStack resultItem = guiInventory.getItem(RESULT_SLOT);
        if (resultItem == null || resultItem.getType().isAir() || resultItem.getType() == Material.BARRIER) return 0;

        ItemStack[] currentMatrix = getMatrix(guiInventory);
        CustomRecipe customRecipe = recipeManager.findMatchingRecipe(currentMatrix);

        if (customRecipe == null || !customRecipe.getResult().isSimilar(resultItem)) {
            // Vanilla recipe max craft calculation was here, removing as it's out of scope/unreliable.
            return 0; // No matching custom recipe for the current result
        }

        int maxPossibleCrafts = Integer.MAX_VALUE;

        if (customRecipe.getType() == CustomRecipe.RecipeType.SHAPED) {
            for (int i = 0; i < 9; i++) {
                RequiredItem required = customRecipe.getRequirementForMatrixIndex(i);
                if (required != null) {
                    int guiSlotIndex = INPUT_SLOTS_ARRAY[i];
                    ItemStack itemInGrid = guiInventory.getItem(guiSlotIndex);

                    // Basic check: If required item is missing or doesn't match, cannot craft at all
                    if (itemInGrid == null || !required.matches(itemInGrid)) {
                        return 0;
                    }

                    // Calculate how many times this specific slot's requirement can be fulfilled
                    int craftsPossibleForSlot = itemInGrid.getAmount() / required.getAmount();
                    maxPossibleCrafts = Math.min(maxPossibleCrafts, craftsPossibleForSlot);
                }
            }
             return Math.max(0, maxPossibleCrafts); // Ensure non-negative

        } else if (customRecipe.getType() == CustomRecipe.RecipeType.SHAPELESS) {
            return 1; // Placeholder: Assume can only craft 1 for now
        }

        return 0; // Should not be reached
    }


    // Optional: Handle right-clicking a vanilla crafting table to open custom GUI
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Check if right-clicking a block with the main hand
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        // Check if the clicked block is a crafting table
        if (clickedBlock != null && clickedBlock.getType() == Material.CRAFTING_TABLE) {
            // Check config if vanilla table should be overridden
            // boolean overrideVanilla = plugin.getConfig().getBoolean("crafting.override_vanilla_table", true);
            // if (overrideVanilla) {
                event.setCancelled(true); // Prevent vanilla GUI from opening
                openCustomCraftingGUI(event.getPlayer());
            // }
        }
    }
}

