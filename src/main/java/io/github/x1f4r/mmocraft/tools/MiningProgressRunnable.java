package io.github.x1f4r.mmocraft.tools;

import io.github.x1f4r.mmocraft.services.ToolProficiencyService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * A BukkitRunnable that executes every game tick for a player actively mining a block
 * under the custom block breaking system. It calls out to ToolProficiencyService
 * to calculate and apply mining progress.
 */
public class MiningProgressRunnable extends BukkitRunnable {
    private final ToolProficiencyService toolService;
    private final UUID playerId;
    // Store direct player reference for quick access, but always re-check validity
    private final Player player;

    public MiningProgressRunnable(ToolProficiencyService toolService, Player player) {
        this.toolService = toolService;
        this.playerId = player.getUniqueId();
        this.player = player; // Initial reference
    }

    @Override
    public void run() {
        // Re-fetch player instance each tick to ensure it's still valid and online
        Player currentPlayer = Bukkit.getPlayer(playerId);

        if (currentPlayer == null || !currentPlayer.isOnline() || currentPlayer.isDead()) {
            // Player logged off or died while mining
            toolService.clearMiningTask(playerId, true); // true to send break animation cancel if possible
            this.cancel(); // Stop this runnable
            return;
        }

        // Delegate the core logic to ToolProficiencyService
        // This method in ToolProficiencyService will check block validity, player distance,
        // line of sight, calculate progress, send animation, and handle block breaking.
        toolService.processMiningProgressTick(playerId, currentPlayer);
    }

    // No need to override cancel() unless specific cleanup for this runnable itself is needed.
    // ToolProficiencyService.clearMiningTask handles the primary cleanup.
}