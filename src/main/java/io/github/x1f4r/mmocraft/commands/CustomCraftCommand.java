package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.crafting.CraftingGUIListener; // Updated import
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CustomCraftCommand implements CommandExecutor {

    private final CraftingGUIListener guiListener;

    public CustomCraftCommand(CraftingGUIListener listener) {
        this.guiListener = listener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        if (this.guiListener != null) {
            guiListener.openCustomCraftingGUI(player);
        } else {
            player.sendMessage("Error: GUI Listener not initialized!");
            sender.getServer().getLogger().warning("CustomCraftCommand executed but guiListener was null!");
        }
        return true;
    }
}
