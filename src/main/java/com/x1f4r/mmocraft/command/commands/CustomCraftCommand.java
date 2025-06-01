package com.x1f4r.mmocraft.command.commands;

import com.x1f4r.mmocraft.command.AbstractPluginCommand;
import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.crafting.ui.CraftingUIManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class CustomCraftCommand extends AbstractPluginCommand {

    private final MMOCraftPlugin plugin; // Added field
    private final CraftingUIManager craftingUIManager;

    public CustomCraftCommand(MMOCraftPlugin plugin) {
        super("customcraft", "mmocraft.command.customcraft", "Opens the custom crafting interface.");
        this.plugin = plugin; // Initialize field
        this.craftingUIManager = plugin.getCraftingUIManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        if (craftingUIManager == null) {
            player.sendMessage(ChatColor.RED + "Crafting system is currently unavailable. Please contact an administrator.");
            plugin.getLoggingUtil().severe("CraftingUIManager is null for /customcraft command!");
            return true;
        }

        craftingUIManager.openCraftingUI(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList(); // No arguments for this command
    }
}
