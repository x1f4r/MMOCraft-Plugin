package com.x1f4r.mmocraft.item.service;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.item.model.CustomItem;
import com.x1f4r.mmocraft.util.LoggingUtil;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey; // For NBTUtil interaction verification
import org.bukkit.persistence.PersistentDataContainer; // Added
import org.bukkit.persistence.PersistentDataType; // For NBTUtil interaction verification
import com.x1f4r.mmocraft.util.StringUtil; // Added

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomItemRegistryTest {

    @Mock private MMOCraftPlugin mockPlugin;
    @Mock private LoggingUtil mockLogger;
    @Mock private ItemMeta mockItemMeta; // For mocking item NBT checks

    private BasicCustomItemRegistry itemRegistry;

    private static class TestItemA extends CustomItem {
        public TestItemA(MMOCraftPlugin plugin) { super(plugin); }
        @Override public String getItemId() { return "item_a"; }
        @Override public Material getMaterial() { return Material.DIAMOND; }
        @Override public String getDisplayName() { return "Item A"; }
        @Override public List<String> getLore() { return List.of("Lore A"); }
    }

    private static class TestItemB extends CustomItem {
        public TestItemB(MMOCraftPlugin plugin) { super(plugin); }
        @Override public String getItemId() { return "item_b"; }
        @Override public Material getMaterial() { return Material.GOLD_INGOT; }
        @Override public String getDisplayName() { return "Item B"; }
        @Override public List<String> getLore() { return List.of("Lore B"); }
    }

    @BeforeEach
    void setUp() {
        itemRegistry = new BasicCustomItemRegistry(mockPlugin, mockLogger);
        // Mock plugin name for NamespacedKey consistency with NBTUtil
        lenient().when(mockPlugin.getName()).thenReturn("MMOCraftTestPlugin");
    }

    @Test
    void registerItem_newItem_registersSuccessfully() {
        CustomItem itemA = new TestItemA(mockPlugin);
        itemRegistry.registerItem(itemA);

        Optional<CustomItem> retrieved = itemRegistry.getCustomItem("item_a");
        assertTrue(retrieved.isPresent());
        assertEquals(itemA, retrieved.get());
        verify(mockLogger).info("Registered custom item: Item A (ID: item_a)");
    }

    @Test
    void registerItem_duplicateId_overwritesAndLogsWarning() {
        CustomItem itemA1 = new TestItemA(mockPlugin);
        CustomItem itemA2 = new TestItemA(mockPlugin) { // Anonymous class to ensure it's a different instance with same ID
            @Override public String getDisplayName() { return "Item A Overwritten"; }
        };
        itemRegistry.registerItem(itemA1);
        itemRegistry.registerItem(itemA2);

        Optional<CustomItem> retrieved = itemRegistry.getCustomItem("item_a");
        assertTrue(retrieved.isPresent());
        assertEquals("Item A Overwritten", retrieved.get().getDisplayName());
        verify(mockLogger).warning(contains("CustomItem ID 'item_a' was already registered. Overwriting"));
    }

    @Test
    void registerItem_nullItem_logsWarning() {
        itemRegistry.registerItem(null);
        verify(mockLogger).warning("Attempted to register a null item or an item with an invalid ID.");
    }

    @Test
    void getCustomItem_byExistingId_returnsItem() {
        CustomItem itemA = new TestItemA(mockPlugin);
        itemRegistry.registerItem(itemA);
        Optional<CustomItem> found = itemRegistry.getCustomItem("item_a");
        assertTrue(found.isPresent());
        assertSame(itemA, found.get());
    }

    @Test
    void getCustomItem_byIdIsCaseInsensitive() {
        CustomItem itemA = new TestItemA(mockPlugin);
        itemRegistry.registerItem(itemA);
        Optional<CustomItem> found = itemRegistry.getCustomItem("ITEM_A");
        assertTrue(found.isPresent());
        assertSame(itemA, found.get());
    }


    @Test
    void getCustomItem_byNonExistentId_returnsEmpty() {
        Optional<CustomItem> found = itemRegistry.getCustomItem("non_existent_id");
        assertFalse(found.isPresent());
    }

    @Test
    void getCustomItem_byItemStackWithId_returnsItem() {
        CustomItem itemB = new TestItemB(mockPlugin);
        itemRegistry.registerItem(itemB);

        ItemStack itemStackWithNbt = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = mock(ItemMeta.class); // Use a fresh mock for this specific test
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(itemStackWithNbt.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenReturn("item_b");
        // Simulate hasItemMeta correctly
        when(itemStackWithNbt.hasItemMeta()).thenReturn(true);


        Optional<CustomItem> found = itemRegistry.getCustomItem(itemStackWithNbt);
        assertTrue(found.isPresent());
        assertSame(itemB, found.get());
    }

    @Test
    void getCustomItem_byItemStackWithoutId_returnsEmpty() {
        ItemStack plainStack = new ItemStack(Material.IRON_INGOT);
        // Simulate no NBT tag by having NBTUtil.getString return null
        // (CustomItem.getItemId will return null)

        // If item has no meta at all
        when(plainStack.hasItemMeta()).thenReturn(false);
        Optional<CustomItem> foundNoMeta = itemRegistry.getCustomItem(plainStack);
        assertFalse(foundNoMeta.isPresent());

        // If item has meta but not the tag
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(plainStack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenReturn(null);
        when(plainStack.hasItemMeta()).thenReturn(true);

        Optional<CustomItem> foundNoTag = itemRegistry.getCustomItem(plainStack);
        assertFalse(foundNoTag.isPresent());
    }

    @Test
    void createItemStack_validId_returnsCorrectItemStack() {
        CustomItem itemA = new TestItemA(mockPlugin);
        itemRegistry.registerItem(itemA);

        ItemStack created = itemRegistry.createItemStack("item_a", 2);
        assertNotNull(created);
        assertEquals(Material.DIAMOND, created.getType());
        assertEquals(2, created.getAmount());
        assertTrue(created.hasItemMeta());
        assertEquals(StringUtil.colorize(itemA.getDisplayName()), created.getItemMeta().getDisplayName());
        // Verify NBT tag is present
        assertEquals("item_a", CustomItem.getItemId(created, mockPlugin));
    }

    @Test
    void createItemStack_invalidId_returnsAirOrDefault() {
        ItemStack created = itemRegistry.createItemStack("non_existent_id", 1);
        assertNotNull(created);
        assertEquals(Material.AIR, created.getType()); // Assuming it defaults to AIR
        verify(mockLogger).warning("Attempted to create ItemStack for unknown custom item ID: non_existent_id");
    }

    @Test
    void getAllItems_returnsAllRegisteredItems() {
        CustomItem itemA = new TestItemA(mockPlugin);
        CustomItem itemB = new TestItemB(mockPlugin);
        itemRegistry.registerItem(itemA);
        itemRegistry.registerItem(itemB);

        Collection<CustomItem> all = itemRegistry.getAllItems();
        assertEquals(2, all.size());
        assertTrue(all.contains(itemA));
        assertTrue(all.contains(itemB));
    }

    @Test
    void getAllItems_collectionIsUnmodifiable() {
        CustomItem itemA = new TestItemA(mockPlugin);
        itemRegistry.registerItem(itemA);
        Collection<CustomItem> allItems = itemRegistry.getAllItems();
        assertThrows(UnsupportedOperationException.class, () -> allItems.add(new TestItemB(mockPlugin)));
    }

    @Test
    void unregisterItem_existingItem_removesAndReturnsTrue() {
        CustomItem itemA = new TestItemA(mockPlugin);
        itemRegistry.registerItem(itemA);
        assertTrue(itemRegistry.getCustomItem("item_a").isPresent());

        boolean result = itemRegistry.unregisterItem("item_a");
        assertTrue(result);
        assertFalse(itemRegistry.getCustomItem("item_a").isPresent());
        verify(mockLogger).info("Unregistered custom item: Item A (ID: item_a)");
    }

    @Test
    void unregisterItem_nonExistingItem_returnsFalse() {
        boolean result = itemRegistry.unregisterItem("item_c");
        assertFalse(result);
    }
}
