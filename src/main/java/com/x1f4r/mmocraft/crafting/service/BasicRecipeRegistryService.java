package com.x1f4r.mmocraft.crafting.service;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.crafting.model.CustomRecipeIngredient;
import com.x1f4r.mmocraft.crafting.model.RecipeType;
import com.x1f4r.mmocraft.crafting.recipe.CustomRecipe;
import com.x1f4r.mmocraft.item.model.CustomItem;
import com.x1f4r.mmocraft.item.service.CustomItemRegistry;
import com.x1f4r.mmocraft.util.LoggingUtil;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BasicRecipeRegistryService implements RecipeRegistryService {

    private final MMOCraftPlugin plugin;
    private final LoggingUtil logger;
    private final CustomItemRegistry itemRegistry;
    private final Map<String, CustomRecipe> recipes = new ConcurrentHashMap<>();

    public BasicRecipeRegistryService(MMOCraftPlugin plugin, LoggingUtil logger, CustomItemRegistry itemRegistry) {
        this.plugin = plugin;
        this.logger = logger;
        this.itemRegistry = itemRegistry;
        logger.debug("BasicRecipeRegistryService initialized.");
    }

    @Override
    public void registerRecipe(CustomRecipe recipe) {
        if (recipe == null || recipe.getRecipeId() == null || recipe.getRecipeId().trim().isEmpty()) {
            logger.warning("Attempted to register a null recipe or recipe with an invalid ID.");
            return;
        }
        CustomRecipe existing = recipes.put(recipe.getRecipeId().toLowerCase(), recipe);
        if (existing != null) {
            logger.warning("Recipe ID '" + recipe.getRecipeId() + "' was already registered. Overwriting.");
        } else {
            logger.info("Registered recipe: " + recipe.getRecipeId() + " (Output: " + recipe.getOutputItemStack().getType() + ")");
        }
    }

    @Override
    public Optional<CustomRecipe> getRecipeById(String recipeId) {
        if (recipeId == null) return Optional.empty();
        return Optional.ofNullable(recipes.get(recipeId.toLowerCase()));
    }

    @Override
    public List<CustomRecipe> getRecipesForOutput(ItemStack outputMatcher) {
        if (outputMatcher == null || outputMatcher.getType().isAir()) {
            return Collections.emptyList();
        }
        String customItemId = CustomItem.getItemId(outputMatcher, plugin);

        return recipes.values().stream()
            .filter(recipe -> {
                ItemStack recipeOutput = recipe.getOutputItemStack();
                if (recipeOutput.getType() != outputMatcher.getType()) {
                    return false;
                }
                String recipeCustomId = CustomItem.getItemId(recipeOutput, plugin);
                // If outputMatcher has a custom ID, recipe must also have it and match.
                // If outputMatcher has no custom ID, recipe also should not have one.
                return Objects.equals(customItemId, recipeCustomId);
                // Further NBT matching could be added here if necessary.
            })
            .collect(Collectors.toList());
    }

    @Override
    public Optional<CustomRecipe> findMatchingRecipe(RecipeType type, Inventory craftingGridInventory) {
        // This is a placeholder for actual recipe matching logic.
        // A real implementation would be significantly more complex.
        logger.finer("Attempting to find matching recipe for type " + type + " in inventory of size " + craftingGridInventory.getSize());

        // For CUSTOM_SHAPELESS (very basic example, counts items)
        if (type == RecipeType.CUSTOM_SHAPELESS) {
            Map<String, Integer> providedIngredients = new HashMap<>();
            int nonNullItems = 0;
            for (ItemStack item : craftingGridInventory.getContents()) {
                if (item != null && !item.getType().isAir()) {
                    nonNullItems++;
                    String key = getItemKey(item);
                    providedIngredients.merge(key, item.getAmount(), Integer::sum);
                }
            }
             if (nonNullItems == 0) return Optional.empty();


            for (CustomRecipe recipe : recipes.values()) {
                if (recipe.getRecipeType() == RecipeType.CUSTOM_SHAPELESS) {
                    if (matchesShapeless(recipe.getShapelessIngredients(), providedIngredients)) {
                        return Optional.of(recipe);
                    }
                }
            }
        }
        // For CUSTOM_SHAPED (placeholder)
        else if (type == RecipeType.CUSTOM_SHAPED) {
            // TODO: Implement shaped recipe matching.
            // This involves:
            // 1. Getting the items from the grid in order (e.g., ItemStack[9] for 3x3).
            // 2. For each shaped recipe:
            //    a. Comparing its ingredient map (slot -> CustomRecipeIngredient)
            //       with the items in the grid at corresponding slots.
            //    b. Handling rotations or mirrored versions if the recipe allows.
            //    c. Using CustomRecipeIngredient.matches() for each comparison.
            logger.warning("Shaped recipe matching is not yet fully implemented in BasicRecipeRegistryService.");
        }

        return Optional.empty();
    }

    private String getItemKey(ItemStack item) {
        if (item == null) return "";
        String customId = CustomItem.getItemId(item, plugin);
        if (customId != null) {
            return "CUSTOM:" + customId;
        }
        return "VANILLA:" + item.getType().name();
    }

    private boolean matchesShapeless(List<CustomRecipeIngredient> recipeIngredients, Map<String, Integer> providedIngredientsCounts) {
        if (recipeIngredients.size() != providedIngredientsCounts.values().stream().mapToInt(Integer::intValue).sum() &&
            recipeIngredients.size() != providedIngredientsCounts.size()) { // Quick check: number of distinct item types or total item count might differ
            // This check is not perfect because recipe might have 2 STICK, grid has 1 STACK of 2 STICK.
            // A more robust check counts distinct item types and their quantities.
        }

        Map<String, Integer> requiredCounts = new HashMap<>();
        for (CustomRecipeIngredient req : recipeIngredients) {
            String key = req.getType() == CustomRecipeIngredient.IngredientType.CUSTOM_ITEM ? "CUSTOM:" + req.getIdentifier() : "VANILLA:" + req.getIdentifier();
            requiredCounts.merge(key, req.getQuantity(), Integer::sum);
        }

        if (requiredCounts.size() != providedIngredientsCounts.size()) {
            return false; // Different number of distinct item types
        }

        for (Map.Entry<String, Integer> requiredEntry : requiredCounts.entrySet()) {
            if (!providedIngredientsCounts.containsKey(requiredEntry.getKey()) ||
                providedIngredientsCounts.get(requiredEntry.getKey()) < requiredEntry.getValue()) {
                return false; // Missing ingredient or not enough quantity
            }
        }
        return true;
    }


    @Override
    public List<CustomRecipe> getAllRecipes() {
        return new ArrayList<>(recipes.values()); // Return a copy
    }

    @Override
    public boolean unregisterRecipe(String recipeId) {
        if (recipeId == null) return false;
        CustomRecipe removed = recipes.remove(recipeId.toLowerCase());
        if (removed != null) {
            logger.info("Unregistered recipe: " + recipeId);
            return true;
        }
        return false;
    }
}
