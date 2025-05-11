package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.commands.AbstractMMOCommand;
import io.github.x1f4r.mmocraft.commands.MMOAdminCommand; // Master admin command
import io.github.x1f4r.mmocraft.commands.user.UserCustomCraftCommand;
import io.github.x1f4r.mmocraft.commands.user.UserStatsCommand;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import org.bukkit.command.PluginCommand;
import org.jetbrains.annotations.NotNull;

public class CommandService implements Service {
    private MMOCore core;
    private LoggingService logging;

    public CommandService(MMOCore core) {
        this.core = core;
    }

    @Override
    public void initialize(MMOCore core) {
        this.logging = core.getService(LoggingService.class);
        registerCommands();
        logging.info(getServiceName() + " initialized and application commands registered.");
    }

    @Override
    public void shutdown() {
        // Commands are unregistered by Bukkit when the plugin disables.
        // No specific shutdown action needed here unless dynamically unregistering.
    }

    private void registerCommands() {
        // --- Register Master Admin Command ---
        // This command (e.g., /mmocadmin) will then route to specific admin command groups.
        MMOAdminCommand masterAdminCommand = new MMOAdminCommand(core);
        registerCommandInstance(masterAdminCommand, "mmocadmin"); // Ensure "mmocadmin" is in plugin.yml

        // --- Register User-Facing Commands ---
        UserCustomCraftCommand customCraftCommand = new UserCustomCraftCommand(core);
        registerCommandInstance(customCraftCommand, "customcraft"); // Ensure "customcraft" is in plugin.yml

        UserStatsCommand userStatsCommand = new UserStatsCommand(core);
        registerCommandInstance(userStatsCommand, "mmostats"); // Ensure "mmostats" is in plugin.yml

        // Add other command registrations here as they are created
        // For example, if AdminSystemCommands was its own top-level command:
        // AdminSystemCommands systemCmd = new AdminSystemCommands(core, "mmocsystem");
        // registerCommandInstance(systemCmd, "mmocsystem");
    }

    /**
     * Helper method to register a command instance (executor and tab completer)
     * to a command name defined in plugin.yml.
     * @param commandInstance The instance of your command class (extending AbstractMMOCommand).
     * @param commandNameInPluginYml The name of the command as defined in plugin.yml.
     */
    private void registerCommandInstance(@NotNull AbstractMMOCommand commandInstance, @NotNull String commandNameInPluginYml) {
        PluginCommand pluginCommand = core.getPlugin().getCommand(commandNameInPluginYml);
        if (pluginCommand != null) {
            pluginCommand.setExecutor(commandInstance);
            pluginCommand.setTabCompleter(commandInstance);
            if (logging.isDebugMode()) { // Only log successful registration in debug to reduce startup spam
                logging.debug("Registered command handler for /" + commandNameInPluginYml +
                        " -> " + commandInstance.getClass().getSimpleName());
            }
        } else {
            logging.warn("Command '/" + commandNameInPluginYml + "' not found in plugin.yml! " +
                    "Cannot register handler: " + commandInstance.getClass().getSimpleName());
        }
    }
}