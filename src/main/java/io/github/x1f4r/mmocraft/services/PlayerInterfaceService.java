package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import io.github.x1f4r.mmocraft.player.stats.CalculatedPlayerStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerInterfaceService implements Service {
    private MMOCore core;
    private LoggingService logging;
    private PlayerStatsService playerStatsService;
    private PlayerResourceService playerResourceService;
    private MMOCraft plugin;

    private BukkitTask globalActionBarUpdateTask;
    private long actionBarIntervalTicks;
    private boolean actionBarEnabled;
    private final Set<UUID> playersNeedingImmediateActionBarUpdate = new HashSet<>();


    // Formatting (could be configurable)
    private final DecimalFormat healthFormat = new DecimalFormat("#0"); // No decimals for health/mana
    private TextColor healthColorVal;
    private TextColor healthColorMax;
    private TextColor manaColorVal;
    private TextColor manaColorMax;
    private TextColor separatorColor;


    public PlayerInterfaceService(MMOCore core) {
        this.core = core;
    }

    @Override
    public void initialize(MMOCore core) {
        this.plugin = core.getPlugin();
        this.logging = core.getService(LoggingService.class);
        this.playerStatsService = core.getService(PlayerStatsService.class);
        this.playerResourceService = core.getService(PlayerResourceService.class);
        ConfigService configService = core.getService(ConfigService.class);

        loadConfigSettings(configService.getMainConfig()); // Initial load

        // Subscribe to config reloads
        configService.subscribeToReload(ConfigService.MAIN_CONFIG_FILENAME, this::loadConfigSettings);

        if (actionBarEnabled && actionBarIntervalTicks > 0) {
            startGlobalActionBarUpdateTask();
        }
        logging.info(getServiceName() + " initialized. Action Bar Updates: " + actionBarEnabled);
    }

    private void loadConfigSettings(FileConfiguration config) {
        boolean oldEnabled = actionBarEnabled;
        long oldInterval = actionBarIntervalTicks;

        actionBarEnabled = config.getBoolean("player.interface.action_bar.enabled", true);
        actionBarIntervalTicks = config.getLong("player.interface.action_bar.interval_ticks", 20L);
        if (actionBarIntervalTicks <= 0) actionBarIntervalTicks = 20L; // Sanitize

        // Example for configurable colors (add these to config.yml if you want them)
        // healthColorVal = TextColor.fromHexString(config.getString("player.interface.action_bar.colors.health_value", "#FF5555")); // Bright Red
        // healthColorMax = TextColor.fromHexString(config.getString("player.interface.action_bar.colors.health_max", "#AA0000"));   // Dark Red
        // manaColorVal = TextColor.fromHexString(config.getString("player.interface.action_bar.colors.mana_value", "#5555FF"));     // Bright Blue
        // manaColorMax = TextColor.fromHexString(config.getString("player.interface.action_bar.colors.mana_max", "#0000AA"));       // Dark Blue
        // separatorColor = TextColor.fromHexString(config.getString("player.interface.action_bar.colors.separator", "#555555"));   // Dark Gray

        // For Part 1, use fixed colors:
        healthColorVal = NamedTextColor.RED;
        healthColorMax = NamedTextColor.DARK_RED;
        manaColorVal = NamedTextColor.AQUA;
        manaColorMax = NamedTextColor.BLUE;
        separatorColor = NamedTextColor.DARK_GRAY;


        if (oldEnabled != actionBarEnabled || oldInterval != actionBarIntervalTicks) {
            if (globalActionBarUpdateTask != null) globalActionBarUpdateTask.cancel();
            if (actionBarEnabled && actionBarIntervalTicks > 0) {
                startGlobalActionBarUpdateTask();
            }
        }
    }


    private void startGlobalActionBarUpdateTask() {
        if (globalActionBarUpdateTask != null && !globalActionBarUpdateTask.isCancelled()) {
            globalActionBarUpdateTask.cancel();
        }
        globalActionBarUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllPlayerActionBars();
            }
        }.runTaskTimer(plugin, 0L, actionBarIntervalTicks); // Initial delay 0, then repeat
        logging.debug("Global action bar update task started (Interval: " + actionBarIntervalTicks + " ticks).");
    }

    @Override
    public void shutdown() {
        if (globalActionBarUpdateTask != null && !globalActionBarUpdateTask.isCancelled()) {
            globalActionBarUpdateTask.cancel();
        }
        playersNeedingImmediateActionBarUpdate.clear();
        logging.info(getServiceName() + " shutdown complete.");
    }

    /**
     * Schedules an action bar update for a specific player on the next available tick.
     * This is useful after an immediate change to their resources (e.g., mana consumption).
     * @param player The player whose action bar should be updated.
     */
    public void scheduleActionBarUpdateForPlayer(Player player) {
        if (!actionBarEnabled || player == null || !player.isOnline()) return;

        // If already scheduled for immediate update, no need to add again.
        // This simple check might not be perfectly thread-safe if called async,
        // but for now, it's likely called from main thread service interactions.
        if (playersNeedingImmediateActionBarUpdate.contains(player.getUniqueId())) return;

        playersNeedingImmediateActionBarUpdate.add(player.getUniqueId());

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (playersNeedingImmediateActionBarUpdate.remove(player.getUniqueId())) {
                if (player.isOnline()) { // Re-check
                    sendActionBar(player);
                }
            }
        });
    }


    private void updateAllPlayerActionBars() {
        if (!actionBarEnabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOnline() && player.isValid()) { // Ensure player is still valid
                sendActionBar(player);
            }
        }
    }

    public void sendActionBar(Player player) {
        if (!actionBarEnabled || player == null || !player.isOnline() || !player.isValid()) return;

        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double currentHealth = player.getHealth();
        double currentMaxHealth = (maxHealthAttr != null) ? maxHealthAttr.getValue() : 20.0; // Bukkit's current max

        // CalculatedPlayerStats stats = playerStatsService.getCalculatedStats(player); // Not strictly needed if PlayerResourceService is current
        int currentMana = playerResourceService.getCurrentMana(player);
        // Get maxMana from PlayerStatsService as it's the authoritative source for the *calculated* max.
        int maxMana = playerStatsService.getCalculatedStats(player).maxMana();

        String healthStr = healthFormat.format(currentHealth);
        String maxHealthStr = healthFormat.format(currentMaxHealth);
        String manaStr = healthFormat.format(currentMana); // Use same format for mana
        String maxManaStr = healthFormat.format(maxMana);

        // Build the component
        Component healthComponent = Component.text("HP: ", NamedTextColor.GRAY)
                .append(Component.text(healthStr, healthColorVal))
                .append(Component.text("/", separatorColor))
                .append(Component.text(maxHealthStr, healthColorMax));

        Component manaComponent = Component.text("Mana: ", NamedTextColor.GRAY)
                .append(Component.text(manaStr, manaColorVal))
                .append(Component.text("/", separatorColor))
                .append(Component.text(maxManaStr, manaColorMax));

        Component separator = Component.text(" | ", separatorColor);

        try {
            player.sendActionBar(healthComponent.append(separator).append(manaComponent));
        } catch (Exception e) {
            // Catch potential errors if Adventure API isn't available or player disconnects mid-send
            logging.warnOnce("ActionBarSendFail_" + player.getName(),
                    "Could not send action bar to " + player.getName() + ". Error: " + e.getMessage() +
                            ". This message will only appear once per player per session to reduce spam.");
            // Optionally disable action bar for this player for the session if it fails repeatedly.
        }
    }
}