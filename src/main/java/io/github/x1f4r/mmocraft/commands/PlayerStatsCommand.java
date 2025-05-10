package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import io.github.x1f4r.mmocraft.stats.PlayerStats; // Correct package
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlayerStatsCommand implements CommandExecutor {

    private final PlayerStatsManager statsManager;

    public PlayerStatsCommand(MMOCore core) {
        this.statsManager = core.getPlayerStatsManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("mmocraft.command.stats")) {
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        // Ensure stats are up-to-date before displaying
        // Note: A scheduled update might already be pending, but forcing one ensures immediate accuracy.
        // Consider if this is necessary or if relying on the periodic/event-based updates is sufficient.
        // statsManager.scheduleStatsUpdate(player); // Optional: Force immediate update (slight delay)
        PlayerStats stats = statsManager.getStats(player); // Get potentially cached but recently updated stats

        player.sendMessage(Component.text("--- Your MMOCraft Stats ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text(" Strength: ", NamedTextColor.RED).append(Component.text(String.valueOf(stats.getStrength()), NamedTextColor.WHITE)));
        player.sendMessage(Component.text(" Defense: ", NamedTextColor.AQUA).append(Component.text(String.valueOf(stats.getDefense()), NamedTextColor.WHITE))); // Changed color
        player.sendMessage(Component.text(" Crit Chance: ", NamedTextColor.GREEN).append(Component.text(stats.getCritChance() + "%", NamedTextColor.WHITE)));
        player.sendMessage(Component.text(" Crit Damage: ", NamedTextColor.GREEN).append(Component.text("+" + stats.getCritDamage() + "%", NamedTextColor.WHITE)));
        player.sendMessage(Component.text(" Max Mana: ", NamedTextColor.BLUE).append(Component.text(String.valueOf(stats.getMaxMana()), NamedTextColor.WHITE))); // Changed color
        player.sendMessage(Component.text(" Current Mana: ", NamedTextColor.BLUE).append(Component.text(String.valueOf(stats.getCurrentMana()), NamedTextColor.WHITE)));
        player.sendMessage(Component.text(" Speed: ", NamedTextColor.YELLOW).append(Component.text(stats.getSpeed() + "%", NamedTextColor.WHITE))); // Changed color
        player.sendMessage(Component.text(" Mining Speed: ", NamedTextColor.DARK_GREEN).append(Component.text(String.valueOf(stats.getMiningSpeed()), NamedTextColor.WHITE)));
        player.sendMessage(Component.text(" Foraging Speed: ", NamedTextColor.DARK_GREEN).append(Component.text(String.valueOf(stats.getForagingSpeed()), NamedTextColor.WHITE)));
        player.sendMessage(Component.text(" Fishing Speed: ", NamedTextColor.DARK_AQUA).append(Component.text(String.valueOf(stats.getFishingSpeed()), NamedTextColor.WHITE)));
        player.sendMessage(Component.text(" Shooting Speed: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(stats.getShootingSpeed()), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("-------------------------", NamedTextColor.GOLD));

        return true;
    }
}
