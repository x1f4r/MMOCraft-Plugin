package com.x1f4r.mmocraft.crafting.model;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.item.model.CustomItem;
import com.x1f4r.mmocraft.item.service.CustomItemRegistry;
import com.x1f4r.mmocraft.util.LoggingUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic; // Added
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomRecipeIngredientTest {

    @Mock private CustomItemRegistry mockItemRegistry;
    @Mock private MMOCraftPlugin mockPlugin;
    @Mock private LoggingUtil mockLogger;

    @BeforeEach
    void setUp() {
        // For CustomItem.getItemId static method, and potentially for logging in CustomRecipeIngredient
        lenient().when(mockPlugin.getName()).thenReturn("MMOCraftTest");
        lenient().when(mockPlugin.getLoggingUtil()).thenReturn(mockLogger);
    }

    @Test
    void constructor_validVanilla_createsInstance() {
        CustomRecipeIngredient ingredient = new CustomRecipeIngredient(
            CustomRecipeIngredient.IngredientType.VANILLA_MATERIAL,
            "DIAMOND", 1);
        assertEquals(CustomRecipeIngredient.IngredientType.VANILLA_MATERIAL, ingredient.getType());
        assertEquals("DIAMOND", ingredient.getIdentifier());
        assertEquals(1, ingredient.getQuantity());
        assertFalse(ingredient.shouldMatchNBT());
    }

    @Test
    void constructor_validCustomItem_createsInstance() {
        CustomRecipeIngredient ingredient = new CustomRecipeIngredient(
            CustomRecipeIngredient.IngredientType.CUSTOM_ITEM,
            "mythic_sword", 1, true);
        assertEquals(CustomRecipeIngredient.IngredientType.CUSTOM_ITEM, ingredient.getType());
        assertEquals("MYTHIC_SWORD", ingredient.getIdentifier()); // Should be uppercased
        assertEquals(1, ingredient.getQuantity());
        assertTrue(ingredient.shouldMatchNBT());
    }

    @Test
    void constructor_invalidQuantity_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new CustomRecipeIngredient(
            CustomRecipeIngredient.IngredientType.VANILLA_MATERIAL, "IRON_INGOT", 0));
    }

    @Test
    void matches_vanillaMaterial_correctMaterialAndQuantity_returnsTrue() {
        CustomRecipeIngredient ingredient = new CustomRecipeIngredient(
            CustomRecipeIngredient.IngredientType.VANILLA_MATERIAL, "GOLD_INGOT", 2);
        ItemStack itemStack = new ItemStack(Material.GOLD_INGOT, 2);
        assertTrue(ingredient.matches(itemStack, mockItemRegistry, mockPlugin));
    }

    @Test
    void matches_vanillaMaterial_incorrectMaterial_returnsFalse() {
        CustomRecipeIngredient ingredient = new CustomRecipeIngredient(
            CustomRecipeIngredient.IngredientType.VANILLA_MATERIAL, "GOLD_INGOT", 1);
        ItemStack itemStack = new ItemStack(Material.IRON_INGOT, 1);
        assertFalse(ingredient.matches(itemStack, mockItemRegistry, mockPlugin));
    }

    @Test
    void matches_vanillaMaterial_insufficientQuantity_returnsFalse() {
        CustomRecipeIngredient ingredient = new CustomRecipeIngredient(
            CustomRecipeIngredient.IngredientType.VANILLA_MATERIAL, "DIAMOND", 3);
        ItemStack itemStack = new ItemStack(Material.DIAMOND, 2);
        assertFalse(ingredient.matches(itemStack, mockItemRegistry, mockPlugin));
    }

    @Test
    void matches_vanillaMaterial_moreQuantityThanRequired_returnsTrue() {
        // The ingredient defines minimum needed. If stack has more, it's still a match for that minimum.
        CustomRecipeIngredient ingredient = new CustomRecipeIngredient(
            CustomRecipeIngredient.IngredientType.VANILLA_MATERIAL, "COAL", 1);
        ItemStack itemStack = new ItemStack(Material.COAL, 5);
        assertTrue(ingredient.matches(itemStack, mockItemRegistry, mockPlugin));
    }


    @Test
    void matches_customItem_correctIdAndQuantity_returnsTrue() {
        CustomRecipeIngredient ingredient = new CustomRecipeIngredient(
            CustomRecipeIngredient.IngredientType.CUSTOM_ITEM, "SUPER_PICKAXE", 1);
        ItemStack itemStack = new ItemStack(Material.DIAMOND_PICKAXE, 1); // Material can be anything for custom item

        // Mock CustomItem.getItemId to return the ID
        // This requires NBTUtil and its interaction with ItemMeta, which is complex to mock here.
        // Instead, we assume CustomItem.getItemId works and mock its output if NBTUtil was directly used.
        // For this test, we'll rely on the fact that CustomItem.getItemId is static and hope it's testable,
        // or mock the NBTUtil if it were injectable.
        // The CustomRecipeIngredient directly calls CustomItem.getItemId(itemStack, plugin)
        // So, we need to mock the static method or ensure the itemStack has the NBT tag.

        // Let's assume NBTUtil works and an item with this ID would have the tag.
        // To test matches(), we need to simulate an itemStack that CustomItem.getItemId() would identify.
        // This is an integration point. For a pure unit test of matches(), we'd mock CustomItem.getItemId.
        // Since CustomItem.getItemId is static, we can't easily mock it without PowerMock.
        // Alternative: Prepare an ItemStack with the NBT tag.

        // For simplicity in this unit test, let's assume CustomItem.getItemId is robust
        // and we can simulate its effect by preparing an item that would return the ID.
        // This means we'd need to use NBTUtil to set the tag on our mock itemStack.
        // This makes the test more of an integration test for the NBT part.

        // Let's simplify: if type is CUSTOM_ITEM, we assume itemRegistry is used,
        // but CustomRecipeIngredient calls CustomItem.getItemId.
        // So, we test the logic flow.

        // We'll mock the static call to CustomItem.getItemId
        try (MockedStatic<CustomItem> mockedCustomItem = mockStatic(CustomItem.class)) {
            mockedCustomItem.when(() -> CustomItem.getItemId(itemStack, mockPlugin)).thenReturn("SUPER_PICKAXE");
            assertTrue(ingredient.matches(itemStack, mockItemRegistry, mockPlugin));
        }
    }

    @Test
    void matches_customItem_incorrectId_returnsFalse() {
        CustomRecipeIngredient ingredient = new CustomRecipeIngredient(
            CustomRecipeIngredient.IngredientType.CUSTOM_ITEM, "SUPER_PICKAXE", 1);
        ItemStack itemStack = new ItemStack(Material.DIAMOND_PICKAXE, 1);
        try (MockedStatic<CustomItem> mockedCustomItem = mockStatic(CustomItem.class)) {
            mockedCustomItem.when(() -> CustomItem.getItemId(itemStack, mockPlugin)).thenReturn("REGULAR_PICKAXE");
            assertFalse(ingredient.matches(itemStack, mockItemRegistry, mockPlugin));
        }
    }

    @Test
    void matches_customItem_isVanillaItem_returnsFalse() {
        CustomRecipeIngredient ingredient = new CustomRecipeIngredient(
            CustomRecipeIngredient.IngredientType.CUSTOM_ITEM, "SUPER_PICKAXE", 1);
        ItemStack itemStack = new ItemStack(Material.IRON_PICKAXE, 1); // A vanilla item without custom ID NBT
        try (MockedStatic<CustomItem> mockedCustomItem = mockStatic(CustomItem.class)) {
            mockedCustomItem.when(() -> CustomItem.getItemId(itemStack, mockPlugin)).thenReturn(null);
            assertFalse(ingredient.matches(itemStack, mockItemRegistry, mockPlugin));
        }
    }

    @Test
    void matches_nullItemStack_returnsFalse() {
        CustomRecipeIngredient ingredient = new CustomRecipeIngredient(
            CustomRecipeIngredient.IngredientType.VANILLA_MATERIAL, "STONE", 1);
        assertFalse(ingredient.matches(null, mockItemRegistry, mockPlugin));
    }

    @Test
    void matches_airItemStack_returnsFalse() {
        CustomRecipeIngredient ingredient = new CustomRecipeIngredient(
            CustomRecipeIngredient.IngredientType.VANILLA_MATERIAL, "STONE", 1);
        ItemStack airStack = new ItemStack(Material.AIR);
        assertFalse(ingredient.matches(airStack, mockItemRegistry, mockPlugin));
    }
}
