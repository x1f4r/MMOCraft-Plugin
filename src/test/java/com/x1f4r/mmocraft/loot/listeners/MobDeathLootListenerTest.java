package com.x1f4r.mmocraft.loot.listeners;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.item.service.CustomItemRegistry;
import com.x1f4r.mmocraft.loot.model.LootTable;
import com.x1f4r.mmocraft.loot.service.LootService;
import com.x1f4r.mmocraft.util.LoggingUtil;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections; // Added import
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MobDeathLootListenerTest {

    @Mock private LootService mockLootService;
    @Mock private CustomItemRegistry mockItemRegistry;
    @Mock private MMOCraftPlugin mockPlugin;
    @Mock private LoggingUtil mockLogger;

    @Mock private EntityDeathEvent mockEvent;
    @Mock private LivingEntity mockDeadEntity;
    @Mock private Player mockKiller;
    @Mock private LootTable mockLootTable;
    @Mock private Location mockLocation;

    private MobDeathLootListener listener;

    @BeforeEach
    void setUp() {
        listener = new MobDeathLootListener(mockLootService, mockItemRegistry, mockPlugin, mockLogger);

        when(mockEvent.getEntity()).thenReturn(mockDeadEntity);
        when(mockDeadEntity.getKiller()).thenReturn(mockKiller); // Assume killed by player by default
        when(mockDeadEntity.getType()).thenReturn(EntityType.ZOMBIE);
        when(mockDeadEntity.getLocation()).thenReturn(mockLocation);

        // Mock event.getDrops() to return a mutable list
        when(mockEvent.getDrops()).thenReturn(new ArrayList<>(List.of(new ItemStack(Material.ROTTEN_FLESH))));
    }

    @Test
    void onMobDeath_noPlayerKiller_doesNotProcessLoot() {
        when(mockDeadEntity.getKiller()).thenReturn(null);
        listener.onMobDeath(mockEvent);
        verify(mockLootService, never()).getLootTable(any(EntityType.class));
        verify(mockLogger).finer(contains("died without a player killer"));
    }

    @Test
    void onMobDeath_noLootTableForMob_doesNotProcessLoot() {
        when(mockLootService.getLootTable(EntityType.ZOMBIE)).thenReturn(Optional.empty());
        listener.onMobDeath(mockEvent);
        verify(mockLootTable, never()).generateLoot(any(), any());
        verify(mockLogger).finer(contains("No custom loot table registered for mob type: ZOMBIE"));
        assertFalse(mockEvent.getDrops().isEmpty(), "Vanilla drops should not be cleared if no custom table.");
    }

    @Test
    void onMobDeath_lootTableGeneratesItems_clearsVanillaDropsAndSpawnsCustom() {
        when(mockLootService.getLootTable(EntityType.ZOMBIE)).thenReturn(Optional.of(mockLootTable));

        ItemStack customDrop1 = new ItemStack(Material.DIAMOND);
        List<ItemStack> customDrops = List.of(customDrop1);
        when(mockLootTable.generateLoot(mockItemRegistry, mockPlugin)).thenReturn(customDrops);

        listener.onMobDeath(mockEvent);

        verify(mockLootTable).generateLoot(mockItemRegistry, mockPlugin);
        assertTrue(mockEvent.getDrops().isEmpty(), "Vanilla drops should be cleared.");
        verify(mockLootService).spawnLoot(mockLocation, customDrops);
        verify(mockLogger, times(1)).info(contains("Custom drop for " + mockKiller.getName()));
    }

    @Test
    void onMobDeath_lootTableGeneratesNoItems_doesNotClearVanillaDropsOrSpawn() {
        when(mockLootService.getLootTable(EntityType.ZOMBIE)).thenReturn(Optional.of(mockLootTable));
        when(mockLootTable.generateLoot(mockItemRegistry, mockPlugin)).thenReturn(Collections.emptyList());

        List<ItemStack> originalDrops = new ArrayList<>(mockEvent.getDrops()); // Copy before event
        listener.onMobDeath(mockEvent);

        verify(mockLootTable).generateLoot(mockItemRegistry, mockPlugin);
        assertEquals(originalDrops, mockEvent.getDrops(), "Vanilla drops should NOT be cleared if custom loot is empty.");
        verify(mockLootService, never()).spawnLoot(any(Location.class), anyList());
        verify(mockLogger).fine(contains("generated no items this time"));
    }
}
