package com.x1f4r.mmocraft.world.zone.service;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.eventbus.EventBusService; // Added
import com.x1f4r.mmocraft.util.LoggingUtil;
import com.x1f4r.mmocraft.world.zone.model.Zone;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player; // Added
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
    @Mock
    private EventBusService mockEventBusService; // Added mock for EventBusService

    private BasicZoneManager zoneManager;

    private Zone zone1_w1, zone2_w1, zone3_w2, overlappingZone_w1, nonOverlappingZone_w1; // Added nonOverlappingZone_w1 here
    private Location loc_zone1_center, loc_outside_w1, loc_zone3_center_w2, loc_zone1_center_wrong_world; // Declare locations used in tests
    private UUID player1UUID = UUID.randomUUID(); // Declare UUIDs used in tests
    private UUID player2UUID = UUID.randomUUID();
    @Mock private Player mockPlayer1; // Mock player for tests needing Player object
    @Mock private Player mockPlayer2;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockPlugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("MMOCraftPluginTest"));
        // when(mockLogger.isDebugEnabled()).thenReturn(false); // Removed: isDebugEnabled() does not exist on LoggingUtil

        zoneManager = new BasicZoneManager(mockPlugin, mockLogger, mockEventBusService); // Added mockEventBusService

        when(mockWorld1.getName()).thenReturn("world1");
        when(mockWorld2.getName()).thenReturn("world2");

        // world1 zones
        zone1_w1 = new Zone("zone1_w1", "Zone 1 W1", "world1", 0, 0, 0, 10, 10, 10, null); // Added null for properties
        zone2_w1 = new Zone("zone2_w1", "Zone 2 W1", "world1", 20, 20, 20, 30, 30, 30, null); // Added null for properties
        overlappingZone_w1 = new Zone("overlap_w1", "Overlap W1", "world1", 5, 5, 5, 15, 15, 15, null); // Added null for properties
        nonOverlappingZone_w1 = new Zone("non_overlap_w1", "Non Overlap W1", "world1", 100, 100, 100, 110, 110, 110, null); // Added and with null

        // world2 zone
        zone3_w2 = new Zone("zone3_w2", "Zone 3 W2", "world2", 0, 0, 0, 10, 10, 10, null); // Added null for properties

        zoneManager.registerZone(zone1_w1);
        zoneManager.registerZone(zone2_w1);
        zoneManager.registerZone(overlappingZone_w1);
        zoneManager.registerZone(zone3_w2);
    }

    @Test
    void registerZone_shouldStoreZoneCorrectly() {
        Optional<Zone> retrievedZone1 = zoneManager.getZone("zone1_w1"); // Renamed
        assertTrue(retrievedZone1.isPresent());
        assertEquals(zone1_w1, retrievedZone1.get());

        Optional<Zone> retrievedZone3 = zoneManager.getZone("zone3_w2"); // Renamed
        assertTrue(retrievedZone3.isPresent());
        assertEquals(zone3_w2, retrievedZone3.get());

        assertEquals(4, zoneManager.getAllZones().size());
    }

    @Test
    void unregisterZone_shouldRemoveZone() {
        zoneManager.unregisterZone("zone1_w1");
        assertFalse(zoneManager.getZone("zone1_w1").isPresent()); // Renamed
        assertEquals(3, zoneManager.getAllZones().size());

        // Try unregistering non-existent zone
        zoneManager.unregisterZone("non_existent_zone");
        assertEquals(3, zoneManager.getAllZones().size());
    }

    @Test
    void getZoneById_shouldReturnCorrectZone() { // Test name can remain, but uses getZone
        assertTrue(zoneManager.getZone("zone2_w1").isPresent()); // Renamed
        assertEquals(zone2_w1, zoneManager.getZone("zone2_w1").get()); // Renamed
        assertFalse(zoneManager.getZone("unknown_id").isPresent()); // Renamed
    }

    @Test
    void getZonesAt_shouldReturnAllMatchingZonesInCorrectWorld() {
        loc_zone1_center = new Location(mockWorld1, 5, 5, 5); // Initialize if not already
        List<Zone> zonesAtLoc1 = zoneManager.getZones(loc_zone1_center); // Renamed
        assertEquals(2, zonesAtLoc1.size()); // zone1_w1 and overlappingZone_w1
        assertTrue(zonesAtLoc1.contains(zone1_w1));
        assertTrue(zonesAtLoc1.contains(overlappingZone_w1));

        Location loc_zone1_edge = new Location(mockWorld1, 10, 10, 10);
        List<Zone> zonesAtLoc1Edge = zoneManager.getZones(loc_zone1_edge); // Renamed
        assertEquals(2, zonesAtLoc1Edge.size()); // zone1_w1 and overlappingZone_w1
        assertTrue(zonesAtLoc1Edge.contains(zone1_w1));
        assertTrue(zonesAtLoc1Edge.contains(overlappingZone_w1));


        Location loc_zone2_center = new Location(mockWorld1, 25, 25, 25);
        List<Zone> zonesAtLoc2 = zoneManager.getZones(loc_zone2_center); // Renamed
        assertEquals(1, zonesAtLoc2.size());
        assertTrue(zonesAtLoc2.contains(zone2_w1));

        loc_outside_w1 = new Location(mockWorld1, 50, 50, 50); // Initialize
        assertTrue(zoneManager.getZones(loc_outside_w1).isEmpty()); // Renamed

        loc_zone3_center_w2 = new Location(mockWorld2, 5, 5, 5); // Initialize
        List<Zone> zonesAtLoc3_w2 = zoneManager.getZones(loc_zone3_center_w2); // Renamed
        assertEquals(1, zonesAtLoc3_w2.size());
        assertTrue(zonesAtLoc3_w2.contains(zone3_w2));

        loc_zone1_center_wrong_world = new Location(mockWorld2, 5, 5, 5); // Initialize
        // This should actually return zone3_w2 as its coordinates match
        List<Zone> zonesAtLoc1_wrong_world = zoneManager.getZones(loc_zone1_center_wrong_world); // Renamed
        assertEquals(1, zonesAtLoc1_wrong_world.size());
        assertTrue(zonesAtLoc1_wrong_world.contains(zone3_w2));
        assertFalse(zonesAtLoc1_wrong_world.contains(zone1_w1));
    }

    @Test
    void getZonesAt_shouldReturnEmptyListForUnknownWorld() {
        World mockUnknownWorld = mock(World.class);
        when(mockUnknownWorld.getName()).thenReturn("unknown_world");
        Location loc_unknown_world = new Location(mockUnknownWorld, 5, 5, 5);
        assertTrue(zoneManager.getZones(loc_unknown_world).isEmpty()); // Renamed
    }


    @Test
    void playerZoneCache_shouldWorkCorrectly() {
        // Setup mock players with UUIDs
        when(mockPlayer1.getUniqueId()).thenReturn(player1UUID);
        when(mockPlayer2.getUniqueId()).thenReturn(player2UUID);


        Set<String> p1InitialZoneIds = Set.of("zone1_w1", "overlap_w1"); // Changed variable name for clarity
        Set<String> p1UpdatedZoneIds = Set.of("zone2_w1"); // Changed variable name for clarity

        // Initial state
        assertTrue(zoneManager.getPlayerCurrentZoneIds(mockPlayer1).isEmpty()); // Use getPlayerCurrentZoneIds and pass Player

        // Update cache for player 1
        zoneManager.updatePlayerCurrentZones(player1UUID, p1InitialZoneIds); // Renamed
        assertEquals(p1InitialZoneIds, zoneManager.getPlayerCurrentZoneIds(mockPlayer1)); // Use getPlayerCurrentZoneIds

        // Update cache for player 1 again
        zoneManager.updatePlayerCurrentZones(player1UUID, p1UpdatedZoneIds); // Renamed
        assertEquals(p1UpdatedZoneIds, zoneManager.getPlayerCurrentZoneIds(mockPlayer1)); // Use getPlayerCurrentZoneIds

        // Player 2 should still be empty
        assertTrue(zoneManager.getPlayerCurrentZoneIds(mockPlayer2).isEmpty()); // Use getPlayerCurrentZoneIds

        // Clear cache for player 1
        zoneManager.clearPlayerZoneCache(player1UUID);
        assertTrue(zoneManager.getPlayerCurrentZoneIds(mockPlayer1).isEmpty()); // Use getPlayerCurrentZoneIds

        // Player 2 should remain unaffected
        assertTrue(zoneManager.getPlayerCurrentZoneIds(mockPlayer2).isEmpty()); // Use getPlayerCurrentZoneIds
    }

    @Test
    void getAllZones_shouldReturnAllRegisteredZones() {
        // Re-register zones for this specific test if setUp doesn't cover it due to other test modifications
        zoneManager.registerZone(zone1_w1);
        zoneManager.registerZone(zone2_w1);
        zoneManager.registerZone(overlappingZone_w1);
        zoneManager.registerZone(zone3_w2);
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
        loc_zone1_center = new Location(mockWorld1, 5, 5, 5); // Initialize
        // Re-register zones for this specific test to ensure clean state
        zoneManager.registerZone(zone1_w1);
        zoneManager.registerZone(overlappingZone_w1);

        assertTrue(zoneManager.getZones(loc_zone1_center).contains(zone1_w1)); // Renamed

        zoneManager.unregisterZone("zone1_w1");
        assertFalse(zoneManager.getZones(loc_zone1_center).contains(zone1_w1)); // Renamed
        assertTrue(zoneManager.getZones(loc_zone1_center).contains(overlappingZone_w1)); // Renamed // Overlap should still be there
    }
}
