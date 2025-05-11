package io.github.x1f4r.mmocraft.commands.user;

import io.github.x1f4r.mmocraft.commands.AbstractMMOCommand;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.player.stats.CalculatedPlayerStats;
import io.github.x1f4r.mmocraft.services.PlayerStatsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class UserStatsCommand extends AbstractMMOCommand {

    public UserStatsCommand(MMOCore core) {
        // Command label in plugin.yml, permission for this user command
        super(core, "mmostats", "mmocraft.user.command.stats");
    }

    @Override
    protected boolean handleBaseCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be run by a player.", NamedTextColor.RED));
            return true;
        }

        PlayerStatsService statsService = core.getService(PlayerStatsService.class);
        CalculatedPlayerStats currentStats = statsService.getCalculatedStats(player);

        player.sendMessage(Component.text("--- Your MMOCraft Stats ---", NamedTextColor.GOLD));

        // Health (from Bukkit Attribute, influenced by our MaxHealth stat)
        double bukkitMaxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double currentHealth = player.getHealth();
        sendStatLine(player, "Health", String.format("%.0f / %.0f", currentHealth, bukkitMaxHealth), NamedTextColor.RED);

        // Mana (from PlayerResourceService, max from CalculatedPlayerStats)
        int currentMana = core.getService(PlayerResourceService.class).getCurrentMana(player);
        sendStatLine(player, "Mana", currentMana + " / " + currentStats.maxMana(), NamedTextColor.AQUA);

        player.sendMessage(Component.empty()); // Spacer

        // Core Combat Stats
        sendStatLine(player, "Strength", currentStats.strength(), NamedTextColor.RED);
        sendStatLine(player, "Defense", currentStats.defense(), NamedTextColor.GREEN);
        sendStatLine(player, "Crit Chance", currentStats.critChance() + "%", NamedTextColor.GOLD);
        sendStatLine(player, "Crit Damage", "+" + currentStats.critDamage() + "%", NamedTextColor.GOLD);

        // Utility Stats
        sendStatLine(player, "Speed", currentStats.speedPercent() + "%", NamedTextColor.WHITE);
        player.sendMessage(Component.empty());

        sendStatLine(player, "Mining Speed", currentStats.miningSpeed(), NamedTextColor.YELLOW);
        sendStatLine(player, "Foraging Speed", currentStats.foragingSpeed(), NamedTextColor.DARK_GREEN);
        sendStatLine(player, "Fishing Speed", currentStats.fishingSpeed(), NamedTextColor.BLUE);
        sendStatLine(player, "Shooting Speed", currentStats.shootingSpeed(), NamedTextColor.LIGHT_PURPLE);

        player.sendMessage(Component.text("-------------------------", NamedTextColor.GOLD));
        return true;
    }

    private void sendStatLine(Player player, String label, Object value, TextColor valueColor) {
        player.sendMessage(Component.text("  " + label + ": ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(value), valueColor)));
    }
}