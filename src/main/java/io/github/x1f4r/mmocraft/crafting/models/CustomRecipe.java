package io.github.x1f4r.mmocraft.crafting.models;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import io.github.x1f4r.mmocraft.items.ItemManager;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class CustomRecipe {

    private final String id;
    private final String resultItemId;
    private final int resultAmount;
    private final RecipeType type;
    private String[] shape;
    private Map<Character, RequiredItem> shapedIngredients;
    private List<RequiredItem> shapelessIngredients;
    private final ItemManager itemManager;
    private static final Logger log = MMOPlugin.getMMOLogger();

    private CustomRecipe(String id, String resultItemId, int resultAmount, RecipeType type, ItemManager manager) {
        this.id = id;
        this.resultItemId = resultItemId;
        this.resultAmount = resultAmount;
        this.type = type;
        this.itemManager = manager;
        if (type == RecipeType.SHAPELESS) {
            this.shapelessIngredients = new ArrayList<>();
        }
    }

    public static CustomRecipe loadFromConfig(MMOCore core, String id, ConfigurationSection config, Map<String, Tag<Material>> customTags) {
        ItemManager itemManager = core.getItemManager();
        ConfigurationSection resultSection = config.getConfigurationSection("result");
        if (resultSection == null || !resultSection.contains("item_id")) {
            log.warning("Recipe '" + id + "' is missing result section or item_id! Skipping.");
            return null;
        }
        String resultId = resultSection.getString("item_id");
        if (itemManager.getItem(resultId) == null) {
            log.warning("Result Item ID '" + resultId + "' by recipe '" + id + "' not in ItemManager! Skipping recipe.");
            return null;
        }
        int resultAmount = resultSection.getInt("amount", 1);
        RecipeType recipeType = RecipeType.valueOf(config.getString("type", "SHAPED").toUpperCase());
        CustomRecipe recipe = new CustomRecipe(id, resultId, resultAmount, recipeType, itemManager);

        if (recipeType == RecipeType.SHAPED) {
            if (!loadShapedData(recipe, config, customTags)) return null;
        } else if (recipeType == RecipeType.SHAPELESS) {
            if (!loadShapelessData(recipe, config, customTags)) return null;
        } else {
            log.warning("Unknown recipe type for recipe '" + id + "'. Skipping.");
            return null;
        }
        return recipe;
    }

    private static boolean loadShapedData(CustomRecipe recipe, ConfigurationSection config, Map<String, Tag<Material>> customTags) {
        List<String> shapeList = config.getStringList("shape");
        if (shapeList.isEmpty() || shapeList.size() > 3 || shapeList.stream().anyMatch(r -> r.length() > 3)) {
            log.warning("Invalid shape for SHAPED recipe '" + recipe.id + "'. Skipping.");
            return false;
        }
        recipe.shape = shapeList.stream().map(r -> String.format("%-3s", r)).toArray(String[]::new);
        ConfigurationSection ingredientsSection = config.getConfigurationSection("ingredients");
        if (ingredientsSection == null) {
            log.warning("Missing ingredients for SHAPED recipe '" + recipe.id + "'. Skipping.");
            return false;
        }
        recipe.shapedIngredients = new HashMap<>();
        for (String key : ingredientsSection.getKeys(false)) {
            if (key.length() != 1 || key.charAt(0) == ' ') {
                log.warning("Invalid ingredient key '" + key + "' in recipe '" + recipe.id + "'. Skipping.");
                return false; 
            }
            RequiredItem reqItem = RequiredItem.loadFromConfig(ingredientsSection.getConfigurationSection(key), customTags);
            if (reqItem == null) {
                log.warning("Invalid ingredient for key '" + key + "' in recipe '" + recipe.id + "'. Skipping.");
                return false;
            }
            recipe.shapedIngredients.put(key.charAt(0), reqItem);
        }
        for (String row : recipe.shape) {
            for (char c : row.toCharArray()) {
                if (c != ' ' && !recipe.shapedIngredients.containsKey(c)) {
                    log.warning("Shape char '" + c + "' in recipe '" + recipe.id + "' not in ingredients. Skipping.");
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean loadShapelessData(CustomRecipe recipe, ConfigurationSection config, Map<String, Tag<Material>> customTags) {
        List<Map<?, ?>> ingredientsList = config.getMapList("ingredients");
        if (ingredientsList.isEmpty()) {
            log.warning("Missing ingredients list for SHAPELESS recipe '" + recipe.id + "'. Skipping.");
            return false;
        }
        for (Map<?, ?> itemMap : ingredientsList) {
            MemoryConfiguration tempMc = new MemoryConfiguration();
            itemMap.forEach((key, value) -> tempMc.set(String.valueOf(key), value));
            RequiredItem reqItem = RequiredItem.loadFromConfig(tempMc, customTags);
            if (reqItem == null) {
                log.warning("Invalid ingredient in SHAPELESS recipe '" + recipe.id + "'. Skipping.");
                return false;
            }
            recipe.shapelessIngredients.add(reqItem);
        }
        return !recipe.shapelessIngredients.isEmpty();
    }

    public boolean matches(ItemStack[] matrix) {
        if (matrix == null || matrix.length != 9) return false;

        if (type == RecipeType.SHAPED) {
            for (int r = 0; r < shape.length; r++) {
                for (int c = 0; c < shape[r].length(); c++) {
                    ItemStack itemInMatrix = matrix[r * 3 + c];
                    char shapeChar = shape[r].charAt(c);
                    RequiredItem required = (shapeChar != ' ') ? shapedIngredients.get(shapeChar) : null;
                    if (required == null) { // Expect empty
                        if (itemInMatrix != null && itemInMatrix.getType() != Material.AIR) return false;
                    } else { // Expect specific item
                        if (!required.matches(itemInMatrix)) return false; // matches() checks type AND amount
                    }
                }
            }
            return true;
        } else if (type == RecipeType.SHAPELESS) {
            if (shapelessIngredients == null || shapelessIngredients.isEmpty()) {
                for (ItemStack item : matrix) if (item != null && item.getType() != Material.AIR) return false;
                return true; // Empty recipe needs empty grid
            }
            List<ItemStack> availableItems = new ArrayList<>();
            for (ItemStack item : matrix) if (item != null && item.getType() != Material.AIR) availableItems.add(item.clone());

            for (RequiredItem req : shapelessIngredients) {
                int amountNeeded = req.getAmount();
                boolean foundRequirement = false;
                for (int i = 0; i < availableItems.size(); i++) {
                    ItemStack currentItem = availableItems.get(i);
                    if (currentItem == null || currentItem.getAmount() == 0) continue;

                    if (req.matchesTypeAndCustomId(currentItem)) {
                        int canTake = Math.min(amountNeeded, currentItem.getAmount());
                        currentItem.setAmount(currentItem.getAmount() - canTake);
                        amountNeeded -= canTake;
                        if (currentItem.getAmount() == 0) availableItems.set(i, null);
                        if (amountNeeded == 0) {
                            foundRequirement = true;
                            break;
                        }
                    }
                }
                if (!foundRequirement) return false;
            }
            // Optional: if strict (no leftovers) is desired, check availableItems for non-null/non-empty stacks
            return true;
        }
        return false;
    }

    public boolean consumeIngredients(Inventory guiInventory, int[] inputSlotIndices) {
        if (inputSlotIndices == null || inputSlotIndices.length != 9) return false;

        if (type == RecipeType.SHAPED) {
            for (int r = 0; r < shape.length; r++) {
                for (int c = 0; c < shape[r].length(); c++) {
                    char shapeChar = shape[r].charAt(c);
                    if (shapeChar != ' ') {
                        RequiredItem required = shapedIngredients.get(shapeChar);
                        if (required == null) { log.severe("Logic error: shaped recipe char not in map: " + shapeChar); return false;}
                        int slotIndex = inputSlotIndices[r * 3 + c];
                        ItemStack itemInSlot = guiInventory.getItem(slotIndex);
                        if (itemInSlot == null || itemInSlot.getAmount() < required.getAmount()) {
                            log.severe("Consume fail (shaped): slot " + slotIndex + " item mismatch. Recipe: " + id);
                            return false; 
                        }
                        itemInSlot.setAmount(itemInSlot.getAmount() - required.getAmount());
                        guiInventory.setItem(slotIndex, itemInSlot.getAmount() > 0 ? itemInSlot : getContainerItem(itemInSlot));
                    }
                }
            }
            return true;
        } else if (type == RecipeType.SHAPELESS) {
            if (shapelessIngredients == null || shapelessIngredients.isEmpty()) return true;

            for (RequiredItem req : shapelessIngredients) {
                int amountToConsume = req.getAmount();
                for (int slotIndex : inputSlotIndices) {
                    if (amountToConsume == 0) break;
                    ItemStack itemInSlot = guiInventory.getItem(slotIndex);
                    if (itemInSlot != null && itemInSlot.getAmount() > 0 && req.matchesTypeAndCustomId(itemInSlot)) {
                        int canTake = Math.min(amountToConsume, itemInSlot.getAmount());
                        itemInSlot.setAmount(itemInSlot.getAmount() - canTake);
                        amountToConsume -= canTake;
                        guiInventory.setItem(slotIndex, itemInSlot.getAmount() > 0 ? itemInSlot : getContainerItem(itemInSlot));
                    }
                }
                if (amountToConsume > 0) {
                    log.severe("Consume fail (shapeless): req " + req + " not fully met. Recipe: " + id);
                    return false; 
                }
            }
            return true;
        }
        return false;
    }

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
        return null;
    }
    public RequiredItem getRequirementForMatrixIndex(int matrixIndex) {
        if (type != RecipeType.SHAPED || matrixIndex < 0 || matrixIndex > 8 || shapedIngredients == null || shape == null) return null;
        int row = matrixIndex / 3; int col = matrixIndex % 3;
        if (row >= shape.length || col >= shape[row].length()) return null;
        char ingredientChar = shape[row].charAt(col);
        return (ingredientChar == ' ') ? null : shapedIngredients.get(ingredientChar);
    }
    public List<RequiredItem> getShapelessIngredients() { return shapelessIngredients; }
    public String getId() { return id; }
    public RecipeType getType() { return type; }
    public ItemStack getResult() {
        ItemStack template = itemManager.getItem(this.resultItemId);
        if (template == null) {
            log.severe("Result Item ID '" + this.resultItemId + "' (recipe '" + this.id + "') not in ItemManager!");
            return new ItemStack(Material.BARRIER);
        }
        ItemStack finalResult = template.clone();
        finalResult.setAmount(this.resultAmount);
        return finalResult;
    }
    public enum RecipeType { SHAPED, SHAPELESS }
}

