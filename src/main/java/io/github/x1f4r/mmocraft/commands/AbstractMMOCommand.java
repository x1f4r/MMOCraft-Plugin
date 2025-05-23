package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.exceptions.CommandArgumentException;
import io.github.x1f4r.mmocraft.exceptions.PlayerNotFoundException;
import io.github.x1f4r.mmocraft.services.ConfigService;
import io.github.x1f4r.mmocraft.services.LoggingService;
import io.github.x1f4r.mmocraft.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public abstract class AbstractMMOCommand implements TabExecutor {

    protected final MMOCraft plugin;
    protected final MMOCore core;
    protected final LoggingService logging;
    protected final ConfigService configService;

    final String requiredBasePermission; // Was private, changed to package-private
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    private final Map<String, String> subCommandAliases = new HashMap<>();

    // Added commandLabel parameter to match subclass calls, may be unused here for now
    public AbstractMMOCommand(MMOCore core, String commandLabel, String requiredBasePermission) {
        this.plugin = core.getPlugin();
        this.core = core;
        this.logging = core.getService(LoggingService.class); // Changed to use getService
        this.configService = core.getService(ConfigService.class); // Changed to use getService
        this.requiredBasePermission = requiredBasePermission;
        // this.commandLabel = commandLabel; // If AbstractMMOCommand needs its own label
    }

    protected void registerSubCommand(String name, SubCommand subCommand) {
        subCommands.put(name.toLowerCase(), subCommand);
        if (subCommand.getAliases() != null) {
            for (String alias : subCommand.getAliases()) {
                subCommandAliases.put(alias.toLowerCase(), name.toLowerCase());
            }
        }
    }


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (requiredBasePermission != null && !sender.hasPermission(requiredBasePermission)) {
                MessageUtils.sendPlayerMessage(sender, configService.getNoPermissionMessage(), NamedTextColor.RED); // Changed to direct call
                return true;
            }
            return showHelp(sender, label);
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            subCommandName = subCommandAliases.get(subCommandName);
            if (subCommandName != null) {
                subCommand = subCommands.get(subCommandName);
            }
        }

        if (subCommand == null) {
            if (requiredBasePermission != null && !sender.hasPermission(requiredBasePermission)) {
                MessageUtils.sendPlayerMessage(sender, configService.getNoPermissionMessage(), NamedTextColor.RED); // Changed to direct call
                return true;
            }
            return showHelp(sender, label);
        }

        if (subCommand.getPermission() != null && !sender.hasPermission(subCommand.getPermission())) {
            MessageUtils.sendPlayerMessage(sender, configService.getNoPermissionMessage(), NamedTextColor.RED); // Changed to direct call
            return true;
        }

        if (!subCommand.isConsoleAllowed() && sender instanceof ConsoleCommandSender) {
            MessageUtils.sendPlayerMessage(sender, configService.getConsoleNotAllowedMessage(), NamedTextColor.RED); // Changed to direct call
            return true;
        }

        String[] subCommandArgs = Arrays.copyOfRange(args, 1, args.length);

        try {
            if (!subCommand.execute(sender, subCommandArgs)) {
                MessageUtils.sendPlayerMessage(sender, Component.text("Usage: /" + label + " " + subCommandName + " " + subCommand.getUsage(), NamedTextColor.RED));
            }
        } catch (CommandArgumentException e) {
            MessageUtils.sendPlayerMessage(sender, e.getMessageComponent());
        } catch (Exception e) {
            logging.severe("Error executing command: /" + label + " " + String.join(" ", args), e); // Changed to logging.severe
            MessageUtils.sendPlayerMessage(sender, configService.getCommandErrorMessage(), NamedTextColor.RED); // Changed to direct call
        }

        return true;
    }


    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return subCommands.entrySet().stream()
                    .filter(entry -> entry.getValue().getPermission() == null || sender.hasPermission(entry.getValue().getPermission()))
                    .map(Map.Entry::getKey)
                    .filter(name -> name.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            subCommandName = subCommandAliases.get(subCommandName);
            if (subCommandName != null) {
                subCommand = subCommands.get(subCommandName);
            }
        }

        if (subCommand != null) {
            if (subCommand.getPermission() != null && !sender.hasPermission(subCommand.getPermission())) {
                return Collections.emptyList();
            }
            String[] subCommandArgs = Arrays.copyOfRange(args, 1, args.length);
            List<String> suggestions = subCommand.onTabComplete(sender, subCommandArgs);
            if (suggestions != null) {
                return suggestions;
            }
        }

        return Collections.emptyList();
    }


    protected boolean showHelp(CommandSender sender, String label) {
        // Default help implementation, can be overridden
        sender.sendMessage(Component.text("Available sub-commands:", NamedTextColor.GOLD));
        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            if (entry.getValue().getPermission() == null || sender.hasPermission(entry.getValue().getPermission())) {
                String usage = entry.getValue().getUsage();
                String description = entry.getValue().getDescription();
                Component helpMessage = Component.text("/" + label + " " + entry.getKey() + (usage.isEmpty() ? "" : " " + usage), NamedTextColor.YELLOW)
                        .append(Component.text(" - " + description, NamedTextColor.GRAY));
                sender.sendMessage(helpMessage);
            }
        }
        return true;
    }

    protected Player parsePlayer(@NotNull CommandSender sender, String playerName, String argName) throws CommandArgumentException {
        if (playerName == null || playerName.isEmpty()) {
            throw new CommandArgumentException(argName + " cannot be empty.", argName);
        }
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            throw new PlayerNotFoundException(playerName);
        }
        return target;
    }

    protected OfflinePlayer parseOfflinePlayer(@NotNull CommandSender sender, String playerName, String argName) throws CommandArgumentException {
        if (playerName == null || playerName.isEmpty()) {
            throw new CommandArgumentException(argName + " cannot be empty.", argName);
        }
        // First try to find online player by exact name match
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer;
        }
        
        // Try fuzzy matching for online players
        Player fuzzyMatch = Bukkit.getPlayer(playerName);
        if (fuzzyMatch != null) {
            return fuzzyMatch;
        }
        
        // Fallback to deprecated method with proper documentation
        @SuppressWarnings("deprecation") // Necessary for offline player lookup by name
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) { // Ensure player exists
            // A more robust check might involve querying a database if player data is stored off-server
            // For now, if getOfflinePlayer returns a non-null object that hasn't played, it might be a new profile
            // but if it's truly not found (e.g., after checking hasPlayedBefore), then throw.
            // This logic might need adjustment based on how server handles offline players.
            // Let's assume for now if hasPlayedBefore is false and not online, they are not found.
            throw new PlayerNotFoundException(playerName);
        }
        return target;
    }

     protected CompletableFuture<OfflinePlayer> parseOfflinePlayerAsync(@NotNull CommandSender sender, String playerName, String argName) throws CommandArgumentException {
        if (playerName == null || playerName.isEmpty()) {
            throw new CommandArgumentException(argName + " cannot be empty.", argName);
        }
        return CompletableFuture.supplyAsync(() -> {
            // First try to find online player (non-blocking)
            Player onlinePlayer = Bukkit.getPlayerExact(playerName);
            if (onlinePlayer != null) {
                return onlinePlayer;
            }
            
            // This is inherently tricky because Bukkit's getOfflinePlayer(String) can be blocking.
            // For now, using the potentially blocking call in an async task.
            @SuppressWarnings("deprecation") // Necessary for offline player lookup by name
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);

            if (offlinePlayer == null || (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline() && (offlinePlayer.getUniqueId() == null || !Bukkit.getOfflinePlayer(offlinePlayer.getUniqueId()).hasPlayedBefore()))) {
                 // Further check by UUID if available, as names can change.
                 // If still no record, then throw PlayerNotFoundException.
                 // This logic is complex due to Bukkit's API limitations around truly verifying existence by name non-blockingly.
                throw new PlayerNotFoundException(playerName); // This will be caught by CompletableFuture's exception handling
            }
            return offlinePlayer;
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof PlayerNotFoundException) {
                throw (PlayerNotFoundException) ex.getCause();
            }
            // Log other unexpected exceptions if necessary
            logging.warn("Unexpected error parsing offline player async: " + playerName, ex);
            throw new PlayerNotFoundException(playerName); // Default to PlayerNotFound
        });
    }


    protected int parseInt(String value, String argName) throws CommandArgumentException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new CommandArgumentException("Invalid number for " + argName + ": " + value, argName);
        }
    }

    protected double parseDouble(String value, String argName) throws CommandArgumentException {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new CommandArgumentException("Invalid number for " + argName + ": " + value, argName);
        }
    }

    protected boolean parseBoolean(String value, String argName) throws CommandArgumentException {
        if (value.equalsIgnoreCase("true")) {
            return true;
        } else if (value.equalsIgnoreCase("false")) {
            return false;
        } else {
            throw new CommandArgumentException("Invalid boolean for " + argName + " (true/false): " + value, argName);
        }
    }

    protected <T extends Enum<T>> T parseEnum(String value, Class<T> enumClass, String argName) throws CommandArgumentException {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CommandArgumentException("Invalid value for " + argName + ": " + value +
                    ". Possible values: " + Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", ")), argName);
        }
    }
}
