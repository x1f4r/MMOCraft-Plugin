package com.x1f4r.mmocraft.item.equipment.service;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.item.model.CustomItem;
import com.x1f4r.mmocraft.item.service.CustomItemRegistry;
import com.x1f4r.mmocraft.playerdata.PlayerDataService;
import com.x1f4r.mmocraft.playerdata.model.PlayerProfile;
import com.x1f4r.mmocraft.playerdata.model.Stat;
import com.x1f4r.mmocraft.util.LoggingUtil;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerEquipmentManagerTest {

    @Mock private MMOCraftPlugin mockPlugin;
    @Mock private PlayerDataService mockPlayerDataService;
    @Mock private CustomItemRegistry mockCustomItemRegistry;
    @Mock private LoggingUtil mockLogger;

    @Mock private Player mockPlayer;
    @Mock private PlayerInventory mockPlayerInventory;
    @Mock private PlayerProfile mockPlayerProfile;

    @Captor private ArgumentCaptor<Map<Stat, Double>> statMapCaptor;

    private PlayerEquipmentManager equipmentManager;
    private UUID playerUUID = UUID.randomUUID();

    // Dummy CustomItem for testing
    private static class TestStatItem extends CustomItem {
        private final String id;
        private final Map<Stat, Double> stats;
        public TestStatItem(MMOCraftPlugin plugin, String id, Map<Stat, Double> stats) {
            super(plugin);
            this.id = id;
            this.stats = stats;
        }
        @Override public String getItemId() { return id; }
        @Override public Material getMaterial() { return Material.STONE; } // Material doesn't matter for this test
        @Override public String getDisplayName() { return "Test Stat Item"; }
        @Override public List<String> getLore() { return Collections.emptyList(); }
        @Override public Map<Stat, Double> getStatModifiers() { return stats; }
    }

    @BeforeEach
    void setUp() {
        equipmentManager = new PlayerEquipmentManager(mockPlugin, mockPlayerDataService, mockCustomItemRegistry, mockLogger);

        when(mockPlayer.getUniqueId()).thenReturn(playerUUID);
        when(mockPlayer.getInventory()).thenReturn(mockPlayerInventory);
        when(mockPlayerDataService.getPlayerProfile(playerUUID)).thenReturn(mockPlayerProfile);
    }

    @Test
    void updateEquipmentStats_playerWithNoProfile_shouldReturnEarly() {
        when(mockPlayerDataService.getPlayerProfile(playerUUID)).thenReturn(null);
        equipmentManager.updateEquipmentStats(mockPlayer);
        verify(mockPlayerProfile, never()).clearEquipmentStatModifiers();
        verify(mockLogger).fine(contains("PlayerProfile not found"));
    }

    @Test
    void updateEquipmentStats_noCustomItemsEquipped_clearsModifiersAndRecalculates() {
        when(mockPlayerInventory.getItemInMainHand()).thenReturn(new ItemStack(Material.WOODEN_SWORD)); // Vanilla item
        when(mockPlayerInventory.getHelmet()).thenReturn(null);
        // ... mock other slots as null or vanilla items ...

        when(mockCustomItemRegistry.getCustomItem(any(ItemStack.class))).thenReturn(Optional.empty());

        equipmentManager.updateEquipmentStats(mockPlayer);

        verify(mockPlayerProfile).clearEquipmentStatModifiers();
        verify(mockPlayerProfile, never()).addAllEquipmentStatModifiers(anyMap()); // Should not be called if no custom stats
        verify(mockPlayerProfile).recalculateDerivedAttributes(); // Crucially called once at the end
    }

    @Test
    void updateEquipmentStats_oneCustomItemWithStats_appliesStatsAndRecalculates() {
        ItemStack customSwordStack = new ItemStack(Material.DIAMOND_SWORD);
        Map<Stat, Double> swordStats = new EnumMap<>(Stat.class);
        swordStats.put(Stat.STRENGTH, 10.0);
        swordStats.put(Stat.AGILITY, 5.0);
        CustomItem customSword = new TestStatItem(mockPlugin, "diamond_slayer", swordStats);

        when(mockPlayerInventory.getItemInMainHand()).thenReturn(customSwordStack);
        when(mockCustomItemRegistry.getCustomItem(customSwordStack)).thenReturn(Optional.of(customSword));
        // Other slots are empty/vanilla
        when(mockCustomItemRegistry.getCustomItem(argThat((ItemStack item) -> item != customSwordStack))).thenReturn(Optional.empty()); // Typed lambda


        equipmentManager.updateEquipmentStats(mockPlayer);

        verify(mockPlayerProfile).clearEquipmentStatModifiers();
        verify(mockPlayerProfile).addAllEquipmentStatModifiers(statMapCaptor.capture());
        Map<Stat, Double> capturedStats = statMapCaptor.getValue();
        assertEquals(10.0, capturedStats.get(Stat.STRENGTH));
        assertEquals(5.0, capturedStats.get(Stat.AGILITY));
        assertEquals(2, capturedStats.size());
        verify(mockPlayerProfile).recalculateDerivedAttributes();
    }

    @Test
    void updateEquipmentStats_multipleCustomItems_aggregatesStatsAndRecalculates() {
        ItemStack customHelmetStack = new ItemStack(Material.IRON_HELMET);
        Map<Stat, Double> helmetStats = new EnumMap<>(Stat.class);
        helmetStats.put(Stat.VITALITY, 15.0);
        helmetStats.put(Stat.DEFENSE, 7.0);
        CustomItem customHelmet = new TestStatItem(mockPlugin, "iron_guardian_helm", helmetStats);

        ItemStack customChestStack = new ItemStack(Material.IRON_CHESTPLATE);
        Map<Stat, Double> chestStats = new EnumMap<>(Stat.class);
        chestStats.put(Stat.VITALITY, 20.0); // More Vitality
        chestStats.put(Stat.STRENGTH, 3.0);  // Strength
        CustomItem customChest = new TestStatItem(mockPlugin, "iron_bulwark_plate", chestStats);

        when(mockPlayerInventory.getHelmet()).thenReturn(customHelmetStack);
        when(mockPlayerInventory.getChestplate()).thenReturn(customChestStack);
        when(mockCustomItemRegistry.getCustomItem(customHelmetStack)).thenReturn(Optional.of(customHelmet));
        when(mockCustomItemRegistry.getCustomItem(customChestStack)).thenReturn(Optional.of(customChest));
        // Other slots are empty/vanilla
        when(mockCustomItemRegistry.getCustomItem(argThat((ItemStack item) -> item != customHelmetStack && item != customChestStack))) // Typed lambda
            .thenReturn(Optional.empty());

        equipmentManager.updateEquipmentStats(mockPlayer);

        verify(mockPlayerProfile).clearEquipmentStatModifiers();
        verify(mockPlayerProfile).addAllEquipmentStatModifiers(statMapCaptor.capture());
        Map<Stat, Double> capturedStats = statMapCaptor.getValue();

        assertEquals(15.0 + 20.0, capturedStats.get(Stat.VITALITY)); // 35.0
        assertEquals(7.0, capturedStats.get(Stat.DEFENSE));
        assertEquals(3.0, capturedStats.get(Stat.STRENGTH));
        assertEquals(3, capturedStats.size()); // VITALITY, DEFENSE, STRENGTH
        verify(mockPlayerProfile).recalculateDerivedAttributes();
    }

    @Test
    void updateEquipmentStats_itemWithNoStats_doesNotAddModifiers() {
        ItemStack customGenericStack = new ItemStack(Material.BOOK);
        CustomItem customGenericItem = new TestStatItem(mockPlugin, "generic_book", Collections.emptyMap()); // No stats

        when(mockPlayerInventory.getItemInMainHand()).thenReturn(customGenericStack);
        when(mockCustomItemRegistry.getCustomItem(customGenericStack)).thenReturn(Optional.of(customGenericItem));
        when(mockCustomItemRegistry.getCustomItem(argThat((ItemStack item) -> item != customGenericStack))).thenReturn(Optional.empty()); // Typed lambda

        equipmentManager.updateEquipmentStats(mockPlayer);

        verify(mockPlayerProfile).clearEquipmentStatModifiers();
        // addAllEquipmentStatModifiers might be called with an empty map, or not at all if optimized
        // For this test, let's ensure it's either not called, or called with an empty map.
        // The current implementation of PlayerEquipmentManager will call it with an empty map if no stats are found.
        verify(mockPlayerProfile).addAllEquipmentStatModifiers(statMapCaptor.capture());
        assertTrue(statMapCaptor.getValue().isEmpty(), "No stats should be added if custom item has no modifiers.");
        verify(mockPlayerProfile).recalculateDerivedAttributes();
    }
}
