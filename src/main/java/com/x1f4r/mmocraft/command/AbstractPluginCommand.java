package com.x1f4r.mmocraft.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player; // For potential player-specific messages

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractPluginCommand implements CommandExecutor, TabCompleter, CommandExecutable {

    protected final String commandName;
    protected final String permission;
    protected final String description;
    protected final Map<String, CommandExecutable> subCommands;

    public AbstractPluginCommand(String commandName, String permission, String description) {
        this.commandName = commandName;
        this.permission = permission;
        this.description = description;
        this.subCommands = new HashMap<>();
    }

    // Method to register subcommands
    protected void registerSubCommand(String subCommandName, CommandExecutable executable) {
        subCommands.put(subCommandName.toLowerCase(), executable);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
            sender.sendMessage("You do not have permission to use this command."); // Consider making this configurable
            return true; // Return true because we handled the command (by denying it)
        }

        if (args.length > 0) {
            String subCommandName = args[0].toLowerCase();
            if (subCommands.containsKey(subCommandName)) {
                CommandExecutable subExecutable = subCommands.get(subCommandName);
                // Prepare new args array for the subcommand
                String[] subArgs = new String[args.length - 1];
                System.arraycopy(args, 1, subArgs, 0, args.length - 1);
                return subExecutable.onCommand(sender, subArgs);
            }
        }
        // If no subcommand matched, or no args provided, execute this command's logic
        return this.onCommand(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
            return Collections.emptyList();
        }

        if (args.length == 1) { // Suggest subcommands or first argument completions
            List<String> completions = new ArrayList<>(subCommands.keySet());
            List<String> mainCompletions = this.onTabComplete(sender, args);
            if (mainCompletions != null) {
                completions.addAll(mainCompletions);
            }
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length > 1) {
            String subCommandName = args[0].toLowerCase();
            if (subCommands.containsKey(subCommandName)) {
                // CommandExecutable subExecutable = subCommands.get(subCommandName); // subExecutable no longer has onTabComplete
                // String[] subArgs = new String[args.length - 1];
                // System.arraycopy(args, 1, subArgs, 0, args.length - 1);
                // return subExecutable.onTabComplete(sender, subArgs); // This line is removed
                return Collections.emptyList(); // No further specific tab-completion for sub-commands for now via CommandExecutable
            }
        }
        // If no subcommand, delegate to this command's specific tab completion for further args
        return this.onTabComplete(sender, args);
    }

    // Abstract methods from CommandExecutable to be implemented by concrete commands
    @Override
    public abstract boolean onCommand(CommandSender sender, String[] args);

    // No longer overriding from CommandExecutable, provide a default implementation
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList(); // Default implementation
    }

    public String getCommandName() {
        return commandName;
    }

    public String getPermission() {
        return permission;
    }

    public String getDescription() {
        return description;
    }
}
