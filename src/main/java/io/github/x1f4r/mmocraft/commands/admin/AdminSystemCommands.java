package io.github.x1f4r.mmocraft.commands.admin;

import io.github.x1f4r.mmocraft.commands.AbstractMMOCommand;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.services.ConfigService;
import io.github.x1f4r.mmocraft.services.LoggingService;
import io.github.x1f4r.mmocraft.services.PlayerStatsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull; // Added import

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AdminSystemCommands extends AbstractMMOCommand {

    // This constructor is for when AdminSystemCommands is a direct command handler itself (e.g., /mmocsystem)
    // If it's a sub-handler for a master command, the master command would instantiate it differently.
    public AdminSystemCommands(MMOCore core, String commandLabelForUsage) {
        super(core, commandLabelForUsage, null); // No top-level permission for this sub-handler itself

        // Subcommand: reload <configName|all>
        addSubCommand("reload", "mmocraft.admin.system.reload", 1, "<configName|all>",
                (sender, args) -> {
                    ConfigService configService = core.getService(ConfigService.class);
                    String configToReload = args[0].toLowerCase();
                    boolean success;
                    String fullConfigName = configToReload.endsWith(".yml") ? configToReload : configToReload + ".yml";

                    if (configToReload.equals("all")) {
                        configService.reloadAllConfigs(); // Assumes this method logs internally
                        sender.sendMessage(Component.text("All MMOCraft configurations reload attempt initiated.", NamedTextColor.GREEN));
                    } else if (configService.isManagedConfig(fullConfigName)) {
                        success = configService.reloadConfig(fullConfigName);
                        if (success) {
                            sender.sendMessage(Component.text("Configuration '" + fullConfigName + "' reloaded successfully.", NamedTextColor.GREEN));
                        } else {
                            sender.sendMessage(Component.text("Failed to reload configuration '" + fullConfigName + "'. Check console for errors.", NamedTextColor.RED));
                        }
                    } else {
                        sender.sendMessage(Component.text("Unknown or unmanaged configuration file: '" + fullConfigName + "'.", NamedTextColor.RED)
                                .append(Component.newline()).append(Component.text("Valid managed configs (without .yml): ", NamedTextColor.GRAY))
                                .append(Component.text(String.join(", ", configService.getManagedConfigFileNames()), NamedTextColor.YELLOW)));
                    }
                },
                (sender, args) -> { // Tab completer for "reload"
                    if (args.length == 1) { // Completing <configName|all>
                        List<String> suggestions = new ArrayList<>(core.getService(ConfigService.class).getManagedConfigFileNames());
                        suggestions.add("all");
                        return tabCompleteFromList(suggestions, args[0]);
                    }
                    return List.of();
                });

        // Subcommand: debug <serviceName|all> <true|false>
        // For Part 7, only LoggingService debug toggle is implemented simply.
        addSubCommand("debug", "mmocraft.admin.system.debug", 1, "<true|false> [serviceName]", // ServiceName is optional for now
                (sender, args) -> {
                    LoggingService loggingService = core.getService(LoggingService.class);
                    boolean newState;
                    try {
                        newState = parseBoolean(args[0], "debug state");
                    } catch (CommandArgumentException e) {
                        sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
                        return;
                    }

                    // String targetService = (args.length > 1) ? args[1].toLowerCase() : "logging"; // Default to logging service

                    // For now, only toggles the main LoggingService debugMode
                    // Future: allow targeting other services if they implement a setDebug(boolean)
                    loggingService.setDebugMode(newState);
                    sender.sendMessage(Component.text("Global plugin debug mode set to: " + newState, NamedTextColor.GREEN));
                },
                (sender, args) -> {
                    if (args.length == 1) return tabCompleteFromList(List.of("true", "false"), args[0]);
                    // if (args.length == 2) return tabCompleteFromList(List.of("LoggingService", "all", ... other service names ...), args[1]);
                    return List.of();

        
        // Subcommand: performance [serviceName]
        addSubCommand("performance", "mmocraft.admin.system.performance", 0, "[serviceName]",
                (sender, args) -> {
                    if (args.length == 0) {
                        // Show performance for all services that support it
                        sender.sendMessage(Component.text("=== MMOCraft Performance Statistics ===", NamedTextColor.GOLD));
                        
                        // PlayerStatsService performance
                        try {
                            PlayerStatsService statsService = core.getService(PlayerStatsService.class);
                            if (statsService != null) {
                                sender.sendMessage(Component.text(statsService.getPerformanceStats(), NamedTextColor.YELLOW));
                            }
                        } catch (Exception e) {
                            sender.sendMessage(Component.text("Error getting PlayerStatsService performance: " + e.getMessage(), NamedTextColor.RED));
                        }
                        
                        sender.sendMessage(Component.text("Use '/mmocadmin system performance <serviceName>' for detailed stats", NamedTextColor.GRAY));
                    } else {
                        String serviceName = args[0];
                        switch (serviceName.toLowerCase()) {
                            case "playerstats", "stats" -> {
                                PlayerStatsService statsService = core.getService(PlayerStatsService.class);
                                if (statsService != null) {
                                    sender.sendMessage(Component.text(statsService.getPerformanceStats(), NamedTextColor.YELLOW));
                                } else {
                                    sender.sendMessage(Component.text("PlayerStatsService not available", NamedTextColor.RED));
                                }
                            }
                            default -> sender.sendMessage(Component.text("Unknown service: " + serviceName + ". Available: playerstats", NamedTextColor.RED));
                        }
                    }
                },
                (sender, args) -> {
                    if (args.length == 1) {
                        return tabCompleteFromList(List.of("playerstats", "stats"), args[0]);
                    }
                    return List.of();
                });                });
    }

    // This handler is called if `/mmocadmin system` is run with no further args,
    // or if this class were registered as its own command like `/mmocsystem`.
    @Override
    protected boolean handleBaseCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        // 'label' would be "mmocadmin system" if routed by a master command, or "mmocsystem" if direct.
        sendHelpMessage(sender, label);
        return true;
    }
}