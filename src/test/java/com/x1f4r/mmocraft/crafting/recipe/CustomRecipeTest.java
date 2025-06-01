package com.x1f4r.mmocraft.crafting.recipe;

import com.x1f4r.mmocraft.crafting.model.CustomRecipeIngredient;
import com.x1f4r.mmocraft.crafting.model.RecipeType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class CustomRecipeTest {

    private ItemStack validOutput;
    private List<CustomRecipeIngredient> shapelessIngredients;
    private Map<Integer, CustomRecipeIngredient> shapedIngredients;

    @BeforeEach
    void setUp() {
        validOutput = new ItemStack(Material.DIAMOND_SWORD, 1);

        shapelessIngredients = List.of(
            new CustomRecipeIngredient(CustomRecipeIngredient.IngredientType.VANILLA_MATERIAL, "DIAMOND", 2),
            new CustomRecipeIngredient(CustomRecipeIngredient.IngredientType.VANILLA_MATERIAL, "STICK", 1)
        );

        shapedIngredients = new HashMap<>();
        shapedIngredients.put(0, new CustomRecipeIngredient(CustomRecipeIngredient.IngredientType.VANILLA_MATERIAL, "DIAMOND", 1)); // Top-left
        shapedIngredients.put(3, new CustomRecipeIngredient(CustomRecipeIngredient.IngredientType.VANILLA_MATERIAL, "DIAMOND", 1)); // Middle-left
        shapedIngredients.put(6, new CustomRecipeIngredient(CustomRecipeIngredient.IngredientType.VANILLA_MATERIAL, "STICK", 1));   // Bottom-left
    }

    @Test
    void constructor_shapeless_validArgs_createsInstance() {
        CustomRecipe recipe = new CustomRecipe("test_sword", validOutput, RecipeType.CUSTOM_SHAPELESS, shapelessIngredients, null);
        assertEquals("test_sword", recipe.getRecipeId());
        assertEquals(validOutput, recipe.getOutputItemStack());
        assertEquals(RecipeType.CUSTOM_SHAPELESS, recipe.getRecipeType());
        assertFalse(recipe.isShaped());
        assertEquals(shapelessIngredients, recipe.getShapelessIngredients());
        assertTrue(recipe.getShapedIngredients().isEmpty());
        assertNull(recipe.getPermissionRequired());
        assertFalse(recipe.hasPermission());
    }

    @Test
    void constructor_shaped_validArgs_createsInstance() {
        CustomRecipe recipe = new CustomRecipe("test_pick", validOutput, RecipeType.CUSTOM_SHAPED, shapedIngredients, "mmocraft.craft.pick");
        assertEquals("test_pick", recipe.getRecipeId());
        assertEquals(RecipeType.CUSTOM_SHAPED, recipe.getRecipeType());
        assertTrue(recipe.isShaped());
        assertEquals(shapedIngredients, recipe.getShapedIngredients());
        assertTrue(recipe.getShapelessIngredients().isEmpty());
        assertEquals("mmocraft.craft.pick", recipe.getPermissionRequired());
        assertTrue(recipe.hasPermission());
    }

    @Test
    void constructor_nullId_throwsException() {
        assertThrows(NullPointerException.class, () ->
            new CustomRecipe(null, validOutput, RecipeType.CUSTOM_SHAPELESS, shapelessIngredients, null));
    }

    @Test
    void constructor_nullOutput_throwsException() {
        assertThrows(NullPointerException.class, () ->
            new CustomRecipe("test_id", null, RecipeType.CUSTOM_SHAPELESS, shapelessIngredients, null));
    }

    @Test
    void constructor_airOutput_throwsException() {
        ItemStack airOutput = new ItemStack(Material.AIR);
        assertThrows(IllegalArgumentException.class, () ->
            new CustomRecipe("test_id", airOutput, RecipeType.CUSTOM_SHAPELESS, shapelessIngredients, null));
    }

    @Test
    void constructor_zeroAmountOutput_throwsException() {
        ItemStack zeroOutput = new ItemStack(Material.DIAMOND, 0);
        assertThrows(IllegalArgumentException.class, () ->
            new CustomRecipe("test_id", zeroOutput, RecipeType.CUSTOM_SHAPELESS, shapelessIngredients, null));
    }


    @Test
    void constructor_shapeless_nullIngredients_throwsException() {
        assertThrows(NullPointerException.class, () ->
            new CustomRecipe("test_id", validOutput, RecipeType.CUSTOM_SHAPELESS, (List<CustomRecipeIngredient>) null, null));
    }

    @Test
    void constructor_shaped_nullIngredients_throwsException() {
        assertThrows(NullPointerException.class, () ->
            new CustomRecipe("test_id", validOutput, RecipeType.CUSTOM_SHAPED, (Map<Integer, CustomRecipeIngredient>) null, null));
    }

    @Test
    void constructor_mismatchedTypeForShapeless_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new CustomRecipe("test_id", validOutput, RecipeType.CUSTOM_SHAPED, shapelessIngredients, null));
    }

    @Test
    void constructor_mismatchedTypeForShaped_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new CustomRecipe("test_id", validOutput, RecipeType.CUSTOM_SHAPELESS, shapedIngredients, null));
    }

    @Test
    void getOutputItemStack_returnsClone() {
        CustomRecipe recipe = new CustomRecipe("test_sword", validOutput, RecipeType.CUSTOM_SHAPELESS, shapelessIngredients, null);
        ItemStack output1 = recipe.getOutputItemStack();
        ItemStack output2 = recipe.getOutputItemStack();
        assertNotSame(output1, output2, "Should return a clone of the output item stack.");
        assertEquals(output1, output2);
    }

    @Test
    void getRemainingItems_default_returnsEmptyList() {
        CustomRecipe recipe = new CustomRecipe("test_sword", validOutput, RecipeType.CUSTOM_SHAPELESS, shapelessIngredients, null);
        assertTrue(recipe.getRemainingItems(null).isEmpty()); // Inventory not used in default impl
    }
}
