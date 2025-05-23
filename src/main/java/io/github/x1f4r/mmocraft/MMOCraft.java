package io.github.x1f4r.mmocraft;

import io.github.x1f4r.mmocraft.core.MMOCore;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class MMOCraft extends JavaPlugin {

    private static MMOCraft instance;
    private static Logger pluginLogger; // Renamed from your old MMOLogger for consistency

    private MMOCore core;

    @Override
    public void onEnable() {
        instance = this;
        pluginLogger = this.getLogger(); // Use the plugin's dedicated logger

        pluginLogger.info("MMOCraft enabling...");

        this.core = new MMOCore(this);
        try {
            this.core.onEnable(); // MMOCore handles service initialization and registration
        } catch (Exception e) {
            pluginLogger.log(Level.SEVERE, "A critical error occurred during MMOCore enabling sequence! Disabling plugin.", e);
            // No need to call e.printStackTrace() here, logger.log with Throwable does it.
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        pluginLogger.info("MMOCraft has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        pluginLogger.info("MMOCraft disabling...");
        if (this.core != null) {
            this.core.onDisable();
        }
        pluginLogger.info("MMOCraft has been disabled.");
        // Nullify static instances to help GC and prevent issues on /reload (though full server restart is always better)
        core = null;
        pluginLogger = null; // Logger instance itself might be managed by Bukkit, but our static ref can be cleared
        instance = null;
    }

    public static MMOCraft getInstance() {
        if (instance == null) {
            // This can happen if other plugins try to access it before onEnable or after onDisable completes.
            // Or if a static initializer in another class of this plugin calls it too early.
            // Returning Bukkit.getPluginManager().getPlugin("MMOCraftNextGen") might be an option but less safe.
            throw new IllegalStateException("MMOCraft plugin instance is not available. Plugin might be disabled or not yet fully enabled.");
        }
        return instance;
    }

    /**
     * Provides the plugin's logger.
     * It's generally better for services to use an injected LoggingService.
     * @return The plugin's logger.
     */
    public static Logger getPluginLogger() {
        // If called before onEnable (e.g., from a static block), instance might be null.
        // In such rare cases, fall back to Bukkit's logger for the plugin name.
        if (pluginLogger == null) {
            if (instance != null) { // Should be set if instance is set
                return instance.getLogger();
            }
            // Extremely unlikely to be needed if code structure is good, but as a last resort:
            Logger fallbackLogger = Logger.getLogger("MMOCraftNextGen_Fallback");
            fallbackLogger.warning("MMOCraft.getPluginLogger() called when internal logger was null and instance was null. Using fallback.");
            return fallbackLogger;
        }
        return pluginLogger;
    }

    public MMOCore getCore() {
        if (core == null) {
            throw new IllegalStateException("MMOCore is not available. Plugin might be disabled or failed to enable core components.");
        }
        return core;
    }
}