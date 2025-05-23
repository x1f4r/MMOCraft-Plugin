package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

/**
 * Service for abstracting data persistence operations.
 * Initially focuses on Player PersistentDataContainer (PDC).
 */
public class PersistenceService implements Service {
    private LoggingService logging;
    // private NBTService nbtService; // Not strictly needed if keys are always passed in by callers

    /**
     * Constructor for PersistenceService.
     * @param loggingService The LoggingService for logging operations.
     */
    public PersistenceService(LoggingService loggingService) {
        // Direct dependency injection for services available at MMOCore construction time
        this.logging = Objects.requireNonNull(loggingService, "LoggingService cannot be null for PersistenceService");
    }

    @Override
    public void initialize(MMOCore core) {
        // If this service depended on others initialized *after* it by MMOCore,
        // you would get them here using core.getService().
        // e.g., this.nbtService = core.getService(NBTService.class);
        // For PersistenceService, logging is often the primary early dependency.
        logging.info(getServiceName() + " initialized.");
    }

    @Override
    public void shutdown() {
        // No specific shutdown tasks for PDC-based persistence, as data is saved by Bukkit.
        // If using file/DB persistence, close connections or write pending data here.
        logging.info(getServiceName() + " shutdown complete.");
    }

    /**
     * Saves data to a player's PersistentDataContainer.
     * @param player The player whose PDC to modify.
     * @param key The NamespacedKey for the data.
     * @param type The PersistentDataType of the data.
     * @param value The value to save. If null, the key is removed.
     * @param <T> The primitive tag type (e.g., String, Integer).
     * @param <Z> The complex object type (e.g., String, Integer, or custom PDC tags).
     */
    public <T, Z> void saveDataToPlayerPDC(Player player, NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        Objects.requireNonNull(player, "Player cannot be null for saving PDC data.");
        Objects.requireNonNull(key, "NamespacedKey cannot be null for saving PDC data.");
        Objects.requireNonNull(type, "PersistentDataType cannot be null for saving PDC data.");

        if (!player.isOnline()) { // PDC operations generally require an online player
            logging.warn("Attempted to save PDC data for offline player: " + player.getName() + ", key: " + key.getKey() + ". Operation skipped.");
            return; // Skip operation for offline players to prevent errors
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value == null) {
            pdc.remove(key);
            logging.debug("Removed PDC data for player " + player.getName() + ", key: " + key.getKey());
        } else {
            pdc.set(key, type, value);
            if (logging.isDebugMode()) { // Avoid excessive toString() calls if not debugging
                String valueStr = value.toString();
                logging.debug("Saved PDC data for player " + player.getName() + ", key: " + key.getKey() +
                        ", value: " + (valueStr.length() > 50 ? valueStr.substring(0, 47) + "..." : valueStr));
            }
        }
    }

    /**
     * Loads data from a player's PersistentDataContainer.
     * @param player The player whose PDC to read from.
     * @param key The NamespacedKey for the data.
     * @param type The PersistentDataType of the data.
     * @param defaultValue The value to return if the key is not found or type mismatch.
     * @param <T> The primitive tag type.
     * @param <Z> The complex object type.
     * @return The loaded value, or defaultValue.
     */
    public <T, Z> Z loadDataFromPlayerPDC(Player player, NamespacedKey key, PersistentDataType<T, Z> type, Z defaultValue) {
        Objects.requireNonNull(player, "Player cannot be null for loading PDC data.");
        Objects.requireNonNull(key, "NamespacedKey cannot be null for loading PDC data.");
        Objects.requireNonNull(type, "PersistentDataType cannot be null for loading PDC data.");

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Z value = pdc.getOrDefault(key, type, defaultValue);

        if (logging.isDebugMode()) {
            String valueStr = (value != null) ? value.toString() : "null (default used: " + (defaultValue != null ? defaultValue.toString() : "null") + ")";
            logging.debug("Loaded PDC data for player " + player.getName() + ", key: " + key.getKey() +
                    ", value: " + (valueStr.length() > 60 ? valueStr.substring(0, 57) + "..." : valueStr));
        }
        return value;
    }

    /**
     * Checks if a player's PersistentDataContainer has data for a given key and type.
     * @param player The player.
     * @param key The NamespacedKey.
     * @param type The PersistentDataType.
     * @return true if the data exists, false otherwise.
     */
    public boolean hasDataInPlayerPDC(Player player, NamespacedKey key, PersistentDataType<?, ?> type) {
        Objects.requireNonNull(player, "Player cannot be null for checking PDC data.");
        Objects.requireNonNull(key, "NamespacedKey cannot be null for checking PDC data.");
        Objects.requireNonNull(type, "PersistentDataType cannot be null for checking PDC data.");
        return player.getPersistentDataContainer().has(key, type);
    }

    /**
     * Removes data from a player's PersistentDataContainer.
     * @param player The player.
     * @param key The NamespacedKey to remove.
     */
    public void removeDataFromPlayerPDC(Player player, NamespacedKey key) {
        Objects.requireNonNull(player, "Player cannot be null for removing PDC data.");
        Objects.requireNonNull(key, "NamespacedKey cannot be null for removing PDC data.");
        player.getPersistentDataContainer().remove(key);
        logging.debug("Explicitly removed PDC data for player " + player.getName() + ", key: " + key.getKey());
    }
}