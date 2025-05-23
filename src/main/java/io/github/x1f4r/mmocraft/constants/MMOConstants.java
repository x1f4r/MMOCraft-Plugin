package io.github.x1f4r.mmocraft.constants;

/**
 * Centralized constants for the MMOCraft plugin.
 * Contains commonly used values, limits, and configuration keys.
 */
public final class MMOConstants {
    
    // Prevent instantiation
    private MMOConstants() {}
    
    /**
     * Plugin metadata and identification
     */
    public static final class Plugin {
        public static final String NAME = "MMOCraft";
        public static final String PREFIX = "[MMOCraft]";
        public static final String PERMISSION_PREFIX = "mmocraft.";
    }
    
    /**
     * Configuration file names
     */
    public static final class Config {
        public static final String MAIN = "config.yml";
        public static final String ITEMS = "items.yml";
        public static final String MOBS = "mobs.yml";
        public static final String CUSTOM_MOBS = "custom_mobs.yml";
        public static final String RECIPES = "recipes.yml";
        public static final String LOOT_TABLES = "loot_tables.yml";
    }
    
    /**
     * Player statistics limits and defaults
     */
    public static final class Stats {
        // Health limits
        public static final double MIN_HEALTH = 1.0;
        public static final double MAX_HEALTH = 2048.0;
        public static final double DEFAULT_HEALTH = 20.0;
        
        // Percentage limits
        public static final double MIN_PERCENT = 0.0;
        public static final double MAX_PERCENT = 1000.0;
        public static final double MAX_CRIT_CHANCE = 100.0;
        
        // Damage limits
        public static final double MIN_DAMAGE = 0.0;
        public static final double MAX_DAMAGE = 100.0;
        public static final double DEFAULT_DAMAGE = 1.0;
        
        // Stat names
        public static final String MAX_HEALTH = "maxHealth";
        public static final String MAX_MANA = "maxMana";
        public static final String STRENGTH = "strength";
        public static final String DEFENSE = "defense";
        public static final String INTELLIGENCE = "intelligence";
        public static final String SPEED = "speed";
        public static final String CRIT_CHANCE = "critChance";
        public static final String CRIT_DAMAGE = "critDamage";
        public static final String SPEED_PERCENT = "speedPercent";
        public static final String MINING_SPEED = "miningSpeed";
        public static final String FORAGING_SPEED = "foragingSpeed";
        public static final String FISHING_SPEED = "fishingSpeed";
        public static final String SHOOTING_SPEED = "shootingSpeed";
    }
    
    /**
     * Attribute modifier names for Bukkit attributes
     */
    public static final class Modifiers {
        public static final String MMO_MAX_HEALTH = "mmoc_max_health";
        public static final String MMO_SPEED = "mmoc_speed";
    }
    
    /**
     * Cache and performance limits
     */
    public static final class Performance {
        public static final int MAX_CACHE_SIZE = 1000;
        public static final long CACHE_CLEANUP_INTERVAL_TICKS = 12000L; // 10 minutes
        public static final long STAT_UPDATE_DELAY_TICKS = 2L;
        public static final int AI_UPDATE_INTERVAL_TICKS = 20; // 1 second
    }
    
    /**
     * Default configuration paths
     */
    public static final class ConfigPaths {
        public static final String DEBUG = "debug";
        public static final String MESSAGES = "messages";
        public static final String PLAYER_DEFAULTS = "player_defaults";
        public static final String BASE_STATS = "player_defaults.base_stats";
        public static final String COMMAND_ERROR = "messages.command_error";
        public static final String ITEMS_SECTION = "items";
        public static final String MOBS_SECTION = "mobs";
        public static final String RECIPES_SECTION = "recipes";
        public static final String LOOT_TABLES_SECTION = "loot_tables";
    }
    
    /**
     * Default messages
     */
    public static final class Messages {
        public static final String DEFAULT_COMMAND_ERROR = "An error occurred while executing the command.";
        public static final String PLAYER_NOT_FOUND = "Player not found.";
        public static final String INVALID_ARGUMENTS = "Invalid arguments.";
        public static final String NO_PERMISSION = "You don't have permission to use this command.";
    }
    
    /**
     * NBT and item data keys
     */
    public static final class NBTKeys {
        public static final String CUSTOM_ITEM = "mmo_custom_item";
        public static final String ITEM_STATS = "mmo_item_stats";
        public static final String CUSTOM_MOB = "mmo_custom_mob";
    }
}