package io.github.x1f4r.mmocraft.crafting.models;

import io.github.x1f4r.mmocraft.MMOCraft;
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
    private String[] shape; // Max 3x3, e.g., [" X ", "X X", " X "]
    private Map<Character, RequiredItem> shapedIngredients;

    private transient ItemManager itemManager;
    private static final Logger logger = MMOCraft.getPlugin(MMOCraft.class).getLogger();


    private CustomRecipe(String id, String resultItemId, int resultAmount, RecipeType type, ItemManager manager) {
        this.id = id;
        this.resultItemId = resultItemId;
        this.resultAmount = resultAmount;
        this.type = type;
        this.itemManager = manager;
    }

    public static CustomRecipe loadFromConfig(MMOCraft plugin, ItemManager itemManager, String id, ConfigurationSection config, Map<String, Tag<Material>> customTags) {
        ConfigurationSection resultSection = config.getConfigurationSection("result");
        if (resultSection == null) {
            plugin.getLogger().warning("Recipe '" + id + "' is missing result section!");
            return null;
        }
        String resultId = resultSection.getString("item_id");
        if (resultId == null || resultId.isEmpty()) {
            plugin.getLogger().warning("Recipe '" + id + "' result section is missing 'item_id'!");
            return null;
        }
        if (itemManager.getItem(resultId) == null) {
            plugin.getLogger().warning("Item ID '" + resultId + "' referenced by recipe '" + id + "' not found in ItemManager!");
            return null;
        }

        int resultAmount = resultSection.getInt("amount", 1);
        String typeStr = config.getString("type", "SHAPED").toUpperCase();
        RecipeType recipeType;
        try {
            recipeType = RecipeType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            recipeType = RecipeType.SHAPED;
        }

        CustomRecipe recipe = new CustomRecipe(id, resultId, resultAmount, recipeType, itemManager);

        if (recipeType == RecipeType.SHAPED) {
            List<String> shapeList = config.getStringList("shape");
            if (shapeList.isEmpty() || shapeList.size() > 3) {
                plugin.getLogger().warning("Invalid shape for SHAPED recipe '" + id + "'. Must be 1-3 rows.");
                return null;
            }
            recipe.shape = new String[shapeList.size()];
            for(int i = 0; i < shapeList.size(); i++) {
                String row = shapeList.get(i);
                if (row.length() > 3) {
                    plugin.getLogger().warning("Shape row '" + row + "' in recipe '" + id + "' exceeds 3 columns.");
                    return null;
                }
                recipe.shape[i] = String.format("%-3s", row); // Pad with spaces to 3 chars
            }

            ConfigurationSection ingredientsSection = config.getConfigurationSection("ingredients");
            if (ingredientsSection == null) {
                plugin.getLogger().warning("Missing ingredients section for SHAPED recipe '" + id + "'.");
                return null;
            }
            recipe.shapedIngredients = new HashMap<>();
            for (String key : ingredientsSection.getKeys(false)) {
                if (key.length() != 1) {
                    plugin.getLogger().warning("Invalid ingredient key '" + key + "' in recipe '" + id + "'. Must be a single character.");
                    continue;
                }
                char ingredientChar = key.charAt(0);
                RequiredItem requiredItem = RequiredItem.loadFromConfig(plugin, ingredientsSection.getConfigurationSection(key), customTags);
                if (requiredItem != null) {
                    recipe.shapedIngredients.put(ingredientChar, requiredItem);
                } else {
                    plugin.getLogger().warning("Invalid ingredient definition for key '" + key + "' in recipe '" + id + "'.");
                    return null;
                }
            }
            for (String row : recipe.shape) {
                for (char c : row.toCharArray()) {
                    if (c != ' ' && !recipe.shapedIngredients.containsKey(c)) {
                        plugin.getLogger().warning("Shape for recipe '" + id + "' contains char '" + c + "' not defined in ingredients.");
                        return null;
                    }
                }
            }
        } else if (recipeType == RecipeType.SHAPELESS) {
            plugin.getLogger().warning("Shapeless recipe loading not yet fully implemented for recipe '" + id + "'.");
            return null;
        }
        return recipe;
    }

    public boolean matches(ItemStack[] matrix) { // Takes a 9-slot matrix
        if (matrix == null || matrix.length != 9) {
            // logger.finer("[RecipeCheck]["+id+"] -> Fail: Invalid matrix provided.");
            return false;
        }

        if (type == RecipeType.SHAPED) {
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int matrixIndex = r * 3 + c;
                    ItemStack itemInMatrixSlot = matrix[matrixIndex];

                    char shapeChar = ' ';
                    if (r < shape.length && c < shape[r].length()) { // Check shape bounds
                        shapeChar = shape[r].charAt(c);
                    }

                    RequiredItem required = (shapeChar != ' ') ? shapedIngredients.get(shapeChar) : null;

                    if (required == null) { // Expect empty slot in recipe's shape
                        if (itemInMatrixSlot != null && itemInMatrixSlot.getType() != Material.AIR) {
                            // logger.finer("[RecipeCheck]["+id+"] -> Fail: Slot " + matrixIndex + " expected empty, found " + itemInMatrixSlot.getType());
                            return false;
                        }
                    } else { // Expect specific item based on recipe's shape
                        if (!required.matches(itemInMatrixSlot)) {
                            // logger.finer("[RecipeCheck]["+id+"] -> Fail: Slot " + matrixIndex + " requirement not met.");
                            return false;
                        }
                    }
                }
            }
            // logger.finer("[RecipeCheck]["+id+"] -> Success: Shaped recipe matches grid.");
            return true;
        } else if (type == RecipeType.SHAPELESS) {
            // TODO: Implement shapeless matching
            return false;
        }
        return false;
    }

    public boolean consumeIngredients(Inventory guiInventory, int[] inputSlotIndices) {
        if (inputSlotIndices == null || inputSlotIndices.length != 9) return false;

        if (type == RecipeType.SHAPED) {
            // This map will store: GUI Slot Index -> Total Amount to Consume from that GUI Slot
            Map<Integer, Integer> consumptionPerGuiSlot = new HashMap<>();

            for (int r = 0; r < 3; r++) { // Iterate through the 3x3 recipe shape definition
                for (int c = 0; c < 3; c++) {
                    char shapeChar = ' ';
                    if (r < shape.length && c < shape[r].length()) { // Check bounds for recipe.shape
                        shapeChar = shape[r].charAt(c);
                    }

                    if (shapeChar != ' ' && shapedIngredients.containsKey(shapeChar)) {
                        RequiredItem required = shapedIngredients.get(shapeChar);
                        int matrixIndex = r * 3 + c; // Conceptual 0-8 index in the 3x3 grid
                        int actualGuiSlot = inputSlotIndices[matrixIndex]; // Get the actual GUI slot number for this part of the recipe

                        // Accumulate amount needed from this specific GUI slot
                        consumptionPerGuiSlot.put(actualGuiSlot,
                                consumptionPerGuiSlot.getOrDefault(actualGuiSlot, 0) + required.getAmount());
                    }
                }
            }

            // Now perform actual consumption from the GUI inventory
            for (Map.Entry<Integer, Integer> entry : consumptionPerGuiSlot.entrySet()) {
                int guiSlot = entry.getKey();
                int amountToConsume = entry.getValue();
                ItemStack itemInSlot = guiInventory.getItem(guiSlot);

                if (itemInSlot == null || itemInSlot.getAmount() < amountToConsume) {
                    // This should ideally be caught by a matches() check before calling consume
                    logger.severe("[Consume] Recipe '" + id + "': Insufficient item in GUI slot " + guiSlot + " for consumption. Has: " + (itemInSlot != null ? itemInSlot.getAmount() : "null") + ", Needs: " + amountToConsume);
                    return false;
                }
                itemInSlot.setAmount(itemInSlot.getAmount() - amountToConsume);
                guiInventory.setItem(guiSlot, itemInSlot.getAmount() > 0 ? itemInSlot : null); // Set to null if depleted
            }
            // logger.finer("[Consume] Consumed ingredients successfully for SHAPED recipe " + id);
            return true;
        } else if (type == RecipeType.SHAPELESS) {
            // TODO: Implement shapeless consumption
            return false;
        }
        return false;
    }

    // New method for calculateMaxCrafts in CraftingGUIListener
    public RequiredItem getRequirementForMatrixIndex(int matrixIndex) {
        if (type != RecipeType.SHAPED || matrixIndex < 0 || matrixIndex > 8 || shapedIngredients == null || shape == null) {
            return null;
        }
        int row = matrixIndex / 3;
        int col = matrixIndex % 3;

        if (row >= shape.length || col >= shape[row].length()) { // Check bounds of the defined shape
            return null; // This part of the 3x3 matrix is outside the recipe's explicit shape (should be empty)
        }

        char ingredientChar = shape[row].charAt(col);
        if (ingredientChar == ' ') {
            return null; // Space in shape means no item required here
        }
        return shapedIngredients.get(ingredientChar);
    }


    // Getters
    public String getId() { return id; }
    public RecipeType getType() { return type; }
    public ItemStack getResult() {
        if (itemManager == null) {
            logger.severe("ItemManager is null in getResult for recipe '" + this.id + "'. Cannot create result item.");
            return new ItemStack(Material.BARRIER);
        }
        ItemStack resultTemplate = itemManager.getItem(this.resultItemId);
        if (resultTemplate == null) {
            logger.severe("Item ID '" + this.resultItemId + "' (result for recipe '" + this.id + "') not found in ItemManager!");
            return new ItemStack(Material.BARRIER);
        }
        ItemStack finalResult = resultTemplate.clone();
        finalResult.setAmount(this.resultAmount);
        return finalResult;
    }

    // This method might be less useful now if CraftingGUIListener directly uses INPUT_SLOTS_ARRAY
    // and maps to matrix indices for getRequirementForMatrixIndex.
    @Deprecated
    public RequiredItem getRequirementForGridSlot(int guiSlotIndex, int[] inputSlotsArray) {
        if (type != RecipeType.SHAPED || shapedIngredients == null || shape == null) return null;
        int matrixIndex = -1;
        for(int i=0; i < inputSlotsArray.length; i++){
            if(inputSlotsArray[i] == guiSlotIndex){
                matrixIndex = i;
                break;
            }
        }
        if(matrixIndex == -1) return null;

        return getRequirementForMatrixIndex(matrixIndex);
    }

    public enum RecipeType { SHAPED, SHAPELESS }
}