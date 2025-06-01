package com.x1f4r.mmocraft.world.zone.listeners;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.eventbus.EventBusService;
import com.x1f4r.mmocraft.util.LoggingUtil;
import com.x1f4r.mmocraft.world.zone.event.PlayerEnterZoneEvent;
import com.x1f4r.mmocraft.world.zone.event.PlayerLeaveZoneEvent;
import com.x1f4r.mmocraft.world.zone.model.Zone;
import com.x1f4r.mmocraft.world.zone.service.ZoneManager;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map; // Added import
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger; // Bukkit's logger

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerZoneTrackerListenerTest {

    @Mock private MMOCraftPlugin mockPlugin;
    @Mock private ZoneManager mockZoneManager;
    @Mock private EventBusService mockEventBusService;
    @Mock private LoggingUtil mockLoggingUtil; // Renamed to avoid conflict with Bukkit's Logger
    @Mock private Player mockPlayer;
    @Mock private World mockWorld;
    @Mock private Server mockServer;
    @Mock private BukkitScheduler mockScheduler;
    @Mock private BukkitTask mockTask; // For scheduler runTaskLater

    @Captor private ArgumentCaptor<PlayerEnterZoneEvent> enterEventCaptor;
    @Captor private ArgumentCaptor<PlayerLeaveZoneEvent> leaveEventCaptor;
    @Captor private ArgumentCaptor<Runnable> runnableCaptor;

    private PlayerZoneTrackerListener listener;
    private UUID playerUUID;
    private Zone zone1, zone2, zone3;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        playerUUID = UUID.randomUUID();

        when(mockPlayer.getUniqueId()).thenReturn(playerUUID);
        when(mockPlayer.getName()).thenReturn("TestPlayer");
        when(mockPlayer.getWorld()).thenReturn(mockWorld);
        when(mockWorld.getName()).thenReturn("test_world");

        when(mockPlugin.getServer()).thenReturn(mockServer);
        when(mockServer.getScheduler()).thenReturn(mockScheduler);
        // For Bukkit.getLogger() or plugin.getLogger() if listener uses it directly (not via LoggingUtil)
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));


        // Capture and immediately run tasks submitted to scheduler for join/teleport
        when(mockScheduler.runTaskLater(eq(mockPlugin), runnableCaptor.capture(), anyLong())).thenAnswer(invocation -> {
            runnableCaptor.getValue().run(); // Run the captured runnable
            return mockTask; // Return a mock task
        });


        listener = new PlayerZoneTrackerListener(mockZoneManager, mockLoggingUtil, mockEventBusService); // Corrected constructor order based on main code

        zone1 = new Zone("zone1", "Zone One", "test_world", 0,0,0, 10,10,10, Map.of("isSafe", true));
        zone2 = new Zone("zone2", "Zone Two", "test_world", 5,5,5, 15,15,15, Map.of("isPvp", true)); // Overlaps zone1
        zone3 = new Zone("zone3", "Zone Three", "test_world", 20,20,20, 30,30,30, null); // Already corrected, ensure order for listener
    }

    private Location loc(double x, double y, double z) {
        return new Location(mockWorld, x, y, z);
    }

    @Test
    void onPlayerMove_noBlockChange_shouldDoNothing() {
        Location from = loc(1.2, 1.2, 1.2); // Block X=1, Y=1, Z=1
        Location to = loc(1.8, 1.8, 1.8);   // Still Block X=1, Y=1, Z=1
        PlayerMoveEvent event = new PlayerMoveEvent(mockPlayer, from, to);

        listener.onPlayerMove(event);

        verify(mockZoneManager, never()).getPlayerCurrentZoneIds(any(Player.class)); // Corrected method name and arg type
        verify(mockZoneManager, never()).getZones(any(Location.class)); // Corrected method name
        verify(mockEventBusService, never()).call(any());
    }

    @Test
    void onPlayerMove_enterNewZone_shouldFireEnterEventAndCache() {
        Location from = loc(100, 100, 100); // Outside all zones
        Location to = loc(1, 1, 1);       // Inside zone1

        when(mockZoneManager.getPlayerCurrentZoneIds(mockPlayer)).thenReturn(Collections.emptySet()); // Corrected
        when(mockZoneManager.getZones(to)).thenReturn(List.of(zone1)); // Corrected

        PlayerMoveEvent event = new PlayerMoveEvent(mockPlayer, from, to);
        listener.onPlayerMove(event);

        verify(mockZoneManager).updatePlayerCurrentZones(playerUUID, Set.of("zone1")); // Corrected (pass Set<String>)
        verify(mockEventBusService).call(enterEventCaptor.capture());
        assertEquals(zone1, enterEventCaptor.getValue().getZone());
        assertEquals(mockPlayer, enterEventCaptor.getValue().getPlayer());
        verify(mockEventBusService, never()).call(any(PlayerLeaveZoneEvent.class));
        verify(mockPlayer).sendMessage(contains("Entered Zone One"));
    }

    @Test
    void onPlayerMove_leaveOldZone_shouldFireLeaveEventAndCache() {
        Location from = loc(1, 1, 1);       // Inside zone1
        Location to = loc(100, 100, 100); // Outside all zones

        when(mockZoneManager.getPlayerCurrentZoneIds(mockPlayer)).thenReturn(Set.of("zone1")); // Corrected (pass Set<String> for cache)
        when(mockZoneManager.getZones(to)).thenReturn(Collections.emptyList()); // Corrected

        PlayerMoveEvent event = new PlayerMoveEvent(mockPlayer, from, to);
        listener.onPlayerMove(event);

        verify(mockZoneManager).updatePlayerCurrentZones(playerUUID, Collections.emptySet()); // Corrected
        verify(mockEventBusService).call(leaveEventCaptor.capture());
        assertEquals(zone1, leaveEventCaptor.getValue().getZone());
        assertEquals(mockPlayer, leaveEventCaptor.getValue().getPlayer());
        verify(mockEventBusService, never()).call(any(PlayerEnterZoneEvent.class));
        verify(mockPlayer).sendMessage(contains("Left Zone One"));
    }

    @Test
    void onPlayerMove_moveToOverlappingZone_shouldFireCorrectEvents() {
        Location from = loc(1, 1, 1);   // Only in zone1 (0,0,0 to 10,10,10)
        Location to = loc(12, 12, 12);  // Only in zone2 (5,5,5 to 15,15,15)

        when(mockZoneManager.getPlayerCurrentZoneIds(mockPlayer)).thenReturn(Set.of("zone1")); // Corrected
        when(mockZoneManager.getZones(from)).thenReturn(List.of(zone1)); // Corrected
        when(mockZoneManager.getZones(to)).thenReturn(List.of(zone2)); // Corrected

        PlayerMoveEvent event = new PlayerMoveEvent(mockPlayer, from, to);
        listener.onPlayerMove(event);

        verify(mockZoneManager).updatePlayerCurrentZones(playerUUID, Set.of("zone2")); // Corrected
        verify(mockEventBusService).call(enterEventCaptor.capture());
        assertEquals(zone2, enterEventCaptor.getValue().getZone());
        verify(mockEventBusService).call(leaveEventCaptor.capture());
        assertEquals(zone1, leaveEventCaptor.getValue().getZone());
        verify(mockPlayer).sendMessage(contains("Entered Zone Two"));
        verify(mockPlayer).sendMessage(contains("Left Zone One"));
    }

    @Test
    void onPlayerMove_moveToSharedArea_fromOnlyZone1_toZone1AndZone2() {
        Location from = loc(1, 1, 1); // In zone1 only
        Location to = loc(7, 7, 7);   // In zone1 AND zone2

        when(mockZoneManager.getPlayerCurrentZoneIds(mockPlayer)).thenReturn(Set.of("zone1")); // Corrected
        when(mockZoneManager.getZones(to)).thenReturn(List.of(zone1, zone2)); // Corrected

        PlayerMoveEvent event = new PlayerMoveEvent(mockPlayer, from, to);
        listener.onPlayerMove(event);

        verify(mockZoneManager).updatePlayerCurrentZones(playerUUID, Set.of("zone1", "zone2")); // Corrected
        verify(mockEventBusService).call(enterEventCaptor.capture()); // Only zone2 should be new
        assertEquals(zone2, enterEventCaptor.getValue().getZone());
        verify(mockEventBusService, never()).call(any(PlayerLeaveZoneEvent.class)); // Still in zone1
        verify(mockPlayer).sendMessage(contains("Entered Zone Two"));
    }


    @Test
    void onPlayerJoin_shouldFireEnterEventsForInitialZones() {
        Location joinLocation = loc(7, 7, 7); // In zone1 and zone2
        when(mockPlayer.getLocation()).thenReturn(joinLocation);
        when(mockZoneManager.getZones(joinLocation)).thenReturn(List.of(zone1, zone2)); // Corrected

        PlayerJoinEvent event = new PlayerJoinEvent(mockPlayer, "Test join message");
        listener.onPlayerJoin(event);

        // Runnable is captured and executed by mockScheduler setup
        verify(mockZoneManager).updatePlayerCurrentZones(playerUUID, Set.of("zone1", "zone2")); // Corrected
        verify(mockEventBusService, times(2)).call(enterEventCaptor.capture());

        Set<Zone> enteredZones = new HashSet<>();
        for(PlayerEnterZoneEvent e : enterEventCaptor.getAllValues()) {
            enteredZones.add(e.getZone());
        }
        assertTrue(enteredZones.contains(zone1));
        assertTrue(enteredZones.contains(zone2));
        verify(mockPlayer, times(1)).sendMessage(contains("Entered Zone One"));
        verify(mockPlayer, times(1)).sendMessage(contains("Entered Zone Two"));
    }

    @Test
    void onPlayerQuit_shouldFireLeaveEventsAndClearCache() {
        when(mockZoneManager.getPlayerCurrentZoneIds(mockPlayer)).thenReturn(Set.of("zone1", "zone2")); // Corrected

        PlayerQuitEvent event = new PlayerQuitEvent(mockPlayer, "Test quit message");
        listener.onPlayerQuit(event);

        verify(mockZoneManager).clearPlayerZoneCache(playerUUID);
        verify(mockEventBusService, times(2)).call(leaveEventCaptor.capture());

        Set<Zone> leftZones = new HashSet<>();
        for(PlayerLeaveZoneEvent e : leaveEventCaptor.getAllValues()) {
            leftZones.add(e.getZone());
        }
        assertTrue(leftZones.contains(zone1));
        assertTrue(leftZones.contains(zone2));
        // Messages on quit are optional, not strictly testing them here unless defined as requirement
    }

    @Test
    void onPlayerTeleport_shouldBehaveLikeMove() {
        Location from = loc(100, 100, 100); // Outside
        Location to = loc(1, 1, 1);       // Inside zone1

        when(mockPlayer.getLocation()).thenReturn(to); // After teleport, player is at 'to'

        when(mockZoneManager.getPlayerCurrentZoneIds(mockPlayer)).thenReturn(Collections.emptySet()); // Corrected
        when(mockZoneManager.getZones(to)).thenReturn(List.of(zone1)); // Corrected

        PlayerTeleportEvent event = new PlayerTeleportEvent(mockPlayer, from, to, TeleportCause.PLUGIN);
        listener.onPlayerTeleport(event);

        // Runnable is captured and executed by mockScheduler setup
        verify(mockZoneManager).updatePlayerCurrentZones(playerUUID, Set.of("zone1")); // Corrected
        verify(mockEventBusService).call(enterEventCaptor.capture());
        assertEquals(zone1, enterEventCaptor.getValue().getZone());
        verify(mockPlayer).sendMessage(contains("Entered Zone One"));
    }

    @Test
    void onPlayerMove_noChangeInZones_shouldNotSendEventsOrMessages() {
        Location from = loc(1, 1, 1); // In zone1
        Location to = loc(2, 2, 2);   // Still only in zone1

        when(mockZoneManager.getPlayerCurrentZoneIds(mockPlayer)).thenReturn(Set.of("zone1")); // Corrected
        when(mockZoneManager.getZones(to)).thenReturn(List.of(zone1)); // Corrected

        PlayerMoveEvent event = new PlayerMoveEvent(mockPlayer, from, to);
        listener.onPlayerMove(event);

        // Cache update might happen with the same set, that's fine.
        verify(mockZoneManager).updatePlayerCurrentZones(playerUUID, Set.of("zone1")); // Corrected
        verify(mockEventBusService, never()).call(any(PlayerEnterZoneEvent.class));
        verify(mockEventBusService, never()).call(any(PlayerLeaveZoneEvent.class));
        verify(mockPlayer, never()).sendMessage(anyString()); // No redundant messages
    }
}
