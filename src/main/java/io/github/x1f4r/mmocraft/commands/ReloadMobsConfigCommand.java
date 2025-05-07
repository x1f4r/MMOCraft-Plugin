package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.MMOCraft;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ReloadMobsConfigCommand implements CommandExecutor {

    private final MMOCraft plugin;

    public ReloadMobsConfigCommand(MMOCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mmocraft.command.reloadmobs")) { // Define this permission
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (plugin.getEntityStatsManager() != null) {
            plugin.getEntityStatsManager().reloadMobsConfig();
            sender.sendMessage(ChatColor.GREEN + "MMOCraft mobs.yml configuration reloaded successfully!");
        } else {
            sender.sendMessage(ChatColor.RED + "Error: EntityStatsManager not initialized.");
        }
        return true;
    }
}