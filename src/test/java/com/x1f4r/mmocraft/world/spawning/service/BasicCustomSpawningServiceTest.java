package com.x1f4r.mmocraft.world.spawning.service;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.combat.service.MobStatProvider;
import com.x1f4r.mmocraft.item.service.CustomItemRegistry;
import com.x1f4r.mmocraft.loot.service.LootService;
import com.x1f4r.mmocraft.util.LoggingUtil;
import com.x1f4r.mmocraft.world.spawning.model.CustomSpawnRule;
import com.x1f4r.mmocraft.world.spawning.model.MobSpawnDefinition;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BasicCustomSpawningServiceTest {

    @Mock private MMOCraftPlugin mockPlugin;
    @Mock private LoggingUtil mockLogger;
    @Mock private MobStatProvider mockMobStatProvider;
    @Mock private LootService mockLootService;
    @Mock private CustomItemRegistry mockCustomItemRegistry;
    @Mock private Server mockServer;
    @Mock private World mockWorld;
    @Mock private Player mockPlayer;

    private BasicCustomSpawningService spawningService;
    private MockedStatic<Bukkit> mockedBukkit;

    @BeforeEach
    void setUp() {
        spawningService = new BasicCustomSpawningService(mockPlugin, mockLogger, mockMobStatProvider, mockLootService, mockCustomItemRegistry); // Corrected lootService to mockLootService

        mockedBukkit = mockStatic(Bukkit.class);
        mockedBukkit.when(Bukkit::getServer).thenReturn(mockServer);
        when(mockServer.getWorlds()).thenReturn(List.of(mockWorld));
        when(mockWorld.getPlayers()).thenReturn(List.of(mockPlayer)); // Simulate one player in one world
        when(mockPlayer.isDead()).thenReturn(false);
        when(mockPlayer.isValid()).thenReturn(true);
        when(mockPlayer.getLocation()).thenReturn(new Location(mockWorld, 0, 64, 0)); // Example location

        // Mock chunk loading for spawn attempts
        when(mockWorld.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        // Mock highest block for spawn attempts
        when(mockWorld.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(63);


    }

    @AfterEach
    void tearDown() {
        mockedBukkit.close();
    }


    @Test
    void registerRule_newRule_addsToList() {
        MobSpawnDefinition definition = new MobSpawnDefinition("test_mob", EntityType.ZOMBIE);
        CustomSpawnRule rule = new CustomSpawnRule("rule1", definition, Collections.emptyList(), 1.0, 0, 255, 5, 32, 100);

        spawningService.registerRule(rule);

        assertEquals(1, spawningService.getAllRules().size());
        assertTrue(spawningService.getAllRules().contains(rule));
        verify(mockLogger).info("Registered custom spawn rule: rule1 for mob type ZOMBIE");
    }

    @Test
    void registerRule_duplicateRuleId_doesNotAddAndLogsWarning() {
        MobSpawnDefinition definition = new MobSpawnDefinition("test_mob", EntityType.ZOMBIE);
        CustomSpawnRule rule1 = new CustomSpawnRule("rule1", definition, Collections.emptyList(), 1.0, 0, 255, 5, 32, 100);
        CustomSpawnRule rule2_same_id = new CustomSpawnRule("rule1", definition, Collections.emptyList(), 0.5, 0, 255, 3, 16, 50);

        spawningService.registerRule(rule1);
        spawningService.registerRule(rule2_same_id); // Attempt to register another with same ID

        assertEquals(1, spawningService.getAllRules().size()); // Should still be 1
        verify(mockLogger).warning("Spawn rule with ID 'rule1' already exists. Skipping registration.");
    }

    @Test
    void unregisterRule_existingRule_removesAndReturnsTrue() {
        MobSpawnDefinition definition = new MobSpawnDefinition("test_mob", EntityType.ZOMBIE);
        CustomSpawnRule rule = new CustomSpawnRule("rule1", definition, Collections.emptyList(), 1.0, 0, 255, 5, 32, 100);
        spawningService.registerRule(rule);

        assertTrue(spawningService.unregisterRule("rule1"));
        assertTrue(spawningService.getAllRules().isEmpty());
        verify(mockLogger).info("Unregistered custom spawn rule: rule1");
    }

    @Test
    void unregisterRule_nonExistentRule_returnsFalse() {
        assertFalse(spawningService.unregisterRule("non_existent_rule"));
    }

    // Test for attemptSpawns is complex due to its reliance on world state and randomness.
    // We'll test the placeholder nature / basic loop for now.
    // A full test would require extensive mocking of world, chunks, locations, getNearbyEntities etc.
    @Test
    void attemptSpawns_placeholderLogic_logsFineMessage() {
        // This test primarily ensures the method runs without NPEs and that the placeholder log is hit.
        // In a real scenario, we would mock rule conditions, spawn chances, etc.

        MobSpawnDefinition definition = new MobSpawnDefinition("zombie_def", EntityType.ZOMBIE);
        CustomSpawnRule rule = mock(CustomSpawnRule.class); // Mock the rule to control its behavior
        when(rule.getRuleId()).thenReturn("test_zombie_rule");
        when(rule.getMobSpawnDefinition()).thenReturn(definition);
        when(rule.isReadyToAttemptSpawn(anyLong())).thenReturn(true);
        when(rule.conditionsMet(any(), any(), any())).thenReturn(true);
        when(rule.getSpawnRadiusCheck()).thenReturn(32.0);
        when(rule.getMaxNearbyEntities()).thenReturn(5);
        when(rule.rollForSpawn()).thenReturn(true); // Force spawn attempt

        // Mock getNearbyEntities to return an empty list (no nearby entities)
        when(mockWorld.getNearbyEntities(any(Location.class), eq(32.0), eq(32.0), eq(32.0), any()))
            .thenReturn(Collections.emptyList());

        // Mock the actual spawning
        LivingEntity mockSpawnedZombie = mock(LivingEntity.class);
        when(mockWorld.spawnEntity(any(Location.class), eq(EntityType.ZOMBIE))).thenReturn(mockSpawnedZombie);
        // Mock attributes for the spawned mob
        lenient().when(mockSpawnedZombie.getAttribute(any(org.bukkit.attribute.Attribute.class))).thenReturn(mock(org.bukkit.attribute.AttributeInstance.class));


        spawningService.registerRule(rule);
        spawningService.attemptSpawns(); // Call the main tick method

        verify(mockLogger, atLeastOnce()).finest("Attempting custom spawns at tick: " + mockWorld.getFullTime());
        // Verify that spawnMob was attempted due to mocked conditions and roll
        verify(mockLogger).fine(contains("Successfully spawned zombie_def via rule test_zombie_rule"));
        verify(rule).setLastSpawnAttemptTickGlobal(mockWorld.getFullTime());
    }

    @Test
    void shutdown_logsMessage() {
        spawningService.shutdown();
        verify(mockLogger).info(contains("BasicCustomSpawningService shutting down"));
    }
}
