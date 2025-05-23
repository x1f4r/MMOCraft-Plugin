package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.commands.admin.*; // Import your admin command group handlers
import io.github.x1f4r.mmocraft.core.MMOCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MMOAdminCommand extends AbstractMMOCommand {

    // Map to store category handlers
    private final Map<String, AbstractMMOCommand> categoryHandlers = new HashMap<>();

    public MMOAdminCommand(MMOCore core) {
        super(core, "mmocadmin", "mmocraft.admin"); // Base command label and top-level permission

        // Instantiate and register category handlers
        // The commandLabelForUsage passed to these handlers helps them build correct usage strings.
        categoryHandlers.put("system", new AdminSystemCommands(core, "mmocadmin system"));
        categoryHandlers.put("player", new AdminPlayerCommands(core, "mmocadmin player"));
        categoryHandlers.put("item", new AdminItemCommands(core, "mmocadmin item"));
        categoryHandlers.put("mob", new AdminMobCommands(core, "mmocadmin mob"));
        // Add more categories as needed (e.g., "config", "debug", "ability")
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (requiredBasePermission != null && !sender.hasPermission(requiredBasePermission)) {
            sendNoPermissionMessage(sender, requiredBasePermission);
            return true;
        }

        if (args.length > 0) {
            String category = args[0].toLowerCase();
            AbstractMMOCommand handler = categoryHandlers.get(category);

            if (handler != null) {
                // Check if the handler itself has a base permission (AdminSystemCommands etc. have null for this currently)
                // String handlerBasePermission = handler.getRequiredBasePermission(); // Need a getter in AbstractMMOCommand
                // if (handlerBasePermission != null && !sender.hasPermission(handlerBasePermission)) {
                //     sendNoPermissionMessage(sender, handlerBasePermission);
                //     return true;
                // }

                // Pass the remaining arguments to the category handler
                return handler.onCommand(sender, command, label + " " + category, Arrays.copyOfRange(args, 1, args.length));
            } else {
                sender.sendMessage(Component.text("Unknown admin category: " + category, NamedTextColor.RED));
                sendHelpMessage(sender, label); // Show master help
                return true;
            }
        }

        // No category specified, show master help for /mmocadmin
        sendHelpMessage(sender, label);
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (requiredBasePermission != null && !sender.hasPermission(requiredBasePermission)) {
            return List.of();
        }

        if (args.length == 1) { // Tab completing the category name
            String currentArg = args[0].toLowerCase();
            return categoryHandlers.keySet().stream()
                    .filter(catName -> catName.startsWith(currentArg))
                    // Optional: Check if player has permission for any subcommand within that category
                    // For simplicity, just show categories if base /mmocadmin perm is met.
                    .sorted()
                    .toList();
        } else if (args.length > 1) { // Tab completing arguments for a specific category
            String category = args[0].toLowerCase();
            AbstractMMOCommand handler = categoryHandlers.get(category);
            if (handler != null) {
                // Pass remaining args to the category handler's tab completer
                return handler.onTabComplete(sender, command, alias + " " + category, Arrays.copyOfRange(args, 1, args.length));
            }
        }
        return List.of(); // Default to no suggestions if no category matches
    }

    // Override sendHelpMessage to show categories
    @Override
    protected void sendHelpMessage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("--- MMOCraft Admin Help ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Usage: /" + label + " <category> <subcommand> [args...]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Available categories:", NamedTextColor.AQUA));
        categoryHandlers.forEach((name, handler) -> {
            // Could add a check here if sender has any permission related to this category's subcommands
            sender.sendMessage(Component.text("  " + name, NamedTextColor.GREEN)
                    .append(Component.text(" - Manages " + name + "-related features.", NamedTextColor.GRAY)));
            // To show subcommands of each category:
            // handler.sendHelpMessage(sender, label + " " + name); // This might be too verbose for master help
        });
        sender.sendMessage(Component.text("Type /" + label + " <category> for specific subcommands.", NamedTextColor.YELLOW));
    }

    // RequiredBasePermission getter (add to AbstractMMOCommand if needed for finer permission checks)
    // public String getRequiredBasePermission() { return requiredBasePermission; }
}