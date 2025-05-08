package io.github.x1f4r.mmocraft.crafting.models;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import io.github.x1f4r.mmocraft.items.ItemManager;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class CustomRecipe {

    private final String id;
    private final String resultItemId;
    private final int resultAmount;
    private final RecipeType type;
    private String[] shape; // Max 3x3 for SHAPED
    private Map<Character, RequiredItem> shapedIngredients; // Key: char in shape, Value: RequiredItem
    // private List<RequiredItem> shapelessIngredients; // For SHAPELESS (implement later)

    private final ItemManager itemManager; // Needed to get the result ItemStack
    private static final Logger log = MMOPlugin.getMMOLogger();


    private CustomRecipe(String id, String resultItemId, int resultAmount, RecipeType type, ItemManager manager) {
        this.id = id;
        this.resultItemId = resultItemId;
        this.resultAmount = resultAmount;
        this.type = type;
        this.itemManager = manager;
    }

    public static CustomRecipe loadFromConfig(MMOCore core, String id, ConfigurationSection config, Map<String, Tag<Material>> customTags) {
        ItemManager itemManager = core.getItemManager(); // Get ItemManager from core

        ConfigurationSection resultSection = config.getConfigurationSection("result");
        if (resultSection == null) {
            log.warning("Recipe '" + id + "' is missing result section! Skipping.");
            return null;
        }
        String resultId = resultSection.getString("item_id");
        if (resultId == null || resultId.isEmpty()) {
            log.warning("Recipe '" + id + "' result section is missing 'item_id'! Skipping.");
            return null;
        }
        if (itemManager.getItem(resultId) == null) {
            log.warning("Result Item ID '" + resultId + "' referenced by recipe '" + id + "' not found in ItemManager! Skipping recipe.");
            return null;
        }

        int resultAmount = resultSection.getInt("amount", 1);
        String typeStr = config.getString("type", "SHAPED").toUpperCase();
        RecipeType recipeType;
        try {
            recipeType = RecipeType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            log.warning("Invalid recipe type '" + typeStr + "' for recipe '" + id + "'. Defaulting to SHAPED.");
            recipeType = RecipeType.SHAPED;
        }

        CustomRecipe recipe = new CustomRecipe(id, resultId, resultAmount, recipeType, itemManager);

        if (recipeType == RecipeType.SHAPED) {
            if (!loadShapedData(recipe, config, customTags)) return null;
        } else if (recipeType == RecipeType.SHAPELESS) {
             log.warning("Shapeless recipe loading not yet implemented for recipe '" + id + "'. Skipping.");
             return null; // TODO: Implement shapeless loading
        } else {
            log.warning("Unknown recipe type for recipe '" + id + "'. Skipping.");
            return null;
        }
        return recipe;
    }

    private static boolean loadShapedData(CustomRecipe recipe, ConfigurationSection config, Map<String, Tag<Material>> customTags) {
        List<String> shapeList = config.getStringList("shape");
        if (shapeList.isEmpty() || shapeList.size() > 3) {
            log.warning("Invalid shape for SHAPED recipe '" + recipe.id + "'. Must be 1-3 rows. Skipping.");
            return false;
        }
        recipe.shape = new String[shapeList.size()];
        for(int i = 0; i < shapeList.size(); i++) {
            String row = shapeList.get(i);
            if (row.length() > 3) {
                log.warning("Shape row '" + row + "' in recipe '" + recipe.id + "' exceeds 3 columns. Skipping.");
                return false;
            }
            // Pad with spaces to ensure consistent 3-char width for easier indexing
            recipe.shape[i] = String.format("%-3s", row);
        }

        ConfigurationSection ingredientsSection = config.getConfigurationSection("ingredients");
        if (ingredientsSection == null) {
            log.warning("Missing ingredients section for SHAPED recipe '" + recipe.id + "'. Skipping.");
            return false;
        }
        recipe.shapedIngredients = new HashMap<>();
        for (String key : ingredientsSection.getKeys(false)) {
            if (key.length() != 1) {
                log.warning("Invalid ingredient key '" + key + "' in recipe '" + recipe.id + "'. Must be a single character. Skipping ingredient.");
                continue; // Skip this invalid key, but maybe allow recipe to load if others are ok? Or return false?
            }
            char ingredientChar = key.charAt(0);
            if (ingredientChar == ' ') {
                 log.warning("Ingredient key cannot be a space character in recipe '" + recipe.id + "'. Skipping ingredient.");
                 continue;
            }

            RequiredItem requiredItem = RequiredItem.loadFromConfig(ingredientsSection.getConfigurationSection(key), customTags);
            if (requiredItem != null) {
                recipe.shapedIngredients.put(ingredientChar, requiredItem);
            } else {
                log.warning("Invalid ingredient definition for key '" + key + "' in recipe '" + recipe.id + "'. Skipping recipe.");
                return false; // Fail recipe load if any ingredient is invalid
            }
        }
        // Validate that all chars used in shape are defined in ingredients
        for (String row : recipe.shape) {
            for (char c : row.toCharArray()) {
                if (c != ' ' && !recipe.shapedIngredients.containsKey(c)) {
                    log.warning("Shape for recipe '" + recipe.id + "' contains character '" + c + "' which is not defined in ingredients. Skipping recipe.");
                    return false;
                }
            }
        }
        return true;
    }


    public boolean matches(ItemStack[] matrix) { // Takes a 9-slot matrix (0-8)
        if (matrix == null || matrix.length != 9) {
            return false;
        }

        if (type == RecipeType.SHAPED) {
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int matrixIndex = r * 3 + c;
                    ItemStack itemInMatrixSlot = matrix[matrixIndex];

                    char shapeChar = ' '; // Default to space (empty)
                    // Check bounds of the defined shape
                    if (r < shape.length && c < shape[r].length()) {
                        shapeChar = shape[r].charAt(c);
                    }

                    RequiredItem required = (shapeChar != ' ') ? shapedIngredients.get(shapeChar) : null;

                    if (required == null) { // Expect empty slot in recipe shape
                        // If the matrix slot is not empty, it doesn't match
                        if (itemInMatrixSlot != null && itemInMatrixSlot.getType() != Material.AIR) {
                            return false;
                        }
                    } else { // Expect a specific item
                        // If the requirement doesn't match the item in the slot, it fails
                        if (!required.matches(itemInMatrixSlot)) {
                            return false;
                        }
                    }
                }
            }
            // If we went through all slots without returning false, it's a match
            return true;
        } else if (type == RecipeType.SHAPELESS) {
            // TODO: Implement shapeless matching logic
            return false;
        }
        return false;
    }

    public boolean consumeIngredients(Inventory guiInventory, int[] inputSlotIndices) {
         if (inputSlotIndices == null || inputSlotIndices.length != 9) return false;

        if (type == RecipeType.SHAPED) {
            // Create a map to track consumption per GUI slot index to handle stacked ingredients correctly
             Map<Integer, Integer> consumptionPerGuiSlot = new HashMap<>();

             // Calculate required consumption for each GUI slot based on the recipe shape
             for (int r = 0; r < 3; r++) {
                 for (int c = 0; c < 3; c++) {
                     char shapeChar = ' ';
                     if (r < shape.length && c < shape[r].length()) {
                         shapeChar = shape[r].charAt(c);
                     }

                     if (shapeChar != ' ' && shapedIngredients.containsKey(shapeChar)) {
                         RequiredItem required = shapedIngredients.get(shapeChar);
                         int matrixIndex = r * 3 + c; // Conceptual 0-8 index
                         int actualGuiSlot = inputSlotIndices[matrixIndex]; // Map to actual GUI slot

                         // Add the required amount for this part of the recipe to the total needed from that GUI slot
                         consumptionPerGuiSlot.put(actualGuiSlot,
                                 consumptionPerGuiSlot.getOrDefault(actualGuiSlot, 0) + required.getAmount());
                     }
                 }
             }

             // Perform the actual consumption from the GUI inventory
             for (Map.Entry<Integer, Integer> entry : consumptionPerGuiSlot.entrySet()) {
                 int guiSlot = entry.getKey();
                 int amountToConsume = entry.getValue();
                 ItemStack itemInSlot = guiInventory.getItem(guiSlot);

                 // Double-check if consumption is possible (should have been verified by matches())
                 if (itemInSlot == null || itemInSlot.getAmount() < amountToConsume) {
                     log.severe("[Consume Error] Recipe '" + id + "': Insufficient item in GUI slot " + guiSlot + " during consumption! Has: " + (itemInSlot != null ? itemInSlot.getAmount() : "null") + ", Needs: " + amountToConsume);
                     return false; // Should not happen if matches() was called first
                 }

                 // Consume the items
                 itemInSlot.setAmount(itemInSlot.getAmount() - amountToConsume);
                 // If stack is depleted, set slot to null (or handle container items like buckets)
                 guiInventory.setItem(guiSlot, itemInSlot.getAmount() > 0 ? itemInSlot : getContainerItem(itemInSlot));
             }
             return true; // Consumption successful
        } else if (type == RecipeType.SHAPELESS) {
            // TODO: Implement shapeless consumption logic
            return false;
        }
        return false;
    }

     // Helper to handle container items like buckets, potion bottles
    private ItemStack getContainerItem(ItemStack consumedItem) {
        if (consumedItem == null) return null;
        Material type = consumedItem.getType();
        if (type == Material.WATER_BUCKET || type == Material.LAVA_BUCKET || type == Material.MILK_BUCKET || type == Material.POWDER_SNOW_BUCKET) {
            return new ItemStack(Material.BUCKET);
        } else if (type.toString().contains("POTION") || type == Material.HONEY_BOTTLE || type == Material.DRAGON_BREATH) {
            return new ItemStack(Material.GLASS_BOTTLE);
        } else if (type == Material.MUSHROOM_STEW || type == Material.RABBIT_STEW || type == Material.BEETROOT_SOUP || type == Material.SUSPICIOUS_STEW) {
             return new ItemStack(Material.BOWL);
         }
        // Default: item is fully consumed
        return null;
    }


    public RequiredItem getRequirementForMatrixIndex(int matrixIndex) {
        if (type != RecipeType.SHAPED || matrixIndex < 0 || matrixIndex > 8 || shapedIngredients == null || shape == null) {
            return null;
        }
        int row = matrixIndex / 3;
        int col = matrixIndex % 3;

        if (row >= shape.length || col >= shape[row].length()) {
            return null; // Outside the defined recipe shape
        }

        char ingredientChar = shape[row].charAt(col);
        if (ingredientChar == ' ') {
            return null; // Empty slot in recipe
        }
        return shapedIngredients.get(ingredientChar); // Can return null if char somehow not in map (shouldn't happen after validation)
    }


    // Getters
    public String getId() { return id; }
    public RecipeType getType() { return type; }
    public ItemStack getResult() {
        if (itemManager == null) {
            log.severe("ItemManager is null in getResult for recipe '" + this.id + "'.");
            return new ItemStack(Material.BARRIER);
        }
        ItemStack resultTemplate = itemManager.getItem(this.resultItemId);
        if (resultTemplate == null) {
            log.severe("Result Item ID '" + this.resultItemId + "' (for recipe '" + this.id + "') not found in ItemManager!");
            return new ItemStack(Material.BARRIER);
        }
        ItemStack finalResult = resultTemplate.clone();
        finalResult.setAmount(this.resultAmount);
        return finalResult;
    }

    public enum RecipeType { SHAPED, SHAPELESS }
}

