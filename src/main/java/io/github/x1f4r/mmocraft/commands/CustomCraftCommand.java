package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.crafting.CraftingGUIListener; // Get listener from core
import io.github.x1f4r.mmocraft.core.MMOPlugin; // Import for logger
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CustomCraftCommand implements CommandExecutor {

    private final MMOCore core;
    // No need to store listener directly if we get it from core

    public CustomCraftCommand(MMOCore core) {
        this.core = core;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        Player player = (Player) sender;

        // Permission check (defined in plugin.yml)
        if (!player.hasPermission("mmocraft.command.craft")) {
             player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
             return true;
        }

        CraftingGUIListener guiListener = core.getCraftingGUIListener();
        if (guiListener != null) {
            guiListener.openCustomCraftingGUI(player);
        } else {
            player.sendMessage(Component.text("Error: Crafting system not available.", NamedTextColor.RED));
            MMOPlugin.getMMOLogger().severe("CustomCraftCommand executed but CraftingGUIListener was null in MMOCore!");
        }
        return true;
    }
}
