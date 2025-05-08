package io.github.x1f4r.mmocraft.core;

import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

public final class MMOPlugin extends JavaPlugin {

    private static MMOPlugin instance;
    private MMOCore core;
    private static final Logger log = Logger.getLogger("MMOCraft");

    @Override
    public void onEnable() {
        instance = this;
        log.info("MMOCraft Plugin (Refactored Engine) enabling...");

        NBTKeys.init(this);

        this.core = new MMOCore(this);
        if (!this.core.enableServices()) {
            log.severe("Failed to enable MMOCore services! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        log.info("MMOCraft Plugin (Refactored Engine) has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        log.info("MMOCraft Plugin (Refactored Engine) disabling...");
        if (this.core != null) {
            this.core.disableServices();
        }
        log.info("MMOCraft Plugin (Refactored Engine) has been disabled.");
        instance = null;
    }

    public MMOCore getCore() {
        return core;
    }

    public static MMOPlugin getInstance() {
        return instance;
    }

    public static Logger getMMOLogger() {
        return log;
    }
}

