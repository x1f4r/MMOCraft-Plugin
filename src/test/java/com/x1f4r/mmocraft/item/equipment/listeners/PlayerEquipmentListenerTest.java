package com.x1f4r.mmocraft.item.equipment.listeners;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.item.equipment.service.PlayerEquipmentManager;
import com.x1f4r.mmocraft.util.LoggingUtil;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryView; // Corrected package
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerEquipmentListenerTest {

    @Mock private MMOCraftPlugin mockPlugin;
    @Mock private PlayerEquipmentManager mockEquipmentManager;
    @Mock private LoggingUtil mockLogger;
    @Mock private Player mockPlayer;
    @Mock private BukkitScheduler mockScheduler;
    @Mock private BukkitTask mockTask; // For runTaskLater

    private PlayerEquipmentListener listener;

    @BeforeEach
    void setUp() {
        when(mockPlugin.getServer()).thenReturn(mock(org.bukkit.Server.class)); // Avoid NPE if getServer called
        when(mockPlugin.getServer().getScheduler()).thenReturn(mockScheduler);
        // Make runTaskLater execute runnable immediately for testing
        when(mockScheduler.runTaskLater(any(MMOCraftPlugin.class), any(Runnable.class), anyLong()))
            .thenAnswer(invocation -> {
                Runnable runnable = invocation.getArgument(1);
                runnable.run();
                return mockTask; // Return a mock task
            });

        listener = new PlayerEquipmentListener(mockPlugin, mockEquipmentManager, mockLogger);
    }

    @Test
    void onPlayerJoin_callsUpdateEquipmentStats() {
        PlayerJoinEvent mockEvent = new PlayerJoinEvent(mockPlayer, "join message");
        listener.onPlayerJoin(mockEvent);
        // Runnable is executed immediately due to mockScheduler setup
        verify(mockEquipmentManager).updateEquipmentStats(mockPlayer);
        verify(mockLogger).fine(contains("Player " + mockPlayer.getName() + " joined."));
    }

    @Test
    void onPlayerRespawn_callsUpdateEquipmentStats() {
        PlayerRespawnEvent mockEvent = new PlayerRespawnEvent(mockPlayer, mock(org.bukkit.Location.class), false);
        listener.onPlayerRespawn(mockEvent);
        verify(mockEquipmentManager).updateEquipmentStats(mockPlayer);
        verify(mockLogger).fine(contains("Player " + mockPlayer.getName() + " respawned."));
    }

    @Test
    void onInventoryClose_ifPlayer_callsUpdateEquipmentStats() {
        InventoryCloseEvent mockEvent = new InventoryCloseEvent(mock(InventoryView.class));
        // To make event.getPlayer() return our mockPlayer:
        // InventoryView view = mock(InventoryView.class);
        // when(view.getPlayer()).thenReturn(mockPlayer);
        // InventoryCloseEvent mockEvent = new InventoryCloseEvent(view);
        // This is complex. The listener checks `event.getPlayer() instanceof Player`.
        // Let's assume the event is constructed such that getPlayer() IS our Player.
        // The default mock InventoryView will return null for getPlayer().
        // So we need to ensure getPlayer() in the event returns our mockPlayer.

        // We cannot directly mock InventoryCloseEvent.getPlayer() as it's final from HumanEntity.
        // Instead, we create a real InventoryCloseEvent with a mocked InventoryView that returns our player.
        InventoryView mockView = mock(InventoryView.class);
        when(mockView.getPlayer()).thenReturn(mockPlayer);
        InventoryCloseEvent eventWithPlayer = new InventoryCloseEvent(mockView);


        listener.onInventoryClose(eventWithPlayer);
        verify(mockEquipmentManager).updateEquipmentStats(mockPlayer);
        verify(mockLogger).finer(contains("Player " + mockPlayer.getName() + " closed inventory."));
    }

    @Test
    void onInventoryClose_notPlayer_doesNotCallManager() {
        InventoryView mockView = mock(InventoryView.class);
        when(mockView.getPlayer()).thenReturn(mock(org.bukkit.entity.HumanEntity.class)); // Not a Player instance
        InventoryCloseEvent eventNotPlayer = new InventoryCloseEvent(mockView);

        listener.onInventoryClose(eventNotPlayer);
        verify(mockEquipmentManager, never()).updateEquipmentStats(any(Player.class));
    }


    @Test
    void onPlayerItemHeld_callsUpdateEquipmentStats_withDelay() {
        PlayerItemHeldEvent mockEvent = new PlayerItemHeldEvent(mockPlayer, 0, 1);
        listener.onPlayerItemHeld(mockEvent);

        // Verify runTaskLater was called with the correct plugin, runnable, and delay (1L)
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mockScheduler).runTaskLater(eq(mockPlugin), runnableCaptor.capture(), eq(1L));

        // Optionally execute the captured runnable to verify manager call
        runnableCaptor.getValue().run();
        verify(mockEquipmentManager).updateEquipmentStats(mockPlayer);
        verify(mockLogger).finer(contains("Player " + mockPlayer.getName() + " changed held item slot."));
    }
}
