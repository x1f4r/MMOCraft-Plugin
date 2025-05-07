package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.player.PlayerStats;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager; // Added import
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlayerStatsCommand implements CommandExecutor {

    private final PlayerStatsManager statsManager;

    public PlayerStatsCommand(MMOCraft plugin) {
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

        statsManager.updateAndApplyAllEffects(player); // Ensure stats are fresh
        PlayerStats stats = statsManager.getStats(player);

        player.sendMessage(ChatColor.GOLD + "--- Your MMOCraft Stats ---");
        player.sendMessage(ChatColor.RED + " Strength: " + ChatColor.WHITE + stats.getStrength());
        player.sendMessage(ChatColor.DARK_AQUA + " Defense: " + ChatColor.WHITE + stats.getDefense()); // Display custom Defense
        player.sendMessage(ChatColor.GREEN + " Crit Chance: " + ChatColor.WHITE + stats.getCritChance() + "%");
        player.sendMessage(ChatColor.GREEN + " Crit Damage: " + ChatColor.WHITE + "+" + stats.getCritDamage() + "%");
        player.sendMessage(ChatColor.AQUA + " Max Mana: " + ChatColor.WHITE + stats.getMaxMana());
        player.sendMessage(ChatColor.AQUA + " Current Mana: " + ChatColor.WHITE + stats.getCurrentMana());
        player.sendMessage(ChatColor.WHITE + " Speed: " + ChatColor.YELLOW + stats.getSpeed() + "%");
        player.sendMessage(ChatColor.GOLD + "-------------------------");

        return true;
    }
}