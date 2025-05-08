package io.github.x1f4r.mmocraft.listeners; // General listeners package

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinQuitListener implements Listener {

    private final PlayerStatsManager playerStatsManager;

    public PlayerJoinQuitListener(MMOCore core) {
        this.playerStatsManager = core.getPlayerStatsManager();
    }

    @EventHandler(priority = EventPriority.LOWEST) // Process early on join
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerStatsManager.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR) // Process late on quit
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerStatsManager.handlePlayerQuit(event.getPlayer());
    }
}

