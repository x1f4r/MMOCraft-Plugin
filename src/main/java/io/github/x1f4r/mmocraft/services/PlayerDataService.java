package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import io.github.x1f4r.mmocraft.player.PlayerProfile;
import io.github.x1f4r.mmocraft.player.listeners.PlayerDataListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataService implements Service {
    private MMOCore core; // To access other services
    private LoggingService logging;
    private PersistenceService persistenceService;
    private NBTService nbtService; // For Player PDC keys
    private ConfigService configService; // For PlayerProfile default values

    // Cache of PlayerProfile objects for online players
    private final Map<UUID, PlayerProfile> profileCache = new ConcurrentHashMap<>();

    /**
     * Constructor for PlayerDataService.
     * Dependencies are injected by MMOCore during its initialization phase.
     * @param core The MMOCore instance.
     */
    public PlayerDataService(MMOCore core) {
        this.core = core;
    }

    @Override
    public void initialize(MMOCore core) {
        // MMOCore passes itself, but dependencies are fetched via getService
        this.logging = core.getService(LoggingService.class);
        this.persistenceService = core.getService(PersistenceService.class);
        this.nbtService = core.getService(NBTService.class); // Ensure NBTService is initialized
        this.configService = core.getService(ConfigService.class); // For PlayerProfile defaults

        // Register the listener that calls handlePlayerJoin/Quit
        core.registerListener(new PlayerDataListener(this));
        logging.info(getServiceName() + " initialized.");

        // Load profiles for any players already online (e.g., on /reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            handlePlayerJoin(player); // Will load/create profile and notify other services
        }
    }

    @Override
    public void shutdown() {
        logging.info("Saving all cached player profiles on " + getServiceName() + " shutdown...");
        profileCache.forEach((uuid, profile) -> {
            Player player = Bukkit.getPlayer(uuid); // Get player object by UUID
            if (player != null && player.isOnline()) {
                savePlayerProfile(player, profile);
            } else {
                // For truly offline players, saving directly to Player PDC is not possible.
                // This data would be saved on quit. If server crashes, it might be lost
                // unless a more robust periodic save or database system is in place.
                logging.warn("Player " + uuid + " not online during PlayerDataService shutdown. " +
                        "Their profile data should have been saved on quit.");
            }
        });
        profileCache.clear();
        logging.info(getServiceName() + " shutdown complete. Profile cache cleared.");
    }

    /**
     * Retrieves the PlayerProfile for a given player.
     * If the profile is not in the cache, it attempts to load it from persistence
     * or creates a new one with default values.
     * This is the primary method other services should use to access player-specific base data.
     *
     * @param player The player whose profile is requested.
     * @return The PlayerProfile; never null (returns a default-initialized profile if new).
     * @throws IllegalArgumentException if the player is null.
     */
    public PlayerProfile getProfile(Player player) {
        if (player == null) {
            logging.severe("Attempted to get profile for a null player!");
            // Consider throwing an exception or returning a specific "GHOST_PROFILE"
            throw new IllegalArgumentException("Cannot get profile for null player.");
        }
        // computeIfAbsent ensures atomic load/creation if not present
        return profileCache.computeIfAbsent(player.getUniqueId(), uuid -> loadOrCreatePlayerProfile(player));
    }

    /**
     * Handles player join logic: loads their profile and notifies dependent services.
     * Called by {@link PlayerDataListener}.
     * @param player The player who joined.
     */
    public void handlePlayerJoin(Player player) {
        logging.debug("Handling player join for: " + player.getName() + " (UUID: " + player.getUniqueId() + ")");
        PlayerProfile profile = getProfile(player); // Ensures profile is loaded or created and cached

        // Notify PlayerStatsService to perform initial stat calculation for the joined player
        core.getService(PlayerStatsService.class).handlePlayerJoin(player);

        // Notify PlayerResourceService to initialize current mana based on the loaded profile's max mana
        // (PlayerResourceService will get the CalculatedPlayerStats which includes maxMana from PlayerStatsService)
        core.getService(PlayerResourceService.class).handlePlayerJoin(player, profile);
    }

    /**
     * Handles player quit logic: saves their profile and cleans up caches.
     * Called by {@link PlayerDataListener}.
     * @param player The player who quit.
     */
    public void handlePlayerQuit(Player player) {
        logging.debug("Handling player quit for: " + player.getName() + " (UUID: " + player.getUniqueId() + ")");
        PlayerProfile profile = profileCache.get(player.getUniqueId());
        if (profile != null) {
            savePlayerProfile(player, profile); // Save before removing from cache
        } else {
            logging.warn("No profile found in cache for quitting player: " + player.getName() + ". Data might not have been loaded or was already cleared.");
        }
        profileCache.remove(player.getUniqueId());

        // Notify PlayerStatsService for cleanup (e.g., remove attribute modifiers)
        core.getService(PlayerStatsService.class).handlePlayerQuit(player);
        // Notify PlayerResourceService for cleanup (e.g., remove mana cache)
        core.getService(PlayerResourceService.class).handlePlayerQuit(player);
    }


    private PlayerProfile loadOrCreatePlayerProfile(Player player) {
        logging.debug("Loading or creating profile for: " + player.getName());
        // Create a new profile instance; it will be initialized with defaults from config.
        PlayerProfile profile = new PlayerProfile(player.getUniqueId(), configService);

        // Attempt to load each base stat from the player's PersistentDataContainer (PDC).
        // If a stat is found in PDC, it overwrites the default value set by PlayerProfile constructor.
        profile.setBaseStrength(persistenceService.loadDataFromPlayerPDC(player, NBTService.PLAYER_BASE_STRENGTH, PersistentDataType.INTEGER, profile.getBaseStrength()));
        profile.setBaseDefense(persistenceService.loadDataFromPlayerPDC(player, NBTService.PLAYER_BASE_DEFENSE, PersistentDataType.INTEGER, profile.getBaseDefense()));
        profile.setBaseCritChance(persistenceService.loadDataFromPlayerPDC(player, NBTService.PLAYER_BASE_CRIT_CHANCE, PersistentDataType.INTEGER, profile.getBaseCritChance()));
        profile.setBaseCritDamage(persistenceService.loadDataFromPlayerPDC(player, NBTService.PLAYER_BASE_CRIT_DAMAGE, PersistentDataType.INTEGER, profile.getBaseCritDamage()));
        profile.setBaseMaxHealth(persistenceService.loadDataFromPlayerPDC(player, NBTService.PLAYER_BASE_MAX_HEALTH, PersistentDataType.INTEGER, profile.getBaseMaxHealth()));
        profile.setBaseMaxMana(persistenceService.loadDataFromPlayerPDC(player, NBTService.PLAYER_BASE_MAX_MANA, PersistentDataType.INTEGER, profile.getBaseMaxMana()));
        profile.setBaseSpeedPercent(persistenceService.loadDataFromPlayerPDC(player, NBTService.PLAYER_BASE_SPEED_PERCENT, PersistentDataType.INTEGER, profile.getBaseSpeedPercent()));
        profile.setBaseMiningSpeed(persistenceService.loadDataFromPlayerPDC(player, NBTService.PLAYER_BASE_MINING_SPEED, PersistentDataType.INTEGER, profile.getBaseMiningSpeed()));
        profile.setBaseForagingSpeed(persistenceService.loadDataFromPlayerPDC(player, NBTService.PLAYER_BASE_FORAGING_SPEED, PersistentDataType.INTEGER, profile.getBaseForagingSpeed()));
        profile.setBaseFishingSpeed(persistenceService.loadDataFromPlayerPDC(player, NBTService.PLAYER_BASE_FISHING_SPEED, PersistentDataType.INTEGER, profile.getBaseFishingSpeed()));
        profile.setBaseShootingSpeed(persistenceService.loadDataFromPlayerPDC(player, NBTService.PLAYER_BASE_SHOOTING_SPEED, PersistentDataType.INTEGER, profile.getBaseShootingSpeed()));

        // If this player is truly new (e.g., no MAX_MANA key found in their PDC),
        // it implies their profile was just created with defaults. We should save these
        // defaults to their PDC now so they persist.
        // A simple check: if a key like PLAYER_BASE_MAX_MANA was not present before loadDataFromPlayerPDC,
        // it means the default from PlayerProfile constructor was used.
        if (!persistenceService.hasDataInPlayerPDC(player, NBTService.PLAYER_BASE_MAX_MANA, PersistentDataType.INTEGER)) {
            // This check is a bit heuristic. A better way might be to check if *any* of our keys existed.
            // Or, if loadDataFromPlayerPDC returned the defaultValue for a known key.
            logging.info("New player profile initialized for " + player.getName() + ". Saving initial default stats to PDC.");
            savePlayerProfile(player, profile); // Save the newly created (default-filled) profile
        }

        logging.info("Profile ready for " + player.getName() + ": " + profile.toString());
        return profile;
    }

    /**
     * Saves the given PlayerProfile data to the player's PersistentDataContainer.
     * Should be called when base stats are modified (e.g., by admin command) or on player quit.
     * @param player The player whose profile to save.
     * @param profile The PlayerProfile object containing the data to save.
     */
    public void savePlayerProfile(Player player, PlayerProfile profile) {
        if (player == null || !player.isOnline()) {
            logging.warn("Attempted to save profile for null or offline player: " + (player != null ? player.getName() : "null player object"));
            return;
        }
        if (profile == null) {
            logging.warn("Attempted to save a null profile for player: " + player.getName());
            return;
        }

        if (logging.isDebugMode()) { // Avoid excessive logging if not debugging
            logging.debug("Saving profile for " + player.getName() + ": " + profile.toString());
        }

        persistenceService.saveDataToPlayerPDC(player, NBTService.PLAYER_BASE_STRENGTH, PersistentDataType.INTEGER, profile.getBaseStrength());
        persistenceService.saveDataToPlayerPDC(player, NBTService.PLAYER_BASE_DEFENSE, PersistentDataType.INTEGER, profile.getBaseDefense());
        persistenceService.saveDataToPlayerPDC(player, NBTService.PLAYER_BASE_CRIT_CHANCE, PersistentDataType.INTEGER, profile.getBaseCritChance());
        persistenceService.saveDataToPlayerPDC(player, NBTService.PLAYER_BASE_CRIT_DAMAGE, PersistentDataType.INTEGER, profile.getBaseCritDamage());
        persistenceService.saveDataToPlayerPDC(player, NBTService.PLAYER_BASE_MAX_HEALTH, PersistentDataType.INTEGER, profile.getBaseMaxHealth());
        persistenceService.saveDataToPlayerPDC(player, NBTService.PLAYER_BASE_MAX_MANA, PersistentDataType.INTEGER, profile.getBaseMaxMana());
        persistenceService.saveDataToPlayerPDC(player, NBTService.PLAYER_BASE_SPEED_PERCENT, PersistentDataType.INTEGER, profile.getBaseSpeedPercent());
        persistenceService.saveDataToPlayerPDC(player, NBTService.PLAYER_BASE_MINING_SPEED, PersistentDataType.INTEGER, profile.getBaseMiningSpeed());
        persistenceService.saveDataToPlayerPDC(player, NBTService.PLAYER_BASE_FORAGING_SPEED, PersistentDataType.INTEGER, profile.getBaseForagingSpeed());
        persistenceService.saveDataToPlayerPDC(player, NBTService.PLAYER_BASE_FISHING_SPEED, PersistentDataType.INTEGER, profile.getBaseFishingSpeed());
        persistenceService.saveDataToPlayerPDC(player, NBTService.PLAYER_BASE_SHOOTING_SPEED, PersistentDataType.INTEGER, profile.getBaseShootingSpeed());
    }
}