package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.constants.MMOConstants;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ConfigService implements Service {
    private final MMOCraft plugin;
    private MMOCore core; // Reference to MMOCore for service access
    private LoggingService logging; // Injected by MMOCore

    public static final String MAIN_CONFIG_FILENAME = MMOConstants.Config.MAIN;
    public static final String ITEMS_CONFIG_FILENAME = MMOConstants.Config.ITEMS;
    public static final String MOBS_CONFIG_FILENAME = MMOConstants.Config.MOBS;
    public static final String RECIPES_CONFIG_FILENAME = MMOConstants.Config.RECIPES;
    public static final String CUSTOM_MOBS_CONFIG_FILENAME = MMOConstants.Config.CUSTOM_MOBS;
    public static final String LOOT_TABLES_CONFIG_FILENAME = MMOConstants.Config.LOOT_TABLES;
    // Add other config filenames as constants when they are introduced

    private final Map<String, FileConfiguration> loadedConfigs = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<FileConfiguration>>> reloadSubscribers = new ConcurrentHashMap<>();
    private final Map<String, File> configFiles = new ConcurrentHashMap<>();


    public ConfigService(MMOCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize(MMOCore core) {
        this.core = core; // Store reference to MMOCore
        this.logging = core.getService(LoggingService.class);

        // Load main config first as other initializations might depend on it (e.g., LoggingService debug mode)
        loadAndManage(MAIN_CONFIG_FILENAME, true);

        // Initialize LoggingService's config-dependent settings now that main config is loaded
        // This is a bit of a special case due to LoggingService's role.
        LoggingService actualLoggingService = core.getService(LoggingService.class); // Get the instance
        if (actualLoggingService != null) { // Should not be null
            actualLoggingService.reloadConfigSettings(); // Tell it to read its settings from the now-loaded config
        }

        // Subscribe LoggingService to future reloads of main config (for debug_mode changes)
        // This specific subscription is handled in Part 7 theoretically, but good to note here.
        // For now, `LoggingService.reloadConfigSettings()` is called by ConfigService directly if needed.


        // Load other primary configs that are expected to exist or have defaults
        // These will be used by services initialized after ConfigService
        loadAndManage(ITEMS_CONFIG_FILENAME, true);
        loadAndManage(MOBS_CONFIG_FILENAME, true);
        loadAndManage(RECIPES_CONFIG_FILENAME, true);
        loadAndManage(CUSTOM_MOBS_CONFIG_FILENAME, true);
        loadAndManage(LOOT_TABLES_CONFIG_FILENAME, true);

        logging.info(getServiceName() + " initialized. Managed configs: " + String.join(", ", configFiles.keySet()));
    }

    @Override
    public void shutdown() {
        loadedConfigs.clear();
        reloadSubscribers.clear();
        configFiles.clear();
        logging.info(getServiceName() + " shutdown complete.");
    }

    /**
     * Gets the main configuration file (config.yml).
     * @return The FileConfiguration for config.yml.
     */
    public FileConfiguration getMainConfig() {
        return getConfig(MAIN_CONFIG_FILENAME);
    }

    /**
     * Checks if a configuration file name (e.g., "items.yml") is currently managed by this service.
     * Normalizes the input name to lowercase and ensures .yml extension for comparison.
     * @param configFileName The simple name of the config file.
     * @return true if managed (i.e., was loaded via loadAndManage), false otherwise.
     */
    public boolean isManagedConfig(@NotNull String configFileName) {
        String normalizedKey = configFileName.toLowerCase();
        if (!normalizedKey.endsWith(".yml")) {
            normalizedKey += ".yml";
        }
        return configFiles.containsKey(normalizedKey); // configFiles stores keys like "config.yml"
    }

    /**
     * Gets a list of currently managed configuration file names, primarily for user display
     * (e.g., in tab completion for a reload command). Returns names without the .yml extension.
     * @return An unmodifiable, sorted list of config names (e.g., "config", "items").
     */
    @NotNull
    public List<String> getManagedConfigFileNames() {
        return Collections.unmodifiableList(
                configFiles.keySet().stream() // Keys are like "config.yml"
                        .map(name -> name.endsWith(".yml") ? name.substring(0, name.length() - 4) : name)
                        .sorted()
                        .collect(Collectors.toList())
        );
    }

    /**
     * Loads a configuration file by its name. If it doesn't exist and
     * saveDefaultIfNeeded is true, it attempts to save the default from JAR.
     *
     * @param configFileName The name of the config file (e.g., "config.yml").
     * @param saveDefaultIfNeeded If true, save default from JAR if file doesn't exist.
     */
    public void loadAndManage(String configFileName, boolean saveDefaultIfNeeded) {
        File configFile = new File(plugin.getDataFolder(), configFileName);
        configFiles.put(configFileName, configFile); // Store the file object

        if (!configFile.exists() && saveDefaultIfNeeded) {
            logging.info("Configuration file '" + configFileName + "' not found, creating default from JAR.");
            try {
                // Ensure the plugin data folder exists
                if (!plugin.getDataFolder().exists()) {
                    if (!plugin.getDataFolder().mkdirs()) {
                        logging.warn("Could not create plugin data folder: " + plugin.getDataFolder().getAbsolutePath());
                        // Continue, but saving resource might fail.
                    }
                }
                plugin.saveResource(configFileName, false); // false to not replace if it somehow exists at this point
            } catch (Exception e) {
                // This can happen if the resource is not in the JAR.
                logging.warn("Could not save default config '" + configFileName + "' from JAR: " + e.getMessage() +
                        ". A blank file might be created or it might need to be created manually.");
            }
        }

        YamlConfiguration config = new YamlConfiguration(); // Use YamlConfiguration
        if (configFile.exists()) { // Load only if it truly exists (saveResource might have failed)
            try {
                config.load(configFile);
            } catch (IOException | InvalidConfigurationException e) {
                logging.severe("Failed to load configuration file: " + configFile.getAbsolutePath(), e);
                // In case of a corrupted file, we might load defaults from JAR into memory
                // to allow the plugin to function with some defaults.
                logging.info("Attempting to load default '" + configFileName + "' into memory due to load failure.");
                try (InputStream defaultConfigStream = plugin.getResource(configFileName);
                     InputStreamReader reader = defaultConfigStream != null ? new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8) : null) {
                    if (reader != null) {
                        config.load(reader);
                        logging.info("Successfully loaded default '" + configFileName + "' into memory.");
                    } else {
                        logging.warn("Default resource '" + configFileName + "' not found in JAR for fallback loading.");
                    }
                } catch (IOException | InvalidConfigurationException | NullPointerException ex) {
                    logging.severe("Failed to load default '" + configFileName + "' from JAR into memory as fallback.", ex);
                    // config remains an empty YamlConfiguration
                }
            }
        } else {
            logging.warn("Configuration file '" + configFileName + "' does not exist and default was not/could not be saved. Using empty configuration.");
        }
        // Validate configuration before storing
        validateConfiguration(configFileName, config);
        
        loadedConfigs.put(configFileName, config);
        logging.debug("Managed configuration: " + configFileName + " (Path: " + configFile.getAbsolutePath() + ")");
    }

    /**
     * Gets a loaded FileConfiguration by its filename.
     * If the config is not yet managed, it will attempt to load it.
     * @param configFileName The name of the config file.
     * @return The FileConfiguration, or an empty one if loading fails.
     */
    public FileConfiguration getConfig(String configFileName) {
        FileConfiguration config = loadedConfigs.get(configFileName);
        if (config == null) {
            // This indicates the config wasn't loaded during initialize() or a service is asking for an unmanaged config.
            logging.warn("Attempted to access non-managed or unloaded config: " + configFileName +
                    ". Attempting to load it now with default save if needed.");
            loadAndManage(configFileName, true); // Attempt to load it on demand
            config = loadedConfigs.get(configFileName); // Get it after loading
            if (config == null) { // Should not happen if loadAndManage is robust
                logging.severe("Failed to load " + configFileName + " on demand even after attempt. Returning new empty config to prevent NPE.");
                return new YamlConfiguration(); // Return new empty config
            }
        }
        return config;
    }

    /**
     * Subscribes a callback to be executed when a specific configuration file is reloaded.
     * @param configFileName The name of the config file to subscribe to.
     * @param callback The Consumer to execute with the reloaded FileConfiguration.
     */
    public void subscribeToReload(String configFileName, Consumer<FileConfiguration> callback) {
        reloadSubscribers.computeIfAbsent(configFileName, k -> new ArrayList<>()).add(callback);
        logging.debug("Service/Component subscribed to reload events for: " + configFileName);
    }

    /**
     * Reloads a specific configuration file from disk and notifies subscribers.
     * @param configFileName The name of the config file to reload.
     * @return true if successfully reloaded, false otherwise.
     */
    public boolean reloadConfig(String configFileName) {
        File configFile = configFiles.get(configFileName);
        if (configFile == null || !configFile.exists()) {
            logging.warn("Cannot reload '" + configFileName + "': File not managed by ConfigService or does not exist on disk.");
            // Optional: try to load it as if it's new, which might create a default if saveDefaultIfNeeded was true initially
            // loadAndManage(configFileName, true); // This might be too aggressive for a simple reload call
            return false;
        }

        YamlConfiguration newConfig = new YamlConfiguration();
        try {
            newConfig.load(configFile);
            loadedConfigs.put(configFileName, newConfig); // Update cached config
            logging.info("Successfully reloaded configuration from disk: " + configFileName);

            // Notify subscribers
            List<Consumer<FileConfiguration>> subscribers = reloadSubscribers.get(configFileName);
            if (subscribers != null && !subscribers.isEmpty()) {
                logging.debug("Notifying " + subscribers.size() + " subscribers about reload of " + configFileName);
                final FileConfiguration effectivelyFinalConfig = newConfig; // For use in lambda
                subscribers.forEach(sub -> {
                    try {
                        sub.accept(effectivelyFinalConfig);
                    } catch (Exception e) {
                        logging.severe("Error in subscriber while processing reload for " + configFileName, e);
                        // Continue notifying other subscribers
                    }
                });
            }

            // Specific handling for main config reload affecting LoggingService debug mode
            if (MAIN_CONFIG_FILENAME.equals(configFileName)) {
                LoggingService ls = core.getService(LoggingService.class); // Ensure we get the service instance from core
                if (ls != null) {
                    ls.reloadConfigSettings();
                }
            }
            return true;
        } catch (IOException | InvalidConfigurationException e) {
            logging.severe("Failed to reload configuration file from disk: " + configFileName, e);
            // Keep the old config in memory if reload fails to avoid breaking services
            return false;
        }
    }

    /**
     * Reloads all currently managed configuration files.
     */
    public void reloadAllConfigs() {
        logging.info("Reloading all managed configurations...");
        // Iterate over a copy of keys in case a reload triggers further config interactions (unlikely here but good practice)
        new ArrayList<>(configFiles.keySet()).forEach(this::reloadConfig);
        logging.info("All configurations reload attempt finished.");
    }

    // --- Convenience Typed Getters for Main Config (config.yml) ---
    // These are examples; specific services might access their own configs directly
    // or you can add more generic typed getters for any config file.

    public String getMainConfigString(String path, String def) {
        return getMainConfig().getString(path, def);
    }
    public int getMainConfigInt(String path, int def) {
        return getMainConfig().getInt(path, def);
    }
    public double getMainConfigDouble(String path, double def) {
        return getMainConfig().getDouble(path, def);
    }
    public boolean getMainConfigBoolean(String path, boolean def) {
        return getMainConfig().getBoolean(path, def);
    }
    public List<String> getMainConfigStringList(String path) {
        return getMainConfig().getStringList(path);
    }

    // Default messages - can be overridden by config.yml
    private static final String DEFAULT_NO_PERMISSION = "You do not have permission to use this command.";
    private static final String DEFAULT_CONSOLE_NOT_ALLOWED = "This command cannot be run from the console.";
    private static final String DEFAULT_COMMAND_ERROR = MMOConstants.Messages.DEFAULT_COMMAND_ERROR;

    public String getNoPermissionMessage() {
        return getMainConfig().getString("messages.no_permission", DEFAULT_NO_PERMISSION);
    }

    public String getConsoleNotAllowedMessage() {
        return getMainConfig().getString("messages.console_not_allowed", DEFAULT_CONSOLE_NOT_ALLOWED);
    }

    public String getCommandErrorMessage() {
        return getMainConfig().getString("messages.command_error", DEFAULT_COMMAND_ERROR);
    }

    /**
     * Validates configuration values to ensure they are within acceptable ranges
     * and contain required fields.
     * @param configFileName The name of the configuration file being validated
     * @param config The configuration to validate
     */
    private void validateConfiguration(String configFileName, FileConfiguration config) {
        try {
            switch (configFileName) {
                case MAIN_CONFIG_FILENAME:
                    validateMainConfig(config);
                    break;
                case ITEMS_CONFIG_FILENAME:
                    validateItemsConfig(config);
                    break;
                case MOBS_CONFIG_FILENAME:
                case CUSTOM_MOBS_CONFIG_FILENAME:
                    validateMobsConfig(config);
                    break;
                case RECIPES_CONFIG_FILENAME:
                    validateRecipesConfig(config);
                    break;
                case LOOT_TABLES_CONFIG_FILENAME:
                    validateLootTablesConfig(config);
                    break;
                default:
                    logging.debug("No specific validation rules for config: " + configFileName);
            }
        } catch (Exception e) {
            logging.warn("Error during configuration validation for " + configFileName, e);
        }
    }
    
    private void validateMainConfig(FileConfiguration config) {
        // Validate debug mode
        if (!config.contains("debug")) {
            logging.warn("Main config missing 'debug' setting, using default: false");
        }
        
        // Validate messages section
        if (!config.contains("messages")) {
            logging.warn("Main config missing 'messages' section, using defaults");
        }
        
        // Validate player defaults
        if (config.contains("player_defaults.base_stats")) {
            validateStatValues(config, "player_defaults.base_stats");
        } else {
            logging.warn("Main config missing 'player_defaults.base_stats' section");
        }
    }
    
    private void validateItemsConfig(FileConfiguration config) {
        if (config.getConfigurationSection("items") == null) {
            logging.warn("Items config missing 'items' section");
            return;
        }
        
        for (String itemKey : config.getConfigurationSection("items").getKeys(false)) {
            String path = "items." + itemKey;
            
            // Validate required fields
            if (!config.contains(path + ".material")) {
                logging.warn("Item '" + itemKey + "' missing required 'material' field");
            }
            
            // Validate stat bonuses if present
            if (config.contains(path + ".stats")) {
                validateStatValues(config, path + ".stats");
            }
        }
    }
    
    private void validateMobsConfig(FileConfiguration config) {
        if (config.getConfigurationSection("mobs") == null) {
            logging.warn("Mobs config missing 'mobs' section");
            return;
        }
        
        for (String mobKey : config.getConfigurationSection("mobs").getKeys(false)) {
            String path = "mobs." + mobKey;
            
            // Validate health
            double health = config.getDouble(path + ".health", 20.0);
            if (health <= 0 || health > 2048) {
                logging.warn("Mob '" + mobKey + "' has invalid health value: " + health + " (should be 1-2048)");
            }
            
            // Validate damage
            double damage = config.getDouble(path + ".damage", 1.0);
            if (damage < 0 || damage > 100) {
                logging.warn("Mob '" + mobKey + "' has invalid damage value: " + damage + " (should be 0-100)");
            }
        }
    }
    
    private void validateRecipesConfig(FileConfiguration config) {
        if (config.getConfigurationSection("recipes") == null) {
            logging.warn("Recipes config missing 'recipes' section");
            return;
        }
        
        for (String recipeKey : config.getConfigurationSection("recipes").getKeys(false)) {
            String path = "recipes." + recipeKey;
            
            if (!config.contains(path + ".result")) {
                logging.warn("Recipe '" + recipeKey + "' missing required 'result' field");
            }
            
            if (!config.contains(path + ".ingredients")) {
                logging.warn("Recipe '" + recipeKey + "' missing required 'ingredients' field");
            }
        }
    }
    
    private void validateLootTablesConfig(FileConfiguration config) {
        if (config.getConfigurationSection("loot_tables") == null) {
            logging.warn("Loot tables config missing 'loot_tables' section");
            return;
        }
        
        for (String tableKey : config.getConfigurationSection("loot_tables").getKeys(false)) {
            String path = "loot_tables." + tableKey;
            
            if (!config.contains(path + ".entries")) {
                logging.warn("Loot table '" + tableKey + "' missing required 'entries' field");
            }
        }
    }
    
    private void validateStatValues(FileConfiguration config, String basePath) {
        String[] statNames = {"maxHealth", "maxMana", "strength", "defense", "intelligence", 
                             "speed", "critChance", "critDamage", "speedPercent", "miningSpeed", 
                             "foragingSpeed", "fishingSpeed", "shootingSpeed"};
        
        for (String stat : statNames) {
            if (config.contains(basePath + "." + stat)) {
                double value = config.getDouble(basePath + "." + stat);
                
                // Basic range validation
                if (stat.equals("critChance") && (value < 0 || value > 100)) {
                    logging.warn("Invalid " + stat + " value: " + value + " (should be 0-100)");
                } else if (stat.endsWith("Percent") && (value < 0 || value > 1000)) {
                    logging.warn("Invalid " + stat + " value: " + value + " (should be 0-1000)");
                } else if (value < 0) {
                    logging.warn("Invalid " + stat + " value: " + value + " (should be non-negative)");
                }
            }
        }
    }}
