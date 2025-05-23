package io.github.x1f4r.mmocraft.player;

import io.github.x1f4r.mmocraft.core.MMOCore; // Added import
import io.github.x1f4r.mmocraft.services.ConfigService;
import io.github.x1f4r.mmocraft.services.LoggingService; // Added import
import org.bukkit.configuration.ConfigurationSection; // For reading default stats

import java.util.UUID;

/**
 * Holds a player's persistent base statistics and other profile-specific data.
 * This object is managed by PlayerDataService and primarily interacts with PersistenceService
 * to load/save its state using NBTService keys.
 */
public class PlayerProfile {
    private final UUID playerId;

    // Base stats - these are persisted via PlayerDataService using PersistenceService
    private int baseStrength;
    private int baseDefense;
    private int baseCritChance;    // Stored as integer (e.g., 5 for 5%)
    private int baseCritDamage;    // Stored as integer (e.g., 50 for +50% damage)
    private int baseMaxHealth;     // MMOCraft's own base, influences Bukkit's attribute
    private int baseMaxMana;
    private int baseSpeedPercent;  // Stored as a whole number percentage (e.g., 10 for +10%)
    private int baseMiningSpeed;
    private int baseForagingSpeed;
    private int baseFishingSpeed;
    private int baseShootingSpeed;

    /**
     * Constructor for a new or loaded player profile.
     * Initializes base stats with defaults fetched from the provided ConfigService.
     * These defaults are used if PersistenceService doesn't find existing data for the player.
     *
     * @param playerId The UUID of the player this profile belongs to.
     * @param configService The ConfigService instance to fetch default base stat values.
     */
    public PlayerProfile(UUID playerId, ConfigService configService) {
        this.playerId = playerId;

        // Fetch default base stats from config.yml (player_defaults.base_stats section)
        ConfigurationSection defaultStatsConfig = configService.getMainConfig().getConfigurationSection("player_defaults.base_stats"); // Changed call

        if (defaultStatsConfig != null) {
            this.baseStrength = defaultStatsConfig.getInt("strength", 0);
            this.baseDefense = defaultStatsConfig.getInt("defense", 0);
            this.baseCritChance = defaultStatsConfig.getInt("crit_chance", 5);
            this.baseCritDamage = defaultStatsConfig.getInt("crit_damage", 50);
            this.baseMaxHealth = defaultStatsConfig.getInt("max_health", 20);
            this.baseMaxMana = defaultStatsConfig.getInt("max_mana", 100);
            this.baseSpeedPercent = defaultStatsConfig.getInt("speed", 0);
            this.baseMiningSpeed = defaultStatsConfig.getInt("mining_speed", 0);
            this.baseForagingSpeed = defaultStatsConfig.getInt("foraging_speed", 0);
            this.baseFishingSpeed = defaultStatsConfig.getInt("fishing_speed", 0);
            this.baseShootingSpeed = defaultStatsConfig.getInt("shooting_speed", 0);
        } else {
            // Fallback defaults if the config section is missing (shouldn't happen with default config.yml)
            this.baseStrength = 0;
            this.baseDefense = 0;
            this.baseCritChance = 5;
            this.baseCritDamage = 50;
            this.baseMaxHealth = 20;
            this.baseMaxMana = 100;
            this.baseSpeedPercent = 0;
            this.baseMiningSpeed = 0;
            this.baseForagingSpeed = 0;
            this.baseFishingSpeed = 0;
            this.baseShootingSpeed = 0;
            // Log a warning if ConfigService is available (it should be by the time PlayerDataService creates this)
            MMOCraft instance = io.github.x1f4r.mmocraft.MMOCraft.getInstance();
            MMOCore core = (instance != null) ? instance.getCore() : null;
            if (core != null) {
                LoggingService logging = core.getService(LoggingService.class);
                if (logging != null) {
                    logging.warn("PlayerProfile: 'player_defaults.base_stats' section missing in config.yml. Using hardcoded defaults.");
                } else {
                    MMOCraft.getPluginLogger().warning("PlayerProfile: 'player_defaults.base_stats' section missing in config.yml. Using hardcoded defaults.");
                }
            } else {
                MMOCraft.getPluginLogger().warning("PlayerProfile: 'player_defaults.base_stats' section missing in config.yml. Using hardcoded defaults.");
            }
        }
    }

    // --- Getters ---
    public UUID getPlayerId() { return playerId; }
    public int getBaseStrength() { return baseStrength; }
    public int getBaseDefense() { return baseDefense; }
    public int getBaseCritChance() { return baseCritChance; }
    public int getBaseCritDamage() { return baseCritDamage; }
    public int getBaseMaxHealth() { return baseMaxHealth; }
    public int getBaseMaxMana() { return baseMaxMana; }
    public int getBaseSpeedPercent() { return baseSpeedPercent; }
    public int getBaseMiningSpeed() { return baseMiningSpeed; }
    public int getBaseForagingSpeed() { return baseForagingSpeed; }
    public int getBaseFishingSpeed() { return baseFishingSpeed; }
    public int getBaseShootingSpeed() { return baseShootingSpeed; }

    // --- Setters (used by PlayerDataService when loading from PDC or by admin commands) ---
    // These setters include basic validation/clamping where appropriate.
    public void setBaseStrength(int baseStrength) { this.baseStrength = baseStrength; }
    public void setBaseDefense(int baseDefense) { this.baseDefense = Math.max(0, baseDefense); } // Defense typically non-negative
    public void setBaseCritChance(int baseCritChance) { this.baseCritChance = Math.max(0, Math.min(100, baseCritChance)); } // Clamp 0-100%
    public void setBaseCritDamage(int baseCritDamage) { this.baseCritDamage = Math.max(0, baseCritDamage); } // Crit damage non-negative
    public void setBaseMaxHealth(int baseMaxHealth) { this.baseMaxHealth = Math.max(1, baseMaxHealth); } // Min 1 max health
    public void setBaseMaxMana(int baseMaxMana) { this.baseMaxMana = Math.max(0, baseMaxMana); } // Min 0 max mana (though 1 is often better)
    public void setBaseSpeedPercent(int baseSpeedPercent) { this.baseSpeedPercent = baseSpeedPercent; } // Can be negative for slowness
    public void setBaseMiningSpeed(int baseMiningSpeed) { this.baseMiningSpeed = Math.max(0, baseMiningSpeed); }
    public void setBaseForagingSpeed(int baseForagingSpeed) { this.baseForagingSpeed = Math.max(0, baseForagingSpeed); }
    public void setBaseFishingSpeed(int baseFishingSpeed) { this.baseFishingSpeed = Math.max(0, baseFishingSpeed); }
    public void setBaseShootingSpeed(int baseShootingSpeed) { this.baseShootingSpeed = Math.max(0, baseShootingSpeed); }

    @Override
    public String toString() {
        return "PlayerProfile{" +
                "playerId=" + playerId +
                ", STR=" + baseStrength +
                ", DEF=" + baseDefense +
                ", CC=" + baseCritChance + "%" +
                ", CD=" + baseCritDamage + "%" +
                ", HP=" + baseMaxHealth +
                ", MP=" + baseMaxMana +
                ", SPD%=" + baseSpeedPercent +
                // Add other stats if needed for quick debug
                '}';
    }
}