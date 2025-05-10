package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.stats.EntityStatsManager; // Get manager from core
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        EntityStatsManager entityStatsManager = core.getEntityStatsManager();
        if (entityStatsManager != null) {
            entityStatsManager.reloadMobsConfig();
            sender.sendMessage(Component.text("MMOCraft mobs.yml configuration reloaded successfully!", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Note: Stats may only apply to newly spawned mobs unless a full server reload/restart occurs.", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Error: EntityStatsManager not available.", NamedTextColor.RED));
        }
        return true;
    }
}
