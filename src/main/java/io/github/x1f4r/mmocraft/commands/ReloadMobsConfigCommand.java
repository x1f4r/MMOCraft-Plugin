package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.stats.EntityStatsManager; // Get manager from core
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ReloadMobsConfigCommand implements CommandExecutor {

    private final MMOCore core;

    public ReloadMobsConfigCommand(MMOCore core) {
        this.core = core;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mmocraft.command.reloadmobs")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        EntityStatsManager entityStatsManager = core.getEntityStatsManager();
        if (entityStatsManager != null) {
            entityStatsManager.reloadMobsConfig();
            // TODO: Consider iterating online non-player LivingEntities and re-applying stats
            // This is complex and potentially performance-intensive.
            // A simpler approach is that only newly spawned mobs will get the updated stats.
            sender.sendMessage(ChatColor.GREEN + "MMOCraft mobs.yml configuration reloaded successfully!");
            sender.sendMessage(ChatColor.YELLOW + "Note: Stats may only apply to newly spawned mobs unless a full server reload/restart occurs.");
        } else {
            sender.sendMessage(ChatColor.RED + "Error: EntityStatsManager not available.");
        }
        return true;
    }
}
