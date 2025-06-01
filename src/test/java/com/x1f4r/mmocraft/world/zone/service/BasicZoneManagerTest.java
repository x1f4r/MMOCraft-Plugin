package com.x1f4r.mmocraft.world.zone.service;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.util.LoggingUtil;
import com.x1f4r.mmocraft.world.zone.model.Zone;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BasicZoneManagerTest {

    @Mock
    private MMOCraftPlugin mockPlugin;
    @Mock
    private LoggingUtil mockLogger;
    @Mock
    private World mockWorld1;
    @Mock
    private World mockWorld2;

    private BasicZoneManager zoneManager;

    private Zone zone1_w1, zone2_w1, zone3_w2, overlappingZone_w1;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockPlugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("MMOCraftPluginTest"));
        when(mockLogger.isDebugEnabled()).thenReturn(false); // Default, can override per test

        zoneManager = new BasicZoneManager(mockPlugin, mockLogger);

        when(mockWorld1.getName()).thenReturn("world1");
        when(mockWorld2.getName()).thenReturn("world2");

        // world1 zones
        zone1_w1 = new Zone("zone1_w1", "Zone 1 W1", "world1", 0, 0, 0, 10, 10, 10);
        zone2_w1 = new Zone("zone2_w1", "Zone 2 W1", "world1", 20, 20, 20, 30, 30, 30);
        overlappingZone_w1 = new Zone("overlap_w1", "Overlap W1", "world1", 5, 5, 5, 15, 15, 15); // Overlaps with zone1_w1

        // world2 zone
        zone3_w2 = new Zone("zone3_w2", "Zone 3 W2", "world2", 0, 0, 0, 10, 10, 10);

        zoneManager.registerZone(zone1_w1);
        zoneManager.registerZone(zone2_w1);
        zoneManager.registerZone(overlappingZone_w1);
        zoneManager.registerZone(zone3_w2);
    }

    @Test
    void registerZone_shouldStoreZoneCorrectly() {
        Optional<Zone> retrievedZone1 = zoneManager.getZoneById("zone1_w1");
        assertTrue(retrievedZone1.isPresent());
        assertEquals(zone1_w1, retrievedZone1.get());

        Optional<Zone> retrievedZone3 = zoneManager.getZoneById("zone3_w2");
        assertTrue(retrievedZone3.isPresent());
        assertEquals(zone3_w2, retrievedZone3.get());

        assertEquals(4, zoneManager.getAllZones().size());
    }

    @Test
    void unregisterZone_shouldRemoveZone() {
        zoneManager.unregisterZone("zone1_w1");
        assertFalse(zoneManager.getZoneById("zone1_w1").isPresent());
        assertEquals(3, zoneManager.getAllZones().size());

        // Try unregistering non-existent zone
        zoneManager.unregisterZone("non_existent_zone");
        assertEquals(3, zoneManager.getAllZones().size());
    }

    @Test
    void getZoneById_shouldReturnCorrectZone() {
        assertTrue(zoneManager.getZoneById("zone2_w1").isPresent());
        assertEquals(zone2_w1, zoneManager.getZoneById("zone2_w1").get());
        assertFalse(zoneManager.getZoneById("unknown_id").isPresent());
    }

    @Test
    void getZonesAt_shouldReturnAllMatchingZonesInCorrectWorld() {
        Location loc_zone1_center = new Location(mockWorld1, 5, 5, 5);
        List<Zone> zonesAtLoc1 = zoneManager.getZonesAt(loc_zone1_center);
        assertEquals(2, zonesAtLoc1.size()); // zone1_w1 and overlappingZone_w1
        assertTrue(zonesAtLoc1.contains(zone1_w1));
        assertTrue(zonesAtLoc1.contains(overlappingZone_w1));

        Location loc_zone1_edge = new Location(mockWorld1, 10, 10, 10);
        List<Zone> zonesAtLoc1Edge = zoneManager.getZonesAt(loc_zone1_edge);
        assertEquals(2, zonesAtLoc1Edge.size()); // zone1_w1 and overlappingZone_w1
        assertTrue(zonesAtLoc1Edge.contains(zone1_w1));
        assertTrue(zonesAtLoc1Edge.contains(overlappingZone_w1));


        Location loc_zone2_center = new Location(mockWorld1, 25, 25, 25);
        List<Zone> zonesAtLoc2 = zoneManager.getZonesAt(loc_zone2_center);
        assertEquals(1, zonesAtLoc2.size());
        assertTrue(zonesAtLoc2.contains(zone2_w1));

        Location loc_outside_w1 = new Location(mockWorld1, 50, 50, 50);
        assertTrue(zoneManager.getZonesAt(loc_outside_w1).isEmpty());

        Location loc_zone3_center_w2 = new Location(mockWorld2, 5, 5, 5);
        List<Zone> zonesAtLoc3_w2 = zoneManager.getZonesAt(loc_zone3_center_w2);
        assertEquals(1, zonesAtLoc3_w2.size());
        assertTrue(zonesAtLoc3_w2.contains(zone3_w2));

        Location loc_zone1_center_wrong_world = new Location(mockWorld2, 5, 5, 5); // Same coords as loc_zone1_center but in world2
        // This should actually return zone3_w2 as its coordinates match
        List<Zone> zonesAtLoc1_wrong_world = zoneManager.getZonesAt(loc_zone1_center_wrong_world);
        assertEquals(1, zonesAtLoc1_wrong_world.size());
        assertTrue(zonesAtLoc1_wrong_world.contains(zone3_w2));
        assertFalse(zonesAtLoc1_wrong_world.contains(zone1_w1));
    }

    @Test
    void getZonesAt_shouldReturnEmptyListForUnknownWorld() {
        World mockUnknownWorld = mock(World.class);
        when(mockUnknownWorld.getName()).thenReturn("unknown_world");
        Location loc_unknown_world = new Location(mockUnknownWorld, 5, 5, 5);
        assertTrue(zoneManager.getZonesAt(loc_unknown_world).isEmpty());
    }


    @Test
    void playerZoneCache_shouldWorkCorrectly() {
        UUID player1UUID = UUID.randomUUID();
        UUID player2UUID = UUID.randomUUID();

        Set<Zone> p1InitialZones = Set.of(zone1_w1, overlappingZone_w1);
        Set<Zone> p1UpdatedZones = Set.of(zone2_w1);

        // Initial state
        assertTrue(zoneManager.getPlayerCurrentZones(player1UUID).isEmpty());

        // Update cache for player 1
        zoneManager.updatePlayerZoneCache(player1UUID, p1InitialZones);
        assertEquals(p1InitialZones, zoneManager.getPlayerCurrentZones(player1UUID));

        // Update cache for player 1 again
        zoneManager.updatePlayerZoneCache(player1UUID, p1UpdatedZones);
        assertEquals(p1UpdatedZones, zoneManager.getPlayerCurrentZones(player1UUID));

        // Player 2 should still be empty
        assertTrue(zoneManager.getPlayerCurrentZones(player2UUID).isEmpty());

        // Clear cache for player 1
        zoneManager.clearPlayerZoneCache(player1UUID);
        assertTrue(zoneManager.getPlayerCurrentZones(player1UUID).isEmpty());

        // Player 2 should remain unaffected
        assertTrue(zoneManager.getPlayerCurrentZones(player2UUID).isEmpty());
    }

    @Test
    void getAllZones_shouldReturnAllRegisteredZones() {
        Collection<Zone> allZones = zoneManager.getAllZones();
        assertEquals(4, allZones.size());
        assertTrue(allZones.containsAll(List.of(zone1_w1, zone2_w1, zone3_w2, overlappingZone_w1)));
    }

    @Test
    void registerZone_withNullZone_shouldNotThrowAndLog() {
        // Logging is hard to test directly without deeper framework/mocking logger behavior
        // This test mainly ensures it doesn't break the manager
        long currentSize = zoneManager.getAllZones().size();
        assertDoesNotThrow(() -> zoneManager.registerZone(null));
        assertEquals(currentSize, zoneManager.getAllZones().size());
        // Expect a log message (verify manually or with more advanced logger mocking)
    }

    @Test
    void unregisterZone_updatesZonesByWorldMap() {
        Location loc_zone1_center = new Location(mockWorld1, 5, 5, 5);
        assertTrue(zoneManager.getZonesAt(loc_zone1_center).contains(zone1_w1));

        zoneManager.unregisterZone("zone1_w1");
        assertFalse(zoneManager.getZonesAt(loc_zone1_center).contains(zone1_w1));
        assertTrue(zoneManager.getZonesAt(loc_zone1_center).contains(overlappingZone_w1)); // Overlap should still be there
    }
}
