package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import io.github.x1f4r.mmocraft.stats.PlayerStats; // Correct package
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlayerStatsCommand implements CommandExecutor {

    private final MMOCore core;
    private final PlayerStatsManager statsManager;

    public PlayerStatsCommand(MMOCore core) {
        this.core = core;
        this.statsManager = core.getPlayerStatsManager();
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

        // Ensure stats are up-to-date before displaying
        // Note: A scheduled update might already be pending, but forcing one ensures immediate accuracy.
        // Consider if this is necessary or if relying on the periodic/event-based updates is sufficient.
        // statsManager.scheduleStatsUpdate(player); // Optional: Force immediate update (slight delay)
        PlayerStats stats = statsManager.getStats(player); // Get potentially cached but recently updated stats

        player.sendMessage(ChatColor.GOLD + "--- Your MMOCraft Stats ---");
        player.sendMessage(ChatColor.RED + " Strength: " + ChatColor.WHITE + stats.getStrength());
        player.sendMessage(ChatColor.AQUA + " Defense: " + ChatColor.WHITE + stats.getDefense()); // Changed color
        player.sendMessage(ChatColor.GREEN + " Crit Chance: " + ChatColor.WHITE + stats.getCritChance() + "%");
        player.sendMessage(ChatColor.GREEN + " Crit Damage: " + ChatColor.WHITE + "+" + stats.getCritDamage() + "%");
        player.sendMessage(ChatColor.BLUE + " Max Mana: " + ChatColor.WHITE + stats.getMaxMana()); // Changed color
        player.sendMessage(ChatColor.BLUE + " Current Mana: " + ChatColor.WHITE + stats.getCurrentMana());
        player.sendMessage(ChatColor.YELLOW + " Speed: " + ChatColor.WHITE + stats.getSpeed() + "%"); // Changed color
        player.sendMessage(ChatColor.DARK_GREEN + " Mining Speed: " + ChatColor.WHITE + stats.getMiningSpeed());
        player.sendMessage(ChatColor.DARK_GREEN + " Foraging Speed: " + ChatColor.WHITE + stats.getForagingSpeed());
        player.sendMessage(ChatColor.DARK_AQUA + " Fishing Speed: " + ChatColor.WHITE + stats.getFishingSpeed());
        player.sendMessage(ChatColor.GRAY + " Shooting Speed: " + ChatColor.WHITE + stats.getShootingSpeed());
        player.sendMessage(ChatColor.GOLD + "-------------------------");

        return true;
    }
}
