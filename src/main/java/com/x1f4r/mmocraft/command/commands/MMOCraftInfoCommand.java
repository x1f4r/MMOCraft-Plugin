package com.x1f4r.mmocraft.command.commands;

import com.x1f4r.mmocraft.command.AbstractPluginCommand;
import com.x1f4r.mmocraft.command.CommandExecutable;
import com.x1f4r.mmocraft.util.LoggingUtil; // Added
import com.x1f4r.mmocraft.util.StringUtil; // Added
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

// import java.util.Arrays; // Keep if used, for now it's not. // Now confirmed unused
import java.util.Collections;
import java.util.List;

public class MMOCraftInfoCommand extends AbstractPluginCommand {

    private final JavaPlugin plugin;
    private final LoggingUtil logger; // Added

    public MMOCraftInfoCommand(JavaPlugin plugin, String commandName, String permission, String description, LoggingUtil logger) {
        super(commandName, permission, description);
        this.plugin = plugin;
        this.logger = logger; // Initialize logger

        // Register a simple subcommand: /mmoc version
        registerSubCommand("version", new CommandExecutable() {
            @Override
            public boolean onCommand(CommandSender sender, String[] args) {
                // Example of using logger if needed: logger.debug("Version subcommand executed by " + sender.getName());
                sender.sendMessage(StringUtil.colorize("&6[MMOCraft] &bVersion: &f" + plugin.getDescription().getVersion()));
                return true;
            }

            // onTabComplete removed as CommandExecutable is now a functional interface for onCommand only
            // @Override
            // public List<String> onTabComplete(CommandSender sender, String[] args) {
            //     return Collections.emptyList(); // No further arguments for "version"
            // }
        });

        // Register another simple subcommand: /mmoc help
        registerSubCommand("help", (s, a) -> { // s for sender, a for args
            s.sendMessage(StringUtil.colorize("&6--- MMOCraft Help ---"));
            s.sendMessage(StringUtil.colorize("&b/" + commandName + " version &7- Shows plugin version."));
            s.sendMessage(StringUtil.colorize("&b/" + commandName + " help &7- Shows this help message."));
            // Add more help messages as other subcommands are added
            // Example: logger.info("Help shown to " + s.getName());
            return true;
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        // This is the default action if no subcommand (or a non-matching one) is provided.
        // Or if /mmoc is typed with no arguments.
        sender.sendMessage(StringUtil.colorize("&6[MMOCraft] &aWelcome to MMOCraft! Your adventure begins."));
        sender.sendMessage(StringUtil.colorize("&eType '/" + commandName + " help' for a list of commands."));
        // Example: logger.info("Base MMOCraftInfoCommand executed by " + sender.getName());
        return true;
    }

    // No longer overriding an abstract method from AbstractPluginCommand as it's now concrete
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        // This method is called by AbstractPluginCommand if no subcommand matches the first argument.
        // For the base /mmoc command, if we expect further arguments beyond subcommands,
        // we can provide them here. Otherwise, return empty list or null.
        // Since subcommands handle their own tab completion, this is mostly for the base command's direct args.
        if (args.length == 1) { // If they are typing the first argument (potential subcommand)
            // AbstractPluginCommand's onTabComplete will already suggest "version" and "help"
            // We can add more suggestions here if /mmoc itself had direct arguments not part of subcommands
            return null; // Let AbstractPluginCommand handle subcommand suggestions
        }
        return Collections.emptyList(); // No further specific suggestions for the base command itself
    }
}
