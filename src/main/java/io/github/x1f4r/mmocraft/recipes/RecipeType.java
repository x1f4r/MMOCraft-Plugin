package io.github.x1f4r.mmocraft.recipes;

/**
 * Defines the type of a custom crafting recipe.
 */
public enum RecipeType {
    /** A recipe with a specific shape in the crafting grid. */
    SHAPED,
    /** A recipe where ingredient placement does not matter. */
    SHAPELESS
    // Future considerations:
    // SMITHING, STONECUTTING, FURNACE (smelting/blasting/smoking), CAMPFIRE_COOKING
}