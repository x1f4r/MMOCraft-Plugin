package io.github.x1f4r.mmocraft.recipes;

import io.github.x1f4r.mmocraft.items.RequiredIngredient;
import io.github.x1f4r.mmocraft.items.RequirementType;
import io.github.x1f4r.mmocraft.services.ItemService;
import io.github.x1f4r.mmocraft.services.RecipeService; // For resolving tags within match
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents an immutable custom crafting recipe definition.
 */
public record CustomRecipe(
        @NotNull String id,             // Unique ID for this recipe (e.g., "aspect_of_the_end_craft")
        @NotNull RecipeType type,
        @NotNull String resultItemId,   // ID of the CustomItem (from ItemService) to be produced, or "VANILLA:MATERIAL_NAME"
        int resultAmount,

        // For SHAPED recipes
        @Nullable List<String> shape,         // e.g., ["ABA", " C ", " D "] (max 3x3), null if not shaped
        @Nullable Map<Character, RequiredIngredient> shapedIngredients, // Key: char in shape, Value: ingredient, null if not shaped

        // For SHAPELESS recipes
        @Nullable List<RequiredIngredient> shapelessIngredients, // null if not shapeless
        boolean strictShapelessIngredientCount // If true, shapeless must use exact number of items matching type (no extra non-matching items in grid)
) {
    public CustomRecipe { // Compact constructor for validation and defaults
        Objects.requireNonNull(id, "Recipe ID cannot be null.");
        Objects.requireNonNull(type, "RecipeType cannot be null.");
        Objects.requireNonNull(resultItemId, "Result Item ID cannot be null.");
        if (resultAmount < 1) resultAmount = 1;

        if (type == RecipeType.SHAPED) {
            Objects.requireNonNull(shape, "Shape cannot be null for SHAPED recipe.");
            Objects.requireNonNull(shapedIngredients, "Shaped ingredients map cannot be null for SHAPED recipe.");
            if (shape.size() > 3 || shape.stream().anyMatch(row -> row.length() > 3)) {
                throw new IllegalArgumentException("Recipe shape cannot exceed 3x3 for recipe: " + id);
            }
            // Normalize shape: ensure all rows are same length (max 3), padded with spaces.
            // This is better handled during parsing in RecipeService.
        } else if (type == RecipeType.SHAPELESS) {
            Objects.requireNonNull(shapelessIngredients, "Shapeless ingredients list cannot be null for SHAPELESS recipe.");
            if (shapelessIngredients.isEmpty()) {
                throw new IllegalArgumentException("Shapeless recipe must have at least one ingredient: " + id);
            }
        }
    }

    /**
     * Checks if the provided crafting matrix matches this recipe.
     * The matrix is a 9-slot ItemStack array representing the 3x3 crafting grid.
     * Slots 0-2 are top row, 3-5 middle, 6-8 bottom.
     *
     * @param matrix The 3x3 crafting grid contents.
     * @param itemService To validate custom item ingredients.
     * @param recipeService For resolving material tags.
     * @return true if the matrix matches, false otherwise.
     */
    public boolean matches(@NotNull ItemStack[] matrix, @NotNull ItemService itemService, @NotNull RecipeService recipeService) {
        if (matrix.length != 9) return false; // Must be a 3x3 grid representation

        if (this.type == RecipeType.SHAPED) {
            return matchesShaped(matrix, itemService, recipeService);
        } else if (this.type == RecipeType.SHAPELESS) {
            return matchesShapeless(matrix, itemService, recipeService);
        }
        return false;
    }

    private boolean matchesShaped(@NotNull ItemStack[] matrix, @NotNull ItemService itemService, @NotNull RecipeService recipeService) {
        // This shaped matching needs to be robust for different recipe sizes (1x1 up to 3x3)
        // and positions within the 3x3 grid.

        // Determine actual recipe dimensions
        int recipeHeight = Objects.requireNonNull(shape).size();
        int recipeWidth = 0;
        for (String row : shape) {
            recipeWidth = Math.max(recipeWidth, row.length()); // Don't trim, space can be significant if char is ' '
        }

        if (recipeHeight == 0 || recipeWidth == 0) return true; // An empty shape matches an empty grid conceptually (or false if desired)

        // Iterate through all possible top-left starting positions of the recipe within the 3x3 grid
        for (int rowOffset = 0; rowOffset <= (3 - recipeHeight); rowOffset++) {
            for (int colOffset = 0; colOffset <= (3 - recipeWidth); colOffset++) {
                if (checkShapedMatchAtOffset(matrix, rowOffset, colOffset, recipeHeight, recipeWidth, itemService, recipeService)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkShapedMatchAtOffset(@NotNull ItemStack[] matrix, int rOffset, int cOffset,
                                             int recipeHeight, int recipeWidth,
                                             @NotNull ItemService itemService, @NotNull RecipeService recipeService) {
        // Check recipe area
        for (int r = 0; r < recipeHeight; r++) { // Iterate through recipe rows
            String recipeRowString = Objects.requireNonNull(shape).get(r);
            for (int c = 0; c < recipeWidth; c++) { // Iterate through recipe columns
                char recipeChar = (c < recipeRowString.length()) ? recipeRowString.charAt(c) : ' ';
                ItemStack itemInGrid = matrix[(r + rOffset) * 3 + (c + cOffset)]; // Corresponding item in the 3x3 crafting grid
                RequiredIngredient required = (recipeChar != ' ') ? Objects.requireNonNull(shapedIngredients).get(recipeChar) : null;

                if (required == null) { // Recipe expects an empty slot (space char)
                    if (itemInGrid != null && !itemInGrid.getType().isAir()) return false; // Grid has an item where recipe expects empty
                } else { // Recipe expects a specific ingredient
                    Tag<Material> resolvedTag = (required.type() == RequirementType.TAG) ? recipeService.resolveMaterialTag(required.value()) : null;
                    if (!required.matchesFully(itemInGrid, itemService, resolvedTag)) { // Check type AND amount
                        return false; // Mismatch in type or insufficient amount
                    }
                }
            }
        }

        // Check remaining grid slots outside the recipe's current offsetted shape – they must be empty.
        for (int rGrid = 0; rGrid < 3; rGrid++) {
            for (int cGrid = 0; cGrid < 3; cGrid++) {
                // Is this grid cell part of the current recipe placement?
                boolean isInRecipeArea = (rGrid >= rOffset && rGrid < (rOffset + recipeHeight) &&
                        cGrid >= cOffset && cGrid < (cOffset + recipeWidth) &&
                        // Also ensure the char at this recipe position was not a space
                        (cGrid - cOffset < Objects.requireNonNull(shape).get(rGrid - rOffset).length() &&
                                shape.get(rGrid - rOffset).charAt(cGrid - cOffset) != ' '));

                if (!isInRecipeArea) { // If this grid cell is OUTSIDE the recipe's pattern
                    if (matrix[rGrid * 3 + cGrid] != null && !matrix[rGrid * 3 + cGrid].getType().isAir()) {
                        return false; // Found an item in the grid where the recipe implies empty space
                    }
                }
            }
        }
        return true; // All conditions met for this offset
    }


    private boolean matchesShapeless(@NotNull ItemStack[] matrix, @NotNull ItemService itemService, @NotNull RecipeService recipeService) {
        List<RequiredIngredient> remainingRequirements = new ArrayList<>(Objects.requireNonNull(shapelessIngredients));
        List<ItemStack> availableItemsInGrid = new ArrayList<>();
        int nonAirItemCountInGrid = 0;

        for (ItemStack item : matrix) {
            if (item != null && !item.getType().isAir()) {
                availableItemsInGrid.add(item.clone()); // Use clones for modification during check
                nonAirItemCountInGrid++;
            }
        }

        if (strictShapelessIngredientCount && nonAirItemCountInGrid != shapelessIngredients.size()) {
            // If strict, the number of non-air items in grid must exactly match the number of ingredients.
            // This assumes each ingredient in shapelessIngredients has amount 1 for this check to be simple.
            // A more complex strict check would involve sum of ingredient amounts.
            // For now, this is a basic "no extra items" check if all required are amount 1.
            return false;
        }

        if (availableItemsInGrid.size() < remainingRequirements.size() && !strictShapelessIngredientCount){
            // If not strict, but fewer items in grid than required ingredient types, cannot match.
            // This is also a simplification if ingredients have amounts > 1.
            // A better check: sum of available item amounts vs sum of required ingredient amounts.
            return false;
        }


        for (RequiredIngredient required : shapelessIngredients) {
            boolean foundMatchForThisRequirement = false;
            for (int i = 0; i < availableItemsInGrid.size(); i++) {
                ItemStack itemInGrid = availableItemsInGrid.get(i);
                if (itemInGrid == null || itemInGrid.getAmount() < required.amount()) continue;

                Tag<Material> resolvedTag = (required.type() == RequirementType.TAG) ? recipeService.resolveMaterialTag(required.value()) : null;
                if (required.matchesTypeAndCustomId(itemInGrid, itemService, resolvedTag)) {
                    // Matched type, "consume" for this check
                    itemInGrid.setAmount(itemInGrid.getAmount() - required.amount());
                    if (itemInGrid.getAmount() <= 0) {
                        availableItemsInGrid.set(i, null); // Mark as fully consumed for this check
                    }
                    foundMatchForThisRequirement = true;
                    remainingRequirements.remove(required); // This simple remove works if ingredients are distinct enough.
                    // For identical RequiredIngredient objects, it removes first.
                    // A count-based map for requirements is more robust.
                    break; // Move to the next RequiredIngredient
                }
            }
            if (!foundMatchForThisRequirement) {
                return false; // A required ingredient was not found in sufficient quantity
            }
        }

        // If strict, all items from the grid must have been "consumed" (availableItemsInGrid all null or amount 0)
        if (strictShapelessIngredientCount) {
            for (ItemStack item : availableItemsInGrid) {
                if (item != null && item.getAmount() > 0) return false; // Extra item left in grid
            }
        }

        return remainingRequirements.isEmpty(); // All requirements satisfied
    }

    /**
     * Gets the resulting ItemStack for this recipe.
     * @param itemService Used to create the ItemStack if it's a custom item.
     * @return The resulting ItemStack.
     */
    @NotNull
    public ItemStack getResult(@NotNull ItemService itemService) {
        if (resultItemId.toUpperCase().startsWith("VANILLA:")) {
            try {
                Material mat = Material.matchMaterial(resultItemId.substring("VANILLA:".length()));
                if (mat != null) {
                    return new ItemStack(mat, resultAmount);
                }
            } catch (Exception ignored) {}
            // Fallback or error if vanilla material invalid
            MMOCraft.getPluginLogger().warning("CustomRecipe " + id + " has invalid VANILLA result_item_id: " + resultItemId);
            return new ItemStack(Material.BARRIER, resultAmount);
        }
        // Assume it's a custom item ID
        return itemService.createItemStack(resultItemId, resultAmount);
    }

    /**
     * Helper for shaped recipe consumption to know what was in a specific matrix slot
     * according to the recipe's shape and ingredients for the successful match.
     * This is conceptual and needs to be tied to the offset where the match occurred.
     * @param matrixIndex 0-8 index of the crafting grid.
     * @param rOffset row offset of the successful match.
     * @param cOffset col offset of the successful match.
     * @return The RequiredIngredient or null if the slot was empty in the recipe shape.
     */
    public RequiredIngredient getRequirementForMatrixIndex(int matrixIndex, int rOffset, int cOffset) {
        if (this.type != RecipeType.SHAPED || shape == null || shapedIngredients == null) return null;

        int rGrid = matrixIndex / 3;
        int cGrid = matrixIndex % 3;

        int rRecipe = rGrid - rOffset;
        int cRecipe = cGrid - cOffset;

        if (rRecipe >= 0 && rRecipe < shape.size() && cRecipe >= 0 && cRecipe < shape.get(rRecipe).length()) {
            char recipeChar = shape.get(rRecipe).charAt(cRecipe);
            if (recipeChar != ' ') {
                return shapedIngredients.get(recipeChar);
            }
        }
        return null; // Slot was outside the recipe shape for this match, or was a space in recipe.
    }
}