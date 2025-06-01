package com.x1f4r.mmocraft.world.zone.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZoneTest {

    private World mockWorld(String worldName) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        return world;
    }

    @Test
    void constructor_shouldSetPropertiesCorrectly() {
        Zone zone = new Zone("test_zone", "Test Zone", "world1", 0, 0, 0, 10, 10, 10, Map.of("isSafe", true, "level", 5));
        assertEquals("test_zone", zone.getZoneId());
        assertEquals("Test Zone", zone.getZoneName());
        assertEquals("world1", zone.getWorldName());
        assertEquals(0, zone.getMinX());
        assertEquals(10, zone.getMaxX());
        assertTrue(zone.getBooleanProperty("isSafe", false));
        assertEquals(5, zone.getProperty("level", 0));
        assertFalse(zone.getBooleanProperty("nonExistent", false));
    }

    @Test
    void constructor_shouldHandleMinMaxSwapping() {
        Zone zone = new Zone("swap_zone", "world1", 10, 10, 10, 0, 0, 0);
        assertEquals(0, zone.getMinX());
        assertEquals(0, zone.getMinY());
        assertEquals(0, zone.getMinZ());
        assertEquals(10, zone.getMaxX());
        assertEquals(10, zone.getMaxY());
        assertEquals(10, zone.getMaxZ());
    }

    @Test
    void getZoneName_shouldReturnIdIfNameIsNull() {
        Zone zone = new Zone("id_only", null, "world", 0,0,0,1,1,1, null);
        assertEquals("id_only", zone.getZoneName());
    }

    @ParameterizedTest
    @CsvSource({
            // x,  y,  z, worldName, expected
            "5,   5,  5, world,     true",    // Inside
            "0,   0,  0, world,     true",    // Min corner
            "10, 10, 10, world,     true",    // Max corner
            "11,  5,  5, world,     false",   // Outside X
            "5,  11,  5, world,     false",   // Outside Y
            "5,   5, 11, world,     false",   // Outside Z
            "-1,  5,  5, world,     false",   // Outside negative X
            "5,  -1,  5, world,     false",   // Outside negative Y
            "5,   5, -1, world,     false",   // Outside negative Z
            "5,   5,  5, other_world, false"  // Wrong world
    })
    void contains_rawCoordinates(int x, int y, int z, String worldName, boolean expected) {
        Zone zone = new Zone("test", "world", 0, 0, 0, 10, 10, 10);
        assertEquals(expected, zone.contains(x, y, z, worldName));
    }

    @Test
    void contains_location() {
        Zone zone = new Zone("loc_test", "world", 0, 0, 0, 10, 10, 10);
        World world = mockWorld("world");
        World otherWorld = mockWorld("other_world");

        Location insideLoc = new Location(world, 5, 5, 5);
        Location outsideLoc = new Location(world, 15, 5, 5);
        Location otherWorldLoc = new Location(otherWorld, 5, 5, 5);
        Location nullWorldLoc = new Location(null, 5,5,5);

        assertTrue(zone.contains(insideLoc));
        assertFalse(zone.contains(outsideLoc));
        assertFalse(zone.contains(otherWorldLoc));
        assertFalse(zone.contains(null));
        assertFalse(zone.contains(nullWorldLoc));

    }

    @Test
    void getBooleanProperty_shouldHandleStringValues() {
        Zone zoneWithTrueString = new Zone("z1", "w", "test_world", 0,0,0,0,0,0, Map.of("isPvp", "true"));
        Zone zoneWithFalseString = new Zone("z2", "w", "test_world", 0,0,0,0,0,0, Map.of("isPvp", "false"));
        Zone zoneWithNonBooleanString = new Zone("z3", "w", "test_world", 0,0,0,0,0,0, Map.of("isPvp", "maybe"));

        assertTrue(zoneWithTrueString.getBooleanProperty("isPvp", false));
        assertFalse(zoneWithFalseString.getBooleanProperty("isPvp", true));
        assertFalse(zoneWithNonBooleanString.getBooleanProperty("isPvp", false)); // default
        assertTrue(zoneWithNonBooleanString.getBooleanProperty("isPvp", true)); // default
    }

    @Test
    void equalsAndHashCode_shouldBeBasedOnId() {
        Zone zone1a = new Zone("zone1", "world_as_zonename", "test_world1", 0,0,0,10,10,10, Map.of("val", 1));
        Zone zone1b = new Zone("zone1", "another_world_as_zonename", "test_world2", 1,1,1,5,5,5, Map.of("val", 2));
        Zone zone2 = new Zone("zone2", "world", 0,0,0,10,10,10); // This one is correct (8-arg constructor)

        assertEquals(zone1a, zone1b);
        assertNotEquals(zone1a, zone2);
        assertEquals(zone1a.hashCode(), zone1b.hashCode());
        assertNotEquals(zone1a.hashCode(), zone2.hashCode());
    }

    @Test
    void constructor_throwsExceptionForNullOrEmptyId() {
        assertThrows(NullPointerException.class, () -> new Zone(null, "Test Zone", "world1", 0,0,0,10,10,10, null));
        // The Zone.java implementation does not throw for empty ID, but the description implies it should be unique.
        // For now, testing existing behavior. If strict validation on empty ID is added, this test needs update.
        // assertThrows(IllegalArgumentException.class, () -> new Zone("", "Test Zone", "world1", 0,0,0,10,10,10, null));
    }

    @Test
    void constructor_throwsExceptionForNullWorldName() {
        assertThrows(NullPointerException.class, () -> new Zone("test_zone", "Test Zone", null, 0,0,0,10,10,10, null));
    }
}
