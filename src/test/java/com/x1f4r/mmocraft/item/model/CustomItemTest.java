package com.x1f4r.mmocraft.item.model;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.item.api.NBTUtil; // For static getItemId verification
import com.x1f4r.mmocraft.util.StringUtil; // Assuming StringUtil is used and available

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.x1f4r.mmocraft.playerdata.model.Stat; // Added for stat modifiers test
import java.util.Arrays;
import java.util.EnumMap; // Added
import java.util.List;
import java.util.Map; // Added

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomItemTest {

    @Mock private MMOCraftPlugin mockPlugin;
    @Mock private ItemMeta mockItemMeta;
    @Mock private PersistentDataContainer mockPdc;

    private TestCustomItem testCustomItem;
    private TestCustomItemWithStats testCustomItemWithStats;
    private TestCustomItemWithRarity testCustomItemWithRarity; // Added for rarity test
    private NamespacedKey nbtKey;

    // Concrete implementation for testing
    private static class TestCustomItem extends CustomItem {
        public TestCustomItem(MMOCraftPlugin plugin) { super(plugin); }
        @Override public String getItemId() { return "test_sword"; }
        @Override public Material getMaterial() { return Material.DIAMOND_SWORD; }
        @Override public String getDisplayName() { return "Legendary Sword"; } // Uncolored base name
        @Override public List<String> getLore() { return Arrays.asList("&7A sword of legends.", "&eBase Lore Line 2"); }
        @Override public int getCustomModelData() { return 12345; }
        @Override public boolean isUnbreakable() { return true; }
        @Override public boolean hasEnchantGlint() { return false; }
        @Override public ItemRarity getRarity() { return ItemRarity.LEGENDARY; } // Specific rarity
    }

    // Another test item with stat modifiers
    private static class TestCustomItemWithStats extends CustomItem {
        public TestCustomItemWithStats(MMOCraftPlugin plugin) { super(plugin); }
        @Override public String getItemId() { return "stat_helmet"; }
        @Override public Material getMaterial() { return Material.IRON_HELMET; }
        @Override public String getDisplayName() { return "Helmet of Vigor"; } // Uncolored
        @Override public List<String> getLore() { return List.of("&7Grants wearer increased vitality."); }
        @Override public Map<Stat, Double> getStatModifiers() {
            Map<Stat, Double> mods = new EnumMap<>(Stat.class);
            mods.put(Stat.VITALITY, 10.0);
            mods.put(Stat.DEFENSE, -2.5);
            return mods;
        }
        @Override public ItemRarity getRarity() { return ItemRarity.RARE; } // Specific rarity
    }

    // Test item with default common rarity
    private static class TestCommonItem extends CustomItem {
        public TestCommonItem(MMOCraftPlugin plugin) { super(plugin); }
        @Override public String getItemId() { return "common_item"; }
        @Override public Material getMaterial() { return Material.STICK; }
        @Override public String getDisplayName() { return "Common Stick"; } // Uncolored
        @Override public List<String> getLore() { return List.of("&7Just a stick."); }
        // Implicitly uses getRarity() { return ItemRarity.COMMON; }
    }

    // Added stub class for TestCustomItemWithRarity
    private static class TestCustomItemWithRarity extends CustomItem {
        private final ItemRarity rarityToTest;
        public TestCustomItemWithRarity(MMOCraftPlugin plugin, ItemRarity rarity) {
            super(plugin);
            this.rarityToTest = rarity;
        }
        @Override public String getItemId() { return "rarity_test_item_" + rarityToTest.name().toLowerCase(); }
        @Override public Material getMaterial() { return Material.AMETHYST_SHARD; }
        @Override public String getDisplayName() { return rarityToTest.name() + " Test Item"; }
        @Override public List<String> getLore() { return List.of("This item is " + rarityToTest.getDisplayName()); }
        @Override public ItemRarity getRarity() { return rarityToTest; }
    }


    @BeforeEach
    void setUp() {
        testCustomItem = new TestCustomItem(mockPlugin);
        testCustomItemWithStats = new TestCustomItemWithStats(mockPlugin);
        testCustomItemWithRarity = new TestCustomItemWithRarity(mockPlugin, ItemRarity.EPIC); // Changed to use the new stub
        lenient().when(mockPlugin.getName()).thenReturn("MMOCraftTestPlugin");
        nbtKey = new NamespacedKey(mockPlugin, CustomItem.CUSTOM_ITEM_ID_NBT_KEY);
    }

    @Test
    void createItemStack_appliesAllPropertiesCorrectly() {
        // We need to mock the ItemStack and its ItemMeta creation path
        ItemStack mockItemStack = spy(new ItemStack(Material.DIAMOND_SWORD, 1)); // Spy on real ItemStack
        // ItemMeta mockItemMeta = mock(ItemMeta.class); // Already mocked by @Mock
        // PersistentDataContainer mockPdc = mock(PersistentDataContainer.class); // Already mocked by @Mock

        when(mockItemStack.getItemMeta()).thenReturn(mockItemMeta);
        when(mockItemMeta.getPersistentDataContainer()).thenReturn(mockPdc);
        // when(mockPlugin.getName()).thenReturn("MMOCraft"); // Ensure NamespacedKey gets a plugin name

        // Actual call - need to ensure NBTUtil static methods are either not called, or mocked if they do more than PDC
        // For this test, we'll verify the PDC interactions directly as NBTUtil is a wrapper.
        // The NBTUtil.setString method will be called by customItem.createItemStack(),
        // so we need to ensure that when it calls itemStack.getItemMeta(), it gets our mockItemMeta.

        // To test createItemStack fully, we need to control what happens *inside* NBTUtil.setString
        // or make NBTUtil mockable (which is hard for static methods without PowerMock).
        // Simpler: verify the state of the ItemStack *after* createItemStack returns.

        // Let's create a real ItemStack and verify its properties
        // This makes it more of an integration test for this method.

        // To test NBT part without PowerMock for NBTUtil's static methods:
        // We can verify the calls made to ItemMeta and PersistentDataContainer directly
        // as NBTUtil just wraps these calls.

        ItemStack createdStack = testCustomItem.createItemStack(1); // This will use real ItemMeta if not mocked globally

        assertNotNull(createdStack);
        assertEquals(Material.DIAMOND_SWORD, createdStack.getType());
        assertEquals(1, createdStack.getAmount());

        assertTrue(createdStack.hasItemMeta());
        ItemMeta meta = createdStack.getItemMeta();
        assertNotNull(meta);

        assertEquals(StringUtil.colorize(ItemRarity.LEGENDARY.getChatColor() + "Legendary Sword"), meta.getDisplayName());
        List<String> expectedLore = Arrays.asList(
            StringUtil.colorize("&7A sword of legends."),
            StringUtil.colorize("&eBase Lore Line 2"),
            StringUtil.colorize(ItemRarity.LEGENDARY.getDisplayName()) // Rarity added to lore
        );
        // The stat modifier part of lore is empty for this item.
        // If getStatModifiers() is empty, no "----------" should be added before rarity unless base lore is also empty.
        // Current logic adds rarity after stats (or base lore if no stats).
        // If base lore and stats are empty, only rarity is added.
        // If base lore exists, and stats are empty, rarity is added.
        // If base lore exists, and stats exist, separator + stats + rarity.
        // In this case: Base lore exists, stats are empty. Rarity is added.

        // Verify all expected lines are present, order might vary slightly if logic changes.
        // For TestCustomItem (Legendary, no stats):
        // line 1: &7A sword of legends.
        // line 2: &eBase Lore Line 2
        // line 3: &6Legendary
        assertEquals(3, meta.getLore().size());
        assertTrue(meta.getLore().contains(StringUtil.colorize("&7A sword of legends.")));
        assertTrue(meta.getLore().contains(StringUtil.colorize("&eBase Lore Line 2")));
        assertTrue(meta.getLore().contains(StringUtil.colorize(ItemRarity.LEGENDARY.getDisplayName())));

        assertEquals(12345, meta.getCustomModelData());
        assertTrue(meta.isUnbreakable());

        // Verify NBT tag
        PersistentDataContainer container = meta.getPersistentDataContainer();
        assertTrue(container.has(nbtKey, PersistentDataType.STRING));
        assertEquals("test_sword", container.get(nbtKey, PersistentDataType.STRING));
    }

    @Test
    void createItemStack_withStatModifiers_includesStatsInLore() {
        ItemStack createdStack = testCustomItemWithStats.createItemStack(1);
        assertNotNull(createdStack);
        assertEquals(Material.IRON_HELMET, createdStack.getType());

        ItemMeta meta = createdStack.getItemMeta();
        assertNotNull(meta);
        assertEquals(StringUtil.colorize(ItemRarity.RARE.getChatColor() + "Helmet of Vigor"), meta.getDisplayName());

        List<String> lore = meta.getLore();
        assertNotNull(lore);
        // Expected: Base lore, separator, stats, rarity
        // "&7Grants wearer increased vitality."
        // "&7----------"
        // "&a+10.0 Vitality"
        // "&c-2.5 Defense"
        // "&9Rare" (Rarity of TestCustomItemWithStats is RARE)
        assertTrue(lore.contains(StringUtil.colorize("&7Grants wearer increased vitality.")));
        assertTrue(lore.contains(StringUtil.colorize("&7----------")));
        assertTrue(lore.contains(StringUtil.colorize("&a+10.0 Vitality")));
        assertTrue(lore.contains(StringUtil.colorize("&c-2.5 Defense")));
        assertTrue(lore.contains(StringUtil.colorize(ItemRarity.RARE.getDisplayName())));
        assertEquals(5, lore.size()); // 1 base + 1 separator + 2 stats + 1 rarity

        // Verify NBT tag
        PersistentDataContainer container = meta.getPersistentDataContainer();
        assertTrue(container.has(new NamespacedKey(mockPlugin, CustomItem.CUSTOM_ITEM_ID_NBT_KEY), PersistentDataType.STRING));
        assertEquals("stat_helmet", container.get(new NamespacedKey(mockPlugin, CustomItem.CUSTOM_ITEM_ID_NBT_KEY), PersistentDataType.STRING));
    }

    @Test
    void createItemStack_amountZeroOrLess_defaultsToOne() {
        ItemStack stackZero = testCustomItem.createItemStack(0);
        assertEquals(1, stackZero.getAmount());

        ItemStack stackNegative = testCustomItem.createItemStack(-5);
        assertEquals(1, stackNegative.getAmount());
    }


    @Test
    void staticGetItemId_itemHasId_returnsId() {
         // This test requires mocking NBTUtil.getString or having a real item with NBT.
         // For a unit test of CustomItem, we assume NBTUtil works as tested separately.
         // We'll verify the parameters passed to NBTUtil.getString.

        // Create a dummy item stack and manually set the NBT for testing this static method
        ItemStack itemWithNbt = new ItemStack(Material.IRON_SWORD);
        ItemMeta currentMeta = itemWithNbt.getItemMeta();
        PersistentDataContainer pdc = currentMeta.getPersistentDataContainer();
        pdc.set(new NamespacedKey(mockPlugin, CustomItem.CUSTOM_ITEM_ID_NBT_KEY), PersistentDataType.STRING, "retrieved_id");
        itemWithNbt.setItemMeta(currentMeta);

        assertEquals("retrieved_id", CustomItem.getItemId(itemWithNbt, mockPlugin));
    }

    @Test
    void staticGetItemId_itemHasNoId_returnsNull() {
        ItemStack plainItem = new ItemStack(Material.STONE);
        assertNull(CustomItem.getItemId(plainItem, mockPlugin));
    }

    @Test
    void staticGetItemId_nullItem_returnsNull() {
        assertNull(CustomItem.getItemId(null, mockPlugin));
    }

    @Test
    void staticGetItemId_airItem_returnsNull() {
        ItemStack airItem = new ItemStack(Material.AIR);
        assertNull(CustomItem.getItemId(airItem, mockPlugin));
    }

    @Test
    void staticGetItemId_itemNoMeta_returnsNull() {
        ItemStack itemWithoutMeta = mock(ItemStack.class); // An item that might not have meta
        when(itemWithoutMeta.getType()).thenReturn(Material.STONE); // Make it not AIR
        when(itemWithoutMeta.hasItemMeta()).thenReturn(false);
        assertNull(CustomItem.getItemId(itemWithoutMeta, mockPlugin));
    }
}
