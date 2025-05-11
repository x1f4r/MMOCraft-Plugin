package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import org.bukkit.configuration.file.FileConfiguration; // For reading debug_mode

import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggingService implements Service {
    private final Logger logger; // Standard Java Logger provided by Bukkit plugin
    private boolean debugMode = false;
    private ConfigService configService; // To fetch debug_mode setting during init and reloads

    /**
     * Constructor for LoggingService.
     * @param plugin The main plugin instance, used to get its logger.
     */
    public LoggingService(MMOCraft plugin) {
        // It's generally better to use the plugin's passed logger instance
        // than Bukkit.getLogger("MMOCraftNextGen") to ensure it's the correct one.
        this.logger = plugin.getLogger();
    }

    @Override
    public void initialize(MMOCore core) throws IllegalStateException {
        // Attempt to get ConfigService. If MMOCore initializes this *very* first (as internalLogger),
        // ConfigService might not be registered yet. Handle this gracefully.
        try {
            this.configService = core.getService(ConfigService.class);
            FileConfiguration mainConfig = configService.getMainConfig(); // Ensures config.yml is loaded
            if (mainConfig != null) {
                this.debugMode = mainConfig.getBoolean("logging.debug_mode", false);
            } else {
                // This case should be rare if ConfigService.getMainConfig() is robust
                logger.warning("LoggingService: Main configuration was null during initialization. Debug mode defaults to false.");
                this.debugMode = false;
            }
        } catch (IllegalStateException e) {
            // This happens if LoggingService is the very first service (MMOCore.internalLogger)
            // and ConfigService is not yet available from MMOCore.getService().
            logger.config("LoggingService initializing before ConfigService is fully available (expected for MMOCore's internal logger). Debug mode defaults to false initially.");
            this.debugMode = false; // Sensible default if config can't be read yet
        }
        // Don't use `info()` here if this logger is being used by MMOCore to log its own init.
        // MMOCore will log "Initializing service: LoggingService"
    }

    @Override
    public void shutdown() {
        // No specific shutdown tasks needed for a simple logger wrapper.
        info("LoggingService shutdown."); // Use info for shutdown confirmation
    }

    /**
     * Called by ConfigService when the main config is reloaded, allowing
     * LoggingService to update its debug_mode state.
     */
    public void reloadConfigSettings() {
        if (configService != null) {
            boolean oldDebugMode = this.debugMode;
            this.debugMode = configService.getMainConfig().getBoolean("logging.debug_mode", false);
            if (oldDebugMode != this.debugMode) {
                info("Debug mode " + (this.debugMode ? "ENABLED" : "DISABLED") + " via configuration reload.");
            }
        } else {
            warn("Attempted to reload LoggingService config settings, but ConfigService reference is null.");
        }
    }

    public void debug(String message, Object... args) {
        if (debugMode) {
            logger.log(Level.INFO, "[DEBUG] " + String.format(message, args));
        }
    }

    public void info(String message, Object... args) {
        logger.log(Level.INFO, String.format(message, args));
    }

    public void warn(String message, Object... args) {
        logger.log(Level.WARNING, String.format(message, args));
    }

    public void warnOnce(String key, String message, Object... args) {
        // TODO: Implement a simple "warn once" mechanism if needed
        // (e.g., using a Set<String> to store keys of messages already warned)
        warn(message, args);
    }

    public void severe(String message, Object... args) {
        logger.log(Level.SEVERE, String.format(message, args));
    }

    public void severe(String message, Throwable t, Object... args) {
        logger.log(Level.SEVERE, String.format(message, args), t);
    }

    public boolean isDebugMode() {
        return debugMode;
    }


    /**
     * Allows manual toggling of debug mode for the current plugin session,
     * for example, by an administrative command.
     * Note: This change is in-memory and will not persist across plugin reloads
     * unless the command also updates the configuration file and triggers a save/reload.
     * @param newDebugState The new state for debug mode (true to enable, false to disable).
     */

    public void setDebugMode(boolean newDebugState) {
        if (this.debugMode != newDebugState) {
            this.debugMode = newDebugState;
            // Log the change using the logger itself
            info("Debug mode manually set to: " + (this.debugMode ? "ENABLED" : "DISABLED"));
        }
    }
}