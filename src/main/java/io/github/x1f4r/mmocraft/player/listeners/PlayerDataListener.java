package io.github.x1f4r.mmocraft.player.listeners;

import io.github.x1f4r.mmocraft.services.PlayerDataService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener responsible for handling player join and quit events to trigger
 * PlayerDataService actions like profile loading and saving.
 */
public class PlayerDataListener implements Listener {

    private final PlayerDataService playerDataService;

    public PlayerDataListener(PlayerDataService playerDataService) {
        this.playerDataService = playerDataService;
    }

    /**
     * Handles player join events.
     * Priority LOWEST to ensure player data is loaded before other plugins might need it.
     * @param event The PlayerJoinEvent.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Asynchronously load data to avoid blocking main thread if persistence is slow
        // However, Player PDC is generally fast. For now, synchronous for simplicity.
        // If performance becomes an issue, consider:
        // Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        //    PlayerProfile profile = playerDataService.getProfile(player); // load/create part
        //    Bukkit.getScheduler().runTask(plugin, () -> {
        //        playerDataService.handlePlayerJoin(player, profile); // main thread notifications
        //    });
        // });
        playerDataService.handlePlayerJoin(player);
    }

    /**
     * Handles player quit events.
     * Priority MONITOR (or HIGHEST) to ensure data is saved after all other plugin interactions
     * that might have modified the player's state/profile.
     * @param event The PlayerQuitEvent.
     */
    @EventHandler(priority = EventPriority.MONITOR) // MONITOR is late, good for final saves.
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerDataService.handlePlayerQuit(event.getPlayer());
    }
}