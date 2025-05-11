package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import io.github.x1f4r.mmocraft.crafting.listeners.CraftingGUIListener;
import io.github.x1f4r.mmocraft.items.RequiredIngredient; // For consumption logic
import io.github.x1f4r.mmocraft.recipes.CustomRecipe;
import io.github.x1f4r.mmocraft.recipes.RecipeType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class CraftingGUIService implements Service {
    private MMOCore core;
    private LoggingService logging;
    private RecipeService recipeService;
    private ItemService itemService;
    // NBTService used via RecipeService/ItemService or static keys
    private ConfigService configService;
    private MMOCraft plugin; // For BukkitScheduler

    // --- GUI Constants ---
    // Using Adventure Component for title comparisons, String for Bukkit.createInventory
    public static final Component GUI_TITLE_COMPONENT = Component.text("Custom Crafter", NamedTextColor.DARK_AQUA);
    public static final String GUI_TITLE_STRING = LegacyComponentSerializer.legacySection().serialize(GUI_TITLE_COMPONENT);
    public static final int GUI_SIZE = 54; // 6 rows

    // 3x3 Input Grid slots (centered in a 6x9 GUI)
    public static final int[] INPUT_SLOTS_ARRAY = {
            12, 13, 14, // Row 2 (0-indexed)
            21, 22, 23, // Row 3
            30, 31, 32  // Row 4
    };
    public static final Set<Integer> INPUT_SLOTS = Arrays.stream(INPUT_SLOTS_ARRAY).boxed().collect(Collectors.toSet());
    public static final int RESULT_SLOT = 25;  // Row 3, to the right of the grid (slot 25)
    public static final int ARROW_SLOT = 24;   // Slot between input grid and result (slot 24)

    private static final ItemStack FILLER_PANE;
    private static final ItemStack ARROW_ITEM_DISPLAY;
    private static final ItemStack BARRIER_ITEM_NO_RECIPE;

    private boolean overrideVanillaCraftingTable;

    static { // Initialize static display items
        FILLER_PANE = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = FILLER_PANE.getItemMeta();
        if (paneMeta != null) { paneMeta.displayName(Component.text(" ")); FILLER_PANE.setItemMeta(paneMeta); }

        ARROW_ITEM_DISPLAY = new ItemStack(Material.SPECTRAL_ARROW); // Or any arrow type
        ItemMeta arrowMeta = ARROW_ITEM_DISPLAY.getItemMeta();
        if (arrowMeta != null) { arrowMeta.displayName(Component.text("Craft ->", NamedTextColor.YELLOW)); ARROW_ITEM_DISPLAY.setItemMeta(arrowMeta); }

        BARRIER_ITEM_NO_RECIPE = new ItemStack(Material.BARRIER);
        ItemMeta barrierMeta = BARRIER_ITEM_NO_RECIPE.getItemMeta();
        if (barrierMeta != null) { barrierMeta.displayName(Component.text("No Matching Recipe", NamedTextColor.RED)); BARRIER_ITEM_NO_RECIPE.setItemMeta(barrierMeta); }
    }

    public CraftingGUIService(MMOCore core) {
        this.core = core;
    }

    @Override
    public void initialize(MMOCore core) {
        this.plugin = core.getPlugin();
        this.logging = core.getService(LoggingService.class);
        this.recipeService = core.getService(RecipeService.class);
        this.itemService = core.getService(ItemService.class);
        this.configService = core.getService(ConfigService.class);

        overrideVanillaCraftingTable = configService.getMainConfigBoolean("crafting.override_vanilla_table", true);

        core.registerListener(new CraftingGUIListener(this, core.getPlugin()));
        logging.info(getServiceName() + " initialized. Vanilla crafting table override: " + overrideVanillaCraftingTable);
    }

    @Override
    public void shutdown() {
        // No specific shutdown tasks beyond listeners being unregistered by Bukkit.
    }

    public boolean shouldOverrideVanillaCraftingTable() {
        return overrideVanillaCraftingTable;
    }

    public void openCustomCraftingGUI(Player player) {
        Inventory customCrafter = Bukkit.createInventory(player, GUI_SIZE, GUI_TITLE_STRING); // Owner is player

        // Fill decorative slots
        for (int i = 0; i < GUI_SIZE; i++) {
            if (!INPUT_SLOTS.contains(i) && i != RESULT_SLOT && i != ARROW_SLOT) {
                customCrafter.setItem(i, FILLER_PANE.clone());
            }
        }
        customCrafter.setItem(ARROW_SLOT, ARROW_ITEM_DISPLAY.clone());
        // Result slot starts empty or with a "no recipe" barrier after initial check.

        player.openInventory(customCrafter);
        scheduleResultUpdate(customCrafter, player); // Initial update check
    }

    /**
     * Schedules an update to the result slot for the next available tick.
     * This is important to ensure inventory state is consistent after Bukkit events.
     * @param guiInventory The GUI inventory being viewed.
     * @param player The player viewing the GUI.
     */
    public void scheduleResultUpdate(Inventory guiInventory, Player player) {
        if (player == null || !player.isOnline() || guiInventory == null) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                // Ensure player is still online and viewing this specific GUI instance
                if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(guiInventory) &&
                        GUI_TITLE_COMPONENT.equals(player.getOpenInventory().title())) {
                    updateResultSlot(guiInventory, player);
                }
            }
        }.runTask(plugin); // Run on next tick (or as soon as possible on main thread)
    }

    private ItemStack[] getCraftingMatrixFromGUI(Inventory guiInventory) {
        ItemStack[] matrix = new ItemStack[9]; // Represents 3x3 grid
        for (int i = 0; i < INPUT_SLOTS_ARRAY.length; i++) {
            matrix[i] = guiInventory.getItem(INPUT_SLOTS_ARRAY[i]);
        }
        return matrix;
    }

    /**
     * Updates the result slot in the GUI based on the current input matrix.
     */
    public void updateResultSlot(Inventory guiInventory, Player player) {
        ItemStack[] currentMatrix = getCraftingMatrixFromGUI(guiInventory);
        CustomRecipe matchedRecipe = recipeService.findMatchingRecipe(currentMatrix);

        if (matchedRecipe != null) {
            guiInventory.setItem(RESULT_SLOT, matchedRecipe.getResult(itemService));
        } else {
            guiInventory.setItem(RESULT_SLOT, BARRIER_ITEM_NO_RECIPE.clone()); // Show "no recipe"
        }
    }

    /**
     * Handles a player's attempt to craft by clicking the result slot.
     * @param guiInventory The crafting GUI inventory.
     * @param player The player attempting to craft.
     * @param isShiftClick True if the player shift-clicked to craft multiple items.
     */
    public void attemptCraft(Inventory guiInventory, Player player, boolean isShiftClick) {
        ItemStack resultSlotItem = guiInventory.getItem(RESULT_SLOT);
        if (resultSlotItem == null || resultSlotItem.getType().isAir() || resultSlotItem.isSimilar(BARRIER_ITEM_NO_RECIPE)) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.0f);
            return; // No valid recipe in result slot
        }

        ItemStack[] currentMatrix = getCraftingMatrixFromGUI(guiInventory);
        CustomRecipe matchedRecipe = recipeService.findMatchingRecipe(currentMatrix);

        // Double-check: does the item in result slot truly match the current recipe?
        if (matchedRecipe == null || !resultSlotItem.isSimilar(matchedRecipe.getResult(itemService))) {
            logging.warn("Craft attempt by " + player.getName() + " but result slot item did not match current recipe state. Matrix might have changed between update and click.");
            updateResultSlot(guiInventory, player); // Re-update to show barrier
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.0f);
            return;
        }

        int craftableAmount = isShiftClick ? calculateMaxCrafts(currentMatrix, matchedRecipe) : 1;
        if (craftableAmount <= 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 0.8f); // "Can't craft" sound
            return;
        }

        ItemStack resultTemplate = matchedRecipe.getResult(itemService);
        int successfullyCraftedCount = 0;

        for (int i = 0; i < craftableAmount; i++) {
            // For each craft, we need to ensure ingredients are still available and then consume them.
            // Re-fetch matrix state *before* consumption for this iteration.
            ItemStack[] matrixForThisIteration = getCraftingMatrixFromGUI(guiInventory);
            if (!matchedRecipe.matches(matrixForThisIteration, itemService, recipeService)) {
                logging.warn("Recipe " + matchedRecipe.id() + " no longer matches mid-shift-craft for " + player.getName() + " (iteration " + (i+1) + "). Stopping.");
                break; // Ingredients likely consumed by a previous iteration, or changed
            }

            if (consumeIngredientsFromActualGUI(guiInventory, matchedRecipe)) {
                // Give result item
                ItemStack craftedResultItem = resultTemplate.clone(); // Get a fresh clone for each craft
                HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(craftedResultItem);
                if (!leftovers.isEmpty()) {
                    leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                    player.sendMessage(Component.text("Your inventory was full! Some crafted items dropped on the ground.", NamedTextColor.YELLOW));
                }
                successfullyCraftedCount++;
            } else {
                logging.warn("Ingredient consumption failed for recipe " + matchedRecipe.id() + " for player " + player.getName() + " (iteration " + (i+1) + "). This should not happen if calculateMaxCrafts and matches are correct. Stopping craft.");
                break; // Stop if consumption unexpectedly fails
            }
        }

        if (successfullyCraftedCount > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f + (random.nextFloat() * 0.4f));
            // Future: Trigger custom craft events, player stats for crafting XP, etc.
        }

        // Update the result slot after all crafting attempts are done
        updateResultSlot(guiInventory, player); // Not scheduled, direct update as event flow is ending here
    }

    private boolean consumeIngredientsFromActualGUI(Inventory guiInventory, CustomRecipe recipe) {
        // This method directly modifies items in the guiInventory's input slots.
        // It assumes the recipe *currently* matches the grid.
        // A safer way is to make a list of items to remove based on recipe, then remove them.

        if (recipe.type() == RecipeType.SHAPED) {
            // Find the offset where the recipe matched (this is the complex part for shaped consumption)
            // For simplicity now, assume it matches at the first possible offset.
            // A robust version of CustomRecipe.matches would return the match context (offsets).
            // For now, let's find *one* valid match configuration to consume.
            ItemStack[] currentMatrix = getCraftingMatrixFromGUI(guiInventory);
            int rOffset = -1, cOffset = -1;
            int recipeHeight = Objects.requireNonNull(recipe.shape()).size();
            int recipeWidth = recipe.shape().stream().mapToInt(String::length).max().orElse(0);

            outerLoop:
            for (int ro = 0; ro <= (3 - recipeHeight); ro++) {
                for (int co = 0; co <= (3 - recipeWidth); co++) {
                    if (recipe.checkShapedMatchAtOffset(currentMatrix, ro, co, recipeHeight, recipeWidth, itemService, recipeService)) { // Assume checkShapedMatchAtOffset exists and is public on CustomRecipe
                        rOffset = ro; cOffset = co;
                        break outerLoop;
                    }
                }
            }
            if (rOffset == -1) return false; // Should not happen if called after a successful global match

            for (int r = 0; r < recipeHeight; r++) {
                String recipeRowString = recipe.shape().get(r);
                for (int c = 0; c < recipeWidth; c++) {
                    char recipeChar = (c < recipeRowString.length()) ? recipeRowString.charAt(c) : ' ';
                    RequiredIngredient required = (recipeChar != ' ') ? Objects.requireNonNull(recipe.shapedIngredients()).get(recipeChar) : null;
                    if (required != null) {
                        int gridSlotIndex = INPUT_SLOTS_ARRAY[(r + rOffset) * 3 + (c + cOffset)];
                        ItemStack itemInSlot = guiInventory.getItem(gridSlotIndex);
                        if (itemInSlot == null || itemInSlot.getAmount() < required.amount()) return false; // Should be caught by matches

                        ItemStack container = getContainerItem(itemInSlot.clone()); // Check before amount change
                        itemInSlot.setAmount(itemInSlot.getAmount() - required.amount());
                        if (itemInSlot.getAmount() <= 0) {
                            guiInventory.setItem(gridSlotIndex, container); // Set container or null
                        } else {
                            // Item remains with reduced amount
                        }
                    }
                }
            }
            return true;

        } else { // SHAPELESS
            List<RequiredIngredient> requirementsToConsume = new ArrayList<>(Objects.requireNonNull(recipe.shapelessIngredients()));
            for (RequiredIngredient required : requirementsToConsume) {
                int amountStillNeeded = required.amount();
                for (int slotIndex : INPUT_SLOTS_ARRAY) { // Iterate over GUI input slots
                    if (amountStillNeeded == 0) break;
                    ItemStack itemInSlot = guiInventory.getItem(slotIndex);
                    if (itemInSlot == null || itemInSlot.getType().isAir() || itemInSlot.getAmount() == 0) continue;

                    Tag<Material> resolvedTag = (required.type() == RequirementType.TAG) ? recipeService.resolveMaterialTag(required.value()) : null;
                    if (required.matchesTypeAndCustomId(itemInSlot, itemService, resolvedTag)) {
                        int canConsumeFromThisStack = Math.min(amountStillNeeded, itemInSlot.getAmount());

                        ItemStack container = getContainerItem(itemInSlot.clone());
                        itemInSlot.setAmount(itemInSlot.getAmount() - canConsumeFromThisStack);
                        amountStillNeeded -= canConsumeFromThisStack;

                        if (itemInSlot.getAmount() <= 0) {
                            guiInventory.setItem(slotIndex, container);
                        }
                    }
                }
                if (amountStillNeeded > 0) {
                    logging.severe("Failed to consume ingredients for shapeless recipe " + recipe.id() + " even after match. Required: " + required + ", needed " + amountStillNeeded + " more.");
                    return false; // Should not happen if matches() is correct and grid didn't change
                }
            }
            return true;
        }
    }

    private int calculateMaxCrafts(ItemStack[] currentMatrix, CustomRecipe recipe) {
        if (recipe == null) return 0;
        int maxCrafts = Integer.MAX_VALUE;

        if (recipe.type() == RecipeType.SHAPED) {
            // Find one match configuration
            int rOffset = -1, cOffset = -1;
            int recipeHeight = Objects.requireNonNull(recipe.shape()).size();
            int recipeWidth = recipe.shape().stream().mapToInt(String::length).max().orElse(0);
            outerLoop:
            for (int ro = 0; ro <= (3 - recipeHeight); ro++) {
                for (int co = 0; co <= (3 - recipeWidth); co++) {
                    if (recipe.checkShapedMatchAtOffset(currentMatrix, ro, co, recipeHeight, recipeWidth, itemService, recipeService)) {
                        rOffset = ro; cOffset = co;
                        break outerLoop;
                    }
                }
            }
            if (rOffset == -1) return 0; // No match

            for (int r = 0; r < recipeHeight; r++) {
                String recipeRowString = recipe.shape().get(r);
                for (int c = 0; c < recipeWidth; c++) {
                    char recipeChar = (c < recipeRowString.length()) ? recipeRowString.charAt(c) : ' ';
                    RequiredIngredient required = (recipeChar != ' ') ? Objects.requireNonNull(recipe.shapedIngredients()).get(recipeChar) : null;
                    if (required != null) {
                        ItemStack itemInGrid = currentMatrix[(r + rOffset) * 3 + (c + cOffset)];
                        if (itemInGrid == null || required.amount() == 0) return 0; // Should not happen if matches
                        maxCrafts = Math.min(maxCrafts, itemInGrid.getAmount() / required.amount());
                    }
                }
            }
        } else { // SHAPELESS
            if (recipe.shapelessIngredients().isEmpty()) return (Arrays.stream(currentMatrix).allMatch(i -> i == null || i.getType().isAir())) ? 1 : 0; // Empty recipe needs empty grid

            Map<String, Integer> availableItemCounts = new HashMap<>(); // Key: material/customID/tag, Value: count
            for (ItemStack item : currentMatrix) {
                if (item == null || item.getType().isAir()) continue;
                // For simplicity, we'll use a simplified key for available items.
                // A robust solution would handle custom items and tags more distinctly.
                String itemKey = itemService.getCustomItemTemplateFromItemStack(item) != null ?
                        "CUSTOM:" + itemService.getCustomItemTemplateFromItemStack(item).id() :
                        "MAT:" + item.getType().name();
                availableItemCounts.put(itemKey, availableItemCounts.getOrDefault(itemKey, 0) + item.getAmount());
            }

            for (RequiredIngredient req : Objects.requireNonNull(recipe.shapelessIngredients())) {
                if (req.amount() == 0) continue;
                int currentItemAvailable = 0;
                // This simplified count won't work well with tags that span many materials or if multiple
                // required ingredients could be satisfied by the same item type.
                // A more robust count would iterate through grid items for each req.
                // For now, this is a placeholder for a more complex counting mechanism.
                if (req.type() == RequirementType.MATERIAL) {
                    currentItemAvailable = availableItemCounts.getOrDefault("MAT:" + req.value().toUpperCase(), 0);
                } else if (req.type() == RequirementType.ITEM) {
                    currentItemAvailable = availableItemCounts.getOrDefault("CUSTOM:" + req.value(), 0);
                } else if (req.type() == RequirementType.TAG) {
                    Tag<Material> tag = recipeService.resolveMaterialTag(req.value());
                    if (tag != null) {
                        for(ItemStack item : currentMatrix){
                            if(item != null && tag.isTagged(item.getType())) currentItemAvailable += item.getAmount();
                        }
                    }
                }
                if (currentItemAvailable == 0) return 0; // Ingredient type not available at all
                maxCrafts = Math.min(maxCrafts, currentItemAvailable / req.amount());
            }
        }
        return (maxCrafts == Integer.MAX_VALUE) ? 0 : maxCrafts;
    }

    private ItemStack getContainerItem(ItemStack consumedItem) {
        if (consumedItem == null) return null;
        Material type = consumedItem.getType();
        // More comprehensive list of container items:
        if (type.toString().endsWith("_BUCKET") && type != Material.BUCKET) return new ItemStack(Material.BUCKET);
        if (type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION || type == Material.HONEY_BOTTLE || type == Material.DRAGON_BREATH) return new ItemStack(Material.GLASS_BOTTLE);
        if (type == Material.MUSHROOM_STEW || type == Material.RABBIT_STEW || type == Material.BEETROOT_SOUP || type == Material.SUSPICIOUS_STEW) return new ItemStack(Material.BOWL);
        return null;
    }
}