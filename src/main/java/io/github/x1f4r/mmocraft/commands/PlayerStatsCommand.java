package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.player.PlayerStats;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlayerStatsCommand implements CommandExecutor {

    // private final MMOCraft plugin; // Not strictly needed if only using StatsManager
    private final PlayerStatsManager statsManager;

    public PlayerStatsCommand(MMOCraft plugin) {
        // this.plugin = plugin;
        this.statsManager = plugin.getPlayerStatsManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("mmocraft.command.stats")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        // Ensure stats are up-to-date before displaying by explicitly calling the update method
        // This is important because the periodic refresh might not have run *just* before the command.
        statsManager.updateAndApplyAllEffects(player);
        PlayerStats stats = statsManager.getStats(player); // Get the freshly updated stats

        player.sendMessage(ChatColor.GOLD + "--- Your MMOCraft Stats ---");
        player.sendMessage(ChatColor.RED + " Strength: " + ChatColor.WHITE + stats.getStrength());
        player.sendMessage(ChatColor.GREEN + " Crit Chance: " + ChatColor.WHITE + stats.getCritChance() + "%");
        player.sendMessage(ChatColor.GREEN + " Crit Damage: " + ChatColor.WHITE + "+" + stats.getCritDamage() + "%");
        player.sendMessage(ChatColor.AQUA + " Max Mana: " + ChatColor.WHITE + stats.getMaxMana());
        player.sendMessage(ChatColor.AQUA + " Current Mana: " + ChatColor.WHITE + stats.getCurrentMana()); // Also show current mana here
        player.sendMessage(ChatColor.WHITE + " Speed: " + ChatColor.YELLOW + stats.getSpeed() + "%");
        player.sendMessage(ChatColor.GOLD + "-------------------------");

        return true;
    }
}