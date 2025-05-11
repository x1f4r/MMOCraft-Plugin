package io.github.x1f4r.mmocraft.services;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent; // Paper API for precise armor changes
import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import io.github.x1f4r.mmocraft.player.PlayerProfile;
import io.github.x1f4r.mmocraft.player.listeners.PlayerEquipmentListener;
import io.github.x1f4r.mmocraft.player.stats.CalculatedPlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerStatsService implements Service {
    private MMOCore core; // Public for PlayerEquipmentListener to access plugin instance via core.getPlugin()
    private LoggingService logging;
    private PlayerDataService playerDataService;
    private NBTService nbtService; // For item stat keys
    // private ConfigService configService; // For future global stat caps, etc.
    // private ItemService itemService; // Will be crucial in Part 2 for CustomItem templates

    private final Map<UUID, CalculatedPlayerStats> statsCache = new ConcurrentHashMap<>();
    // Stores active stat update tasks, de-bouncing rapid changes.
    private final Map<UUID, BukkitTask> updateTasks = new ConcurrentHashMap<>();

    // Names for our custom attribute modifiers
    private static final String MMO_MAX_HEALTH_MODIFIER_NAME = "mmoc_max_health";
    private static final String MMO_SPEED_MODIFIER_NAME = "mmoc_speed";

    // Deterministic UUID generation for player-specific attribute modifiers
    // These salts ensure that modifiers are unique per player AND per attribute type
    // but consistent for that player and attribute if re-applied.
    private static final UUID MAX_HEALTH_MODIFIER_UUID_SALT = UUID.fromString("a1b2c3d4-e5f6-7788-99aa-bbccddeeff00");
    private static final UUID SPEED_MODIFIER_UUID_SALT = UUID.fromString("11223344-5566-7788-99aa-bbccddeeff11");


    public PlayerStatsService(MMOCore core) {
        this.core = core; // Store MMOCore for access to plugin instance and other services
    }

    @Override
    public void initialize(MMOCore core) {
        this.logging = core.getService(LoggingService.class);
        this.playerDataService = core.getService(PlayerDataService.class);
        this.nbtService = core.getService(NBTService.class); // NBTService.java holds the static keys
        // this.configService = core.getService(ConfigService.class); // If needed for settings
        // this.itemService = core.getService(ItemService.class); // When ItemService is added in Part 2

        core.registerListener(new PlayerEquipmentListener(this, core)); // Pass MMOCore for logging
        logging.info(getServiceName() + " initialized.");

        // Recalculate stats for any players already online (e.g., after /reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Profile should be loaded by PlayerDataService.initialize() already
            scheduleStatsUpdate(player);
        }
    }

    @Override
    public void shutdown() {
        updateTasks.values().forEach(task -> {
            if (task != null && !task.isCancelled()) task.cancel();
        });
        updateTasks.clear();
        statsCache.clear();

        // Remove attribute modifiers from all online players to prevent them persisting incorrectly
        for (Player player : Bukkit.getOnlinePlayers()) {
            removePlayerAttributeModifiers(player);
        }
        logging.info(getServiceName() + " shutdown complete.");
    }

    /**
     * Gets the calculated stats for a player. If not cached, it will trigger a recalculation.
     * @param player The player.
     * @return The CalculatedPlayerStats object.
     */
    public CalculatedPlayerStats getCalculatedStats(Player player) {
        if (player == null) {
            logging.warn("Attempted to get CalculatedPlayerStats for a null player. Returning defaults.");
            return CalculatedPlayerStats.PURE_VANILLA_DEFAULTS;
        }
        // computeIfAbsent ensures atomic calculation if not present
        return statsCache.computeIfAbsent(player.getUniqueId(), uuid -> {
            logging.debug("Stat cache miss for " + player.getName() + ". Calculating stats now.");
            return recalculateAndApplyAllStats(player); // Directly calculate if not in cache
        });
    }

    /**
     * Schedules a debounced statistics update for the player.
     * This is called by listeners when equipment or other stat-affecting conditions change.
     * @param player The player to update.
     */
    public void scheduleStatsUpdate(Player player) {
        if (player == null || !player.isOnline()) return;

        UUID playerId = player.getUniqueId();
        BukkitTask existingTask = updateTasks.remove(playerId);
        if (existingTask != null) {
            existingTask.cancel();
        }

        // Schedule the actual recalculation to run on the next server tick (or slightly after)
        // This helps batch multiple rapid changes (e.g., quickly swapping armor pieces).
        BukkitTask newTask = Bukkit.getScheduler().runTaskLater(core.getPlugin(), () -> {
            if (player.isOnline()) { // Double-check player is still online
                recalculateAndApplyAllStats(player);
            }
            updateTasks.remove(playerId); // Remove self from tracking after execution
            logging.debug("Scheduled stat update task executed for " + player.getName());
        }, 2L); // 2-tick delay; adjust as needed. 1L might be sufficient.
        updateTasks.put(playerId, newTask);
    }

    /**
     * Core method to recalculate all player stats from base (PlayerProfile) and equipment NBT.
     * Updates the cache and applies Bukkit attribute modifiers.
     * @param player The player whose stats to recalculate.
     * @return The newly calculated stats.
     */
    private CalculatedPlayerStats recalculateAndApplyAllStats(Player player) {
        if (player == null || !player.isOnline()) {
            if (player != null) statsCache.remove(player.getUniqueId());
            logging.warn("Attempted to recalculate stats for null or offline player.");
            return CalculatedPlayerStats.PURE_VANILLA_DEFAULTS;
        }

        PlayerProfile profile = playerDataService.getProfile(player);

        // Initialize equipment contributions to zero
        int eqStrength = 0, eqDefense = 0, eqCritChance = 0, eqCritDamage = 0;
        int eqMaxHealth = 0, eqMaxMana = 0, eqSpeedPercent = 0;
        int eqMiningSpeed = 0, eqForagingSpeed = 0, eqFishingSpeed = 0, eqShootingSpeed = 0;

        // Iterate through equipped items
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> equippedItems = new ArrayList<>();
        equippedItems.add(inventory.getItemInMainHand());
        equippedItems.add(inventory.getItemInOffHand());
        equippedItems.addAll(List.of(inventory.getArmorContents()));
        // Future: Add items from custom accessory slots here

        for (ItemStack item : equippedItems) {
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue; // Should not happen if hasItemMeta is true
            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            // For Part 1, we directly read NBT keys.
            // In Part 2, ItemService will provide CustomItem templates with parsed stats.
            eqStrength += NBTService.get(pdc, NBTService.ITEM_STAT_STRENGTH, PersistentDataType.INTEGER, 0);
            eqDefense += NBTService.get(pdc, NBTService.ITEM_STAT_DEFENSE, PersistentDataType.INTEGER, 0);
            eqCritChance += NBTService.get(pdc, NBTService.ITEM_STAT_CRIT_CHANCE, PersistentDataType.INTEGER, 0);
            eqCritDamage += NBTService.get(pdc, NBTService.ITEM_STAT_CRIT_DAMAGE, PersistentDataType.INTEGER, 0);
            eqMaxHealth += NBTService.get(pdc, NBTService.ITEM_STAT_MAX_HEALTH_BONUS, PersistentDataType.INTEGER, 0);
            eqMaxMana += NBTService.get(pdc, NBTService.ITEM_STAT_MAX_MANA_BONUS, PersistentDataType.INTEGER, 0);
            eqSpeedPercent += NBTService.get(pdc, NBTService.ITEM_STAT_SPEED_BONUS_PERCENT, PersistentDataType.INTEGER, 0);
            eqMiningSpeed += NBTService.get(pdc, NBTService.ITEM_STAT_MINING_SPEED_BONUS, PersistentDataType.INTEGER, 0);
            eqForagingSpeed += NBTService.get(pdc, NBTService.ITEM_STAT_FORAGING_SPEED_BONUS, PersistentDataType.INTEGER, 0);
            eqFishingSpeed += NBTService.get(pdc, NBTService.ITEM_STAT_FISHING_SPEED_BONUS, PersistentDataType.INTEGER, 0);
            eqShootingSpeed += NBTService.get(pdc, NBTService.ITEM_STAT_SHOOTING_SPEED_BONUS, PersistentDataType.INTEGER, 0);

            // Placeholder for special set bonuses (e.g., Ender Armor in The End)
            // This logic will be significantly improved with ItemService and ArmorSetService in later parts.
            // String itemId = NBTService.get(pdc, NBTService.ITEM_ID_KEY, PersistentDataType.STRING, "");
            // if (itemId.startsWith("ender_armor_") && player.getWorld().getEnvironment() == World.Environment.THE_END) {
            //    eqDefense += NBTService.get(pdc, NBTService.ITEM_STAT_DEFENSE, PersistentDataType.INTEGER, 0); // Double defense part
            // }
        }

        // Calculate total stats by combining base from profile and equipment bonuses
        int totalStrength = profile.getBaseStrength() + eqStrength;
        int totalDefense = Math.max(0, profile.getBaseDefense() + eqDefense);
        int totalCritChance = Math.max(0, Math.min(100, profile.getBaseCritChance() + eqCritChance));
        int totalCritDamage = Math.max(0, profile.getBaseCritDamage() + eqCritDamage);
        int totalMaxHealth = Math.max(1, profile.getBaseMaxHealth() + eqMaxHealth);
        int totalMaxMana = Math.max(0, profile.getBaseMaxMana() + eqMaxMana); // Allow 0 max mana
        int totalSpeedPercent = profile.getBaseSpeedPercent() + eqSpeedPercent;
        int totalMiningSpeed = Math.max(0, profile.getBaseMiningSpeed() + eqMiningSpeed);
        int totalForagingSpeed = Math.max(0, profile.getBaseForagingSpeed() + eqForagingSpeed);
        int totalFishingSpeed = Math.max(0, profile.getBaseFishingSpeed() + eqFishingSpeed);
        int totalShootingSpeed = Math.max(0, profile.getBaseShootingSpeed() + eqShootingSpeed);

        CalculatedPlayerStats newStats = new CalculatedPlayerStats(
                totalStrength, totalDefense, totalCritChance, totalCritDamage,
                totalMaxHealth, totalMaxMana, totalSpeedPercent,
                totalMiningSpeed, totalForagingSpeed, totalFishingSpeed, totalShootingSpeed
        );

        statsCache.put(player.getUniqueId(), newStats);
        if (logging.isDebugMode()) {
            logging.debug("Recalculated stats for " + player.getName() + ": " + newStats.toString());
        }

        applyBukkitAttributes(player, newStats);

        // Notify PlayerResourceService about potential max mana change for clamping current mana
        core.getService(PlayerResourceService.class).handleStatsUpdate(player, newStats);
        // Notify PlayerInterfaceService to update display (e.g. action bar)
        core.getService(PlayerInterfaceService.class).scheduleActionBarUpdateForPlayer(player);


        // Future: Fire a custom PlayerStatsUpdatedEvent
        // Bukkit.getPluginManager().callEvent(new PlayerStatsUpdatedEvent(player, oldStats, newStats));

        return newStats;
    }

    /**
     * Applies relevant calculated stats (Max Health, Movement Speed) to the player's
     * Bukkit AttributeInstances.
     * @param player The player.
     * @param stats The calculated stats to apply.
     */
    private void applyBukkitAttributes(Player player, CalculatedPlayerStats stats) {
        // --- Max Health ---
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            // Our model: MMOCraft's totalMaxHealth directly becomes Bukkit's base max health.
            // No separate modifiers needed *from this service* for the total max health.
            // If items provide vanilla GENERIC_MAX_HEALTH modifiers, those will stack on top.
            if (maxHealthAttr.getBaseValue() != stats.maxHealth()) {
                double oldBukkitMaxHealth = maxHealthAttr.getBaseValue();
                maxHealthAttr.setBaseValue(stats.maxHealth());
                logging.debug("Set GENERIC_MAX_HEALTH base for " + player.getName() + " to " + stats.maxHealth() + " (was " + oldBukkitMaxHealth + ")");
            }

            // Adjust current health if it exceeds new max or if player was "dead" (0 max health)
            if (player.getHealth() > stats.maxHealth()) {
                player.setHealth(stats.maxHealth());
            } else if (player.getHealth() <= 0 && stats.maxHealth() >= 1 && !player.isDead()) {
                // This case is tricky; if player somehow had 0 max health and 0 current,
                // then gets >0 max health, they might still appear dead.
                // Respawn logic typically handles setting health.
                // For safety, if they get health back and are not "Bukkit dead", ensure they have some current HP.
                // player.setHealth(Math.min(1.0, stats.maxHealth()));
            }
        } else {
            logging.warn("Player " + player.getName() + " missing GENERIC_MAX_HEALTH attribute!");
        }

        // --- Movement Speed ---
        AttributeInstance speedAttr = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            UUID modifierUUID = getDeterministicUUID(player.getUniqueId(), SPEED_MODIFIER_UUID_SALT);
            // Remove any existing speed modifier from this plugin before applying a new one
            removeModifierByUUID(speedAttr, modifierUUID, MMO_SPEED_MODIFIER_NAME);

            if (stats.speedPercent() != 0) {
                // Vanilla base speed is 0.1.
                // Our speedPercent is an additional percentage.
                // E.g., speedPercent = 20 means +20% of current base speed.
                // Operation.MULTIPLY_SCALAR_1 adds (value * current_total_of_lower_priority_modifiers).
                // For a simple percentage increase OF THE BASE, we need to be careful.
                // Bukkit's MULTIPLY_SCALAR_1 applies to (BaseValue + ADD_NUMBER modifiers) * (1 + this_modifier_value).
                // If speedPercent is +20%, modifierValue should be 0.2.
                double modifierValue = stats.speedPercent() / 100.0;
                AttributeModifier speedModifier = new AttributeModifier(
                        modifierUUID,
                        MMO_SPEED_MODIFIER_NAME,
                        modifierValue,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1
                );
                try {
                    speedAttr.addModifier(speedModifier);
                    logging.debug("Applied speed modifier (" + String.format("%.2f", modifierValue) + ", " + stats.speedPercent() + "%) to " + player.getName() + ". Speed Attr Value: " + String.format("%.4f", speedAttr.getValue()));
                } catch (IllegalArgumentException e) {
                    // This can happen if a modifier with the same UUID somehow still exists.
                    logging.warn("Failed to apply speed modifier for " + player.getName() + ": " + e.getMessage() + ". Attempting to remove and re-add.");
                    // Try removing again just in case, then re-add. This is a fallback.
                    removeModifierByUUID(speedAttr, modifierUUID, MMO_SPEED_MODIFIER_NAME); // Force remove
                    try { speedAttr.addModifier(speedModifier); } catch (Exception ex) {
                        logging.severe("Still failed to apply speed modifier after re-attempt for " + player.getName(), ex);
                    }
                }
            } else {
                // If speedPercent is 0, the modifier is removed (or not added), reverting to base speed.
                logging.debug("Speed percent is 0 for " + player.getName() + ". Ensured no custom MMO speed modifier. Speed Attr Value: " + String.format("%.4f", speedAttr.getValue()));
            }
        } else {
            logging.warn("Player " + player.getName() + " missing GENERIC_MOVEMENT_SPEED attribute!");
        }
    }

    /**
     * Generates a deterministic UUID for an attribute modifier based on player's UUID and a salt.
     */
    private UUID getDeterministicUUID(UUID playerUUID, UUID salt) {
        String combined = playerUUID.toString() + ":" + salt.toString();
        return UUID.nameUUIDFromBytes(combined.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Removes an attribute modifier by its UUID and expected name from an AttributeInstance.
     */
    private void removeModifierByUUID(AttributeInstance attributeInstance, UUID modifierUUID, String expectedName) {
        AttributeModifier toRemove = null;
        for (AttributeModifier modifier : attributeInstance.getModifiers()) {
            if (modifier.getUniqueId().equals(modifierUUID)) {
                // We found our modifier by UUID. Name check is a secondary safety.
                if (!modifier.getName().equals(expectedName)) {
                    logging.warn("Found modifier with matching UUID " + modifierUUID + " for attribute " +
                            attributeInstance.getAttribute().getKey() + " but name mismatch. Expected: '" +
                            expectedName + "', Found: '" + modifier.getName() + "'. Removing anyway by UUID.");
                }
                toRemove = modifier;
                break;
            }
        }
        if (toRemove != null) {
            try {
                attributeInstance.removeModifier(toRemove);
                logging.debug("Removed attribute modifier '" + expectedName + "' (UUID: " + modifierUUID + ") from " + attributeInstance.getAttribute().getKey());
            } catch (Exception e) { // Catch broad exceptions as Bukkit can be finicky with modifier removal
                logging.warn("Could not remove modifier " + expectedName + " for attribute " +
                        attributeInstance.getAttribute().getKey() + " (UUID: " + modifierUUID + "): " + e.getMessage());
            }
        }
    }

    /**
     * Removes all MMOCraft-specific attribute modifiers from a player.
     * Called on player quit or plugin disable.
     */
    private void removePlayerAttributeModifiers(Player player) {
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            // Since we set baseValue directly for max health, we need to revert it to Bukkit's default.
            // Any item-specific vanilla GENERIC_MAX_HEALTH modifiers would remain.
            if (maxHealthAttr.getBaseValue() != maxHealthAttr.getDefaultValue()) {
                maxHealthAttr.setBaseValue(maxHealthAttr.getDefaultValue());
                logging.debug("Reset GENERIC_MAX_HEALTH base for " + player.getName() + " to vanilla default: " + maxHealthAttr.getDefaultValue());
            }
        }

        AttributeInstance speedAttr = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            UUID speedModifierUUID = getDeterministicUUID(player.getUniqueId(), SPEED_MODIFIER_UUID_SALT);
            removeModifierByUUID(speedAttr, speedModifierUUID, MMO_SPEED_MODIFIER_NAME);
        }
        logging.debug("Cleaned up MMOCraft attribute modifiers for " + player.getName());
    }

    /**
     * Called by PlayerDataService when a player joins and their profile is loaded/created.
     * @param player The player who joined.
     */
    public void handlePlayerJoin(Player player) {
        // PlayerDataService calls this *after* profile is loaded/created.
        // Schedule initial stat calculation.
        scheduleStatsUpdate(player);
        logging.debug(getServiceName() + " acknowledged join for " + player.getName() + ", scheduled initial stat update.");
    }

    /**
     * Called by PlayerDataService when a player quits.
     * @param player The player who quit.
     */
    public void handlePlayerQuit(Player player) {
        removePlayerAttributeModifiers(player); // Crucial to remove our persistent effects
        statsCache.remove(player.getUniqueId());
        BukkitTask task = updateTasks.remove(player.getUniqueId());
        if (task != null && !task.isCancelled()) task.cancel();
        logging.debug(getServiceName() + " processed quit for " + player.getName() + ", cleaned up stats and modifiers.");
    }
}