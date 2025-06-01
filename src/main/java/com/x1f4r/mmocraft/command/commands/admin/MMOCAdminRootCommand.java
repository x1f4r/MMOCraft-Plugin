package com.x1f4r.mmocraft.command.commands.admin;

import com.x1f4r.mmocraft.command.AbstractPluginCommand;
import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.util.StringUtil;
import com.x1f4r.mmocraft.world.resourcegathering.service.ActiveNodeManager; // For permission check example
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MMOCAdminRootCommand extends AbstractPluginCommand {

    private final MMOCraftPlugin plugin;

    public MMOCAdminRootCommand(MMOCraftPlugin plugin) {
        super("mmocadm", "mmocraft.admin", "Base command for MMOCraft administration.");
        this.plugin = plugin;

        // Register other admin command modules as subcommands here
        registerSubCommand("combat", new CombatAdminCommand(plugin));
        registerSubCommand("item", new ItemAdminCommand(plugin));
        registerSubCommand("resource", new ResourceAdminCommand(plugin, "mmocadm resource", "mmocraft.admin.resource"));
        // Example: registerSubCommand("config", new ConfigAdminCommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        // Base /mmocadm command - show help for its subcommands
        sender.sendMessage(StringUtil.colorize("&6--- MMOCraft Admin Commands ---"));
        if (sender.hasPermission("mmocraft.admin.combat")) {
            sender.sendMessage(StringUtil.colorize("&e/mmocadm combat &7- Access combat admin commands."));
        }
        if (sender.hasPermission("mmocraft.admin.item")) {
            sender.sendMessage(StringUtil.colorize("&e/mmocadm item &7- Access item admin commands."));
        }
        if (sender.hasPermission("mmocraft.admin.resource")) { // General permission for the resource module
            sender.sendMessage(StringUtil.colorize("&e/mmocadm resource &7- Access resource gathering admin commands."));
        }

        // Check if the sender has permission for any registered subcommand to avoid "No admin modules available"
        // if they have permission for a dynamically registered one but not the hardcoded ones above.
        boolean hasAtLeastOneSubCommandPermission = subCommands.values().stream()
            .filter(cmdExec -> cmdExec instanceof AbstractPluginCommand) // Ensure it's an AbstractPluginCommand
            .map(cmdExec -> (AbstractPluginCommand) cmdExec)           // Cast to AbstractPluginCommand
            .anyMatch(abstractCmd -> sender.hasPermission(abstractCmd.getPermission()));

        if (!hasAtLeastOneSubCommandPermission) {
             sender.sendMessage(StringUtil.colorize("&7No admin modules available to you or none registered."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        // This method in AbstractPluginCommand already handles suggesting registered subcommand names
        // like "combat" when args.length == 1.
        // If further arguments were directly for /mmocadm, they would be handled here.
        return super.onTabComplete(sender, args); // Delegates to AbstractPluginCommand's logic
    }
}
