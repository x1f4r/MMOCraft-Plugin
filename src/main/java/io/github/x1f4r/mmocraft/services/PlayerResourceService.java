package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import io.github.x1f4r.mmocraft.player.PlayerProfile; // For initial mana setup on join
import io.github.x1f4r.mmocraft.player.stats.CalculatedPlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType; // For checking effects that might block regen
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerResourceService implements Service {
    private MMOCore core;
    private LoggingService logging;
    private PlayerStatsService playerStatsService; // To get max mana/health
    private ConfigService configService;
    private MMOCraft plugin; // For scheduling tasks

    // Manages current mana for players. Current health is obtained directly from Player.getHealth().
    private final Map<UUID, Integer> currentPlayerMana = new ConcurrentHashMap<>();

    private BukkitTask healthRegenTask;
    private BukkitTask manaRegenTask;

    // Cached configuration values for regeneration
    private boolean healthRegenEnabled;
    private double healthRegenPercentage;
    private double healthRegenFlatAmount;
    private long healthRegenIntervalTicks;

    private boolean manaRegenEnabled;
    private double manaRegenPercentage;
    private int manaRegenFlatAmount; // Mana is typically integer
    private long manaRegenIntervalTicks;

    public PlayerResourceService(MMOCore core) {
        this.core = core;
    }

    @Override
    public void initialize(MMOCore core) {
        this.plugin = core.getPlugin();
        this.logging = core.getService(LoggingService.class);
        this.playerStatsService = core.getService(PlayerStatsService.class);
        this.configService = core.getService(ConfigService.class);

        loadConfigSettings(); // Load initial settings
        startRegenerationTasks();

        // Subscribe to config reloads to update settings dynamically
        configService.subscribeToReload(ConfigService.MAIN_CONFIG_FILENAME, newConfig -> {
            logging.info("Reloading PlayerResourceService configuration settings...");
            boolean oldHealthEnabled = healthRegenEnabled;
            long oldHealthInterval = healthRegenIntervalTicks;
            boolean oldManaEnabled = manaRegenEnabled;
            long oldManaInterval = manaRegenIntervalTicks;

            loadConfigSettings(); // Reload values from the new config

            // Restart tasks if enabled status or intervals changed
            if (healthRegenEnabled != oldHealthEnabled || healthRegenIntervalTicks != oldHealthInterval) {
                if (healthRegenTask != null) healthRegenTask.cancel();
                if (healthRegenEnabled && healthRegenIntervalTicks > 0) startHealthRegenTask();
            }
            if (manaRegenEnabled != oldManaEnabled || manaRegenIntervalTicks != oldManaInterval) {
                if (manaRegenTask != null) manaRegenTask.cancel();
                if (manaRegenEnabled && manaRegenIntervalTicks > 0) startManaRegenTask();
            }
        });

        // Initialize mana for any players already online (e.g., on /reload)
        // PlayerDataService.handlePlayerJoin -> PlayerStatsService.handlePlayerJoin -> PlayerResourceService.handlePlayerJoin
        // This sequence ensures stats are calculated before mana is set.
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = core.getService(PlayerDataService.class).getProfile(player); // Ensure profile is loaded
            handlePlayerJoin(player, profile); // Initialize mana
        }

        logging.info(getServiceName() + " initialized. Health Regen: " + healthRegenEnabled + ", Mana Regen: " + manaRegenEnabled);
    }

    private void loadConfigSettings() {
        FileConfiguration mainCfg = configService.getMainConfig();
        healthRegenEnabled = mainCfg.getBoolean("player.regeneration.health.enabled", true);
        healthRegenPercentage = mainCfg.getDouble("player.regeneration.health.percentage_of_max_health", 0.01);
        healthRegenFlatAmount = mainCfg.getDouble("player.regeneration.health.flat_amount", 0.5);
        healthRegenIntervalTicks = mainCfg.getLong("player.regeneration.health.interval_ticks", 40L);
        if (healthRegenIntervalTicks <= 0) healthRegenIntervalTicks = 40L; // Sanitize

        manaRegenEnabled = mainCfg.getBoolean("player.regeneration.mana.enabled", true);
        manaRegenPercentage = mainCfg.getDouble("player.regeneration.mana.percentage_of_max_mana", 0.02);
        manaRegenFlatAmount = mainCfg.getInt("player.regeneration.mana.flat_amount", 1);
        manaRegenIntervalTicks = mainCfg.getLong("player.regeneration.mana.interval_ticks", 20L);
        if (manaRegenIntervalTicks <= 0) manaRegenIntervalTicks = 20L; // Sanitize
    }

    private void startRegenerationTasks() {
        startHealthRegenTask();
        startManaRegenTask();
    }

    private void startHealthRegenTask() {
        if (healthRegenTask != null && !healthRegenTask.isCancelled()) healthRegenTask.cancel();
        if (healthRegenEnabled && healthRegenIntervalTicks > 0) {
            healthRegenTask = new BukkitRunnable() {
                @Override
                public void run() {
                    runHealthRegenerationCycle();
                }
            }.runTaskTimer(plugin, healthRegenIntervalTicks, healthRegenIntervalTicks);
            logging.debug("Health regeneration task started (Interval: " + healthRegenIntervalTicks + " ticks).");
        }
    }

    private void startManaRegenTask() {
        if (manaRegenTask != null && !manaRegenTask.isCancelled()) manaRegenTask.cancel();
        if (manaRegenEnabled && manaRegenIntervalTicks > 0) {
            manaRegenTask = new BukkitRunnable() {
                @Override
                public void run() {
                    runManaRegenerationCycle();
                }
            }.runTaskTimer(plugin, manaRegenIntervalTicks, manaRegenIntervalTicks);
            logging.debug("Mana regeneration task started (Interval: " + manaRegenIntervalTicks + " ticks).");
        }
    }

    @Override
    public void shutdown() {
        if (healthRegenTask != null && !healthRegenTask.isCancelled()) healthRegenTask.cancel();
        if (manaRegenTask != null && !manaRegenTask.isCancelled()) manaRegenTask.cancel();
        currentPlayerMana.clear();
        logging.info(getServiceName() + " shutdown complete. Regeneration tasks stopped.");
    }

    /**
     * Called by PlayerDataService when a player joins and their profile is established.
     * @param player The player.
     * @param profile The player's profile (contains base max mana).
     */
    public void handlePlayerJoin(Player player, PlayerProfile profile) {
        // PlayerStatsService.handlePlayerJoin would have been called by PlayerDataService,
        // ensuring CalculatedPlayerStats (including maxMana) is up-to-date in cache.
        CalculatedPlayerStats stats = playerStatsService.getCalculatedStats(player);
        setCurrentMana(player, stats.maxMana()); // Initialize to full mana
        logging.debug("Initialized mana for " + player.getName() + " to " + stats.maxMana() + " on join.");
    }

    public void handlePlayerQuit(Player player) {
        currentPlayerMana.remove(player.getUniqueId());
        logging.debug("Cleared mana cache for quitting player: " + player.getName());
    }

    /**
     * Called by PlayerStatsService when a player's stats (especially max mana) are updated.
     * Ensures current mana does not exceed new max mana.
     * @param player The player.
     * @param newStats The newly calculated player stats.
     */
    public void handleStatsUpdate(Player player, CalculatedPlayerStats newStats) {
        int currentMana = getCurrentMana(player);
        if (currentMana > newStats.maxMana()) {
            setCurrentMana(player, newStats.maxMana()); // Clamp to new max
            logging.debug("Mana for " + player.getName() + " clamped to new max: " + newStats.maxMana());
        }
        // If max mana increased, current mana naturally stays, regen will fill it.
    }

    private void runHealthRegenerationCycle() {
        if (!healthRegenEnabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead() || !player.isValid()) continue;

            AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealthAttr == null) continue;

            double currentMaxHealth = maxHealthAttr.getValue();
            double currentHealth = player.getHealth();

            if (currentHealth < currentMaxHealth) {
                // Vanilla usually prevents regen during POISON/WITHER, but explicit check is safer.
                if (player.hasPotionEffect(PotionEffectType.POISON) || player.hasPotionEffect(PotionEffectType.WITHER)) {
                    continue;
                }
                // Future: Add a check if player is "in combat" (from CombatService) to pause regen.

                double regenFromPercentage = currentMaxHealth * healthRegenPercentage;
                double totalRegenThisTick = regenFromPercentage + healthRegenFlatAmount;

                if (totalRegenThisTick > 0) { // Only apply if there's actual regen
                    player.setHealth(Math.min(currentMaxHealth, currentHealth + totalRegenThisTick));
                    // logging.debug("Regenerated " + String.format("%.2f", totalRegenThisTick) + " health for " + player.getName());
                }
            }
        }
    }

    private void runManaRegenerationCycle() {
        if (!manaRegenEnabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead() || !player.isValid()) continue;

            CalculatedPlayerStats stats = playerStatsService.getCalculatedStats(player); // Get current stats
            int currentMana = getCurrentMana(player);
            int maxMana = stats.maxMana();

            if (maxMana <= 0) continue; // No mana to regenerate if max is zero or less

            if (currentMana < maxMana) {
                // Future: Add a check if player is "in combat" or recently used mana ability to pause regen.

                int regenFromPercentage = (int) Math.floor(maxMana * manaRegenPercentage);
                int totalRegenThisTick = regenFromPercentage + manaRegenFlatAmount;

                // Ensure at least 1 mana regens if configured values are positive but result in 0 due to floor/low max.
                if (totalRegenThisTick <= 0 && (manaRegenPercentage > 0.0 || manaRegenFlatAmount > 0)) {
                    totalRegenThisTick = 1;
                }

                if (totalRegenThisTick > 0) {
                    setCurrentMana(player, currentMana + totalRegenThisTick); // setCurrentMana handles clamping
                    // logging.debug("Regenerated " + totalRegenThisTick + " mana for " + player.getName() + ". New: " + getCurrentMana(player));
                }
            }
        }
    }

    public int getCurrentMana(Player player) {
        if (player == null) return 0;
        return currentPlayerMana.getOrDefault(player.getUniqueId(), 0);
    }

    public void setCurrentMana(Player player, int amount) {
        if (player == null) return;
        CalculatedPlayerStats stats = playerStatsService.getCalculatedStats(player);
        int maxMana = Math.max(0, stats.maxMana()); // Ensure maxMana isn't negative
        currentPlayerMana.put(player.getUniqueId(), Math.max(0, Math.min(maxMana, amount)));
        // Notify PlayerInterfaceService to update the action bar for this specific player
        core.getService(PlayerInterfaceService.class).scheduleActionBarUpdateForPlayer(player);
    }

    public void addMana(Player player, int amount) {
        if (player == null || amount <= 0) return;
        setCurrentMana(player, getCurrentMana(player) + amount);
    }

    /**
     * Attempts to consume mana from the player.
     * @param player The player.
     * @param amountToConsume The amount of mana to consume. Must be positive.
     * @return true if mana was successfully consumed, false otherwise (e.g., not enough mana).
     */
    public boolean consumeMana(Player player, int amountToConsume) {
        if (player == null) return false;
        if (amountToConsume <= 0) return true; // Consuming 0 or negative mana is always "successful" without change.

        int currentMana = getCurrentMana(player);
        if (currentMana >= amountToConsume) {
            setCurrentMana(player, currentMana - amountToConsume);
            logging.debug("Consumed " + amountToConsume + " mana from " + player.getName() + ". New mana: " + getCurrentMana(player));
            return true;
        }
        logging.debug(player.getName() + " failed to consume " + amountToConsume + " mana. Current: " + currentMana);
        return false;
    }
}