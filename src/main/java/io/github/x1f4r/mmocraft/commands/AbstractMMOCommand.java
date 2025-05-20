package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.services.LoggingService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractMMOCommand implements CommandExecutor, TabCompleter {
    protected final MMOCore core;
    protected final LoggingService logging;
    private final String commandLabel; // The main command string (e.g., "mmocadmin")
    final String requiredBasePermission; // Optional permission for the base command itself (changed to package-private)
    protected final Map<String, SubCommand> subCommands = new LinkedHashMap<>(); // Preserve insertion order for help

    /**
     * Functional interface for executing a subcommand.
     */
    @FunctionalInterface
    public interface SubCommandExecutor {
        void execute(@NotNull CommandSender sender, @NotNull String[] args) throws CommandArgumentException;
    }

    /**
     * Custom exception for command argument parsing errors.
     */
    public static class CommandArgumentException extends IllegalArgumentException {
        public CommandArgumentException(String message) {
            super(message);
        }
    }


    protected record SubCommand(
            @NotNull String name, // The actual subcommand string
            @Nullable String permission,
            int minArgs,
            @NotNull String usageFormat, // e.g., "<playerName> stats set <stat> <value>" (args for sub-executor)
            @NotNull SubCommandExecutor executor,
            @Nullable SubCommandTabCompleter tabCompleter
    ) {}

    @FunctionalInterface
    protected interface SubCommandTabCompleter {
        List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args);
    }

    public AbstractMMOCommand(@NotNull MMOCore core, @NotNull String commandLabel, @Nullable String requiredBasePermission) {
        this.core = Objects.requireNonNull(core, "MMOCore cannot be null for AbstractMMOCommand");
        this.logging = core.getService(LoggingService.class); // Assumes LoggingService is registered
        this.commandLabel = Objects.requireNonNull(commandLabel, "Command label cannot be null");
        this.requiredBasePermission = requiredBasePermission;
    }

    protected void addSubCommand(@NotNull String name, @Nullable String permission, int minArgs, @NotNull String usageArgumentFormat,
                                 @NotNull SubCommandExecutor executor, @Nullable SubCommandTabCompleter tabCompleter) {
        String lowerName = name.toLowerCase();
        if (subCommands.containsKey(lowerName)) {
            logging.warn("Subcommand '" + lowerName + "' is already registered for command /" + commandLabel + ". Overwriting.");
        }
        String fullUsage = "/" + commandLabel + " " + name + (usageArgumentFormat.isEmpty() ? "" : " " + usageArgumentFormat);
        subCommands.put(lowerName, new SubCommand(name, permission, minArgs, fullUsage, executor, tabCompleter));
    }

    // Convenience overload without usage format (will generate a basic one)
    protected void addSubCommand(@NotNull String name, @Nullable String permission, int minArgs,
                                 @NotNull SubCommandExecutor executor, @Nullable SubCommandTabCompleter tabCompleter) {
        addSubCommand(name, permission, minArgs, generateDefaultUsageArgs(minArgs), executor, tabCompleter);
    }

    private String generateDefaultUsageArgs(int minArgs) {
        if (minArgs == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= minArgs; i++) {
            sb.append("<arg").append(i).append("> ");
        }
        return sb.toString().trim();
    }


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (requiredBasePermission != null && !sender.hasPermission(requiredBasePermission)) {
            sendNoPermissionMessage(sender, requiredBasePermission);
            return true;
        }

        if (args.length > 0) {
            String subCommandName = args[0].toLowerCase();
            SubCommand subCmd = subCommands.get(subCommandName);
            if (subCmd != null) {
                if (subCmd.permission() != null && !sender.hasPermission(subCmd.permission())) {
                    sendNoPermissionMessage(sender, subCmd.permission());
                    return true;
                }
                String[] subArgs = (args.length > 1) ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
                if (subArgs.length < subCmd.minArgs()) {
                    sendUsageMessage(sender, subCmd.usageFormat());
                    return true;
                }
                try {
                    subCmd.executor().execute(sender, subArgs);
                } catch (CommandArgumentException e) { // Catch our specific argument exception
                    sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
                    sendUsageMessage(sender, subCmd.usageFormat()); // Show usage on argument error
                } catch (Exception e) {
                    logging.severe("Error executing subcommand '" + subCommandName + "' for command /" + label + " by " + sender.getName(), e);
                    sender.sendMessage(Component.text("An unexpected error occurred while executing this command.", NamedTextColor.RED));
                }
                return true;
            }
        }
        // No valid subcommand matched or no args given for subcommand path
        return handleBaseCommand(sender, label, args);
    }

    /**
     * Handles the command if no subcommands are matched or if the base command is called directly.
     * Default implementation shows a help message listing available subcommands.
     */
    protected boolean handleBaseCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        sendHelpMessage(sender, label);
        return true;
    }

    protected void sendHelpMessage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("Available subcommands for /" + label + ":", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        if (subCommands.isEmpty()) {
            sender.sendMessage(Component.text("  (No subcommands available or you lack permissions)", NamedTextColor.GRAY));
            return;
        }
        subCommands.forEach((name, sub) -> {
            if (sub.permission() == null || sender.hasPermission(sub.permission())) {
                sender.sendMessage(Component.text("  /" + label + " " + sub.name(), NamedTextColor.YELLOW)
                        .append(Component.text(sub.usageFormat().substring(sub.usageFormat().indexOf(sub.name()) + sub.name().length()), NamedTextColor.AQUA)) // Show only args part of usage
                );
            }
        });
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (requiredBasePermission != null && !sender.hasPermission(requiredBasePermission)) {
            return Collections.emptyList();
        }

        if (args.length == 1) { // Tab completing the subcommand name itself
            String currentArg = args[0].toLowerCase();
            return subCommands.keySet().stream()
                    .filter(name -> subCommands.get(name).permission() == null || sender.hasPermission(subCommands.get(name).permission()))
                    .filter(name -> name.startsWith(currentArg))
                    .sorted()
                    .toList();
        } else if (args.length > 1) { // Tab completing arguments for a specific subcommand
            SubCommand subCmd = subCommands.get(args[0].toLowerCase());
            if (subCmd != null && (subCmd.permission() == null || sender.hasPermission(subCmd.permission()))) {
                if (subCmd.tabCompleter() != null) {
                    // Pass arguments *after* the subcommand name
                    return subCmd.tabCompleter().onTabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
                }
            }
        }
        return defaultTabComplete(sender, args); // Fallback for base command or if no specific completer
    }

    /** Override for base command tab completion if args.length == 0 or no subcommand matches */
    protected List<String> defaultTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    protected void sendNoPermissionMessage(CommandSender sender, String permissionNode) {
        sender.sendMessage(Component.text("You do not have permission (" + permissionNode + ") to use this command.", NamedTextColor.RED));
    }

    protected void sendUsageMessage(CommandSender sender, String usage) {
        sender.sendMessage(Component.text("Usage: " + usage, NamedTextColor.RED));
    }

    // --- Argument Parsing Utilities ---
    protected Player parsePlayer(@NotNull CommandSender sender, String playerName, String argName) throws CommandArgumentException {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            throw new CommandArgumentException("Player '" + playerName + "' not found for argument '" + argName + "'.");
        }
        return target;
    }

    protected int parseInt(@NotNull String input, @NotNull String argName) throws CommandArgumentException {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new CommandArgumentException("Invalid " + argName + ": '" + input + "' is not a whole number.");
        }
    }
    protected double parseDouble(@NotNull String input, @NotNull String argName) throws CommandArgumentException {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            throw new CommandArgumentException("Invalid " + argName + ": '" + input + "' is not a number.");
        }
    }
    protected boolean parseBoolean(@NotNull String input, @NotNull String argName) throws CommandArgumentException {
        if ("true".equalsIgnoreCase(input)) return true;
        if ("false".equalsIgnoreCase(input)) return false;
        throw new CommandArgumentException("Invalid " + argName + ": '" + input + "' is not 'true' or 'false'.");
    }

    // --- Common Tab Completions ---
    protected static List<String> tabCompletePlayerNames(String currentArg) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(currentArg.toLowerCase()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    protected static List<String> tabCompleteFromList(List<String> options, String currentArg) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(currentArg.toLowerCase()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}