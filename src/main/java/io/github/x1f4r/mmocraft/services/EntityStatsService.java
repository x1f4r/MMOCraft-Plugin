package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import io.github.x1f4r.mmocraft.entities.EntityStats;
import io.github.x1f4r.mmocraft.entities.listeners.EntityLifecycleListener;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType; // For CustomMobTypeID in Part 6

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EntityStatsService implements Service {
    private MMOCore core;
    private LoggingService logging;
    private ConfigService configService;
    private NBTService nbtService; // For CUSTOM_MOB_TYPE_ID_KEY in Part 6
    private CustomMobService customMobService; // For getting CustomMobType stats in Part 6

    // Default stats for vanilla EntityTypes if overridden in mobs.yml
    private final Map<EntityType, EntityStats> vanillaMobTypeStatOverrides = new ConcurrentHashMap<>();
    // Cache for entities that have had MMOCraft stats actively applied (UUID -> their specific EntityStats)
    private final Map<UUID, EntityStats> activeEntityStatsCache = new ConcurrentHashMap<>();

    // To store original Bukkit attribute base values before our direct modification
    private final Map<UUID, Map<Attribute, Double>> originalBaseValues = new ConcurrentHashMap<>();
    // To store original Bukkit attribute modifiers (less common for mobs, but good practice for restoration)
    private final Map<UUID, Map<Attribute, List<AttributeModifier>>> originalModifiers = new ConcurrentHashMap<>();

    public EntityStatsService(MMOCore core) {
        this.core = core;
    }

    @Override
    public void initialize(MMOCore core) {
        this.logging = core.getService(LoggingService.class);
        this.configService = core.getService(ConfigService.class);
        this.nbtService = core.getService(NBTService.class); // Static keys via NBTService.KEY

        // CustomMobService is now a firm dependency for EntityStatsService if custom mobs are handled
        try {
            this.customMobService = core.getService(CustomMobService.class);
        } catch (IllegalStateException e) {
            // This is critical if CustomMobService isn't ready, as applying stats might be incorrect.
            logging.severe("EntityStatsService FAILED to initialize: CustomMobService not available. This is a critical order dependency. Ensure CustomMobService is registered before EntityStatsService in MMOCore.", e);
            throw e; // Prevent this service from loading incorrectly
        }

        FileConfiguration mobsConfig = configService.getConfig(ConfigService.MOBS_CONFIG_FILENAME);
        if (mobsConfig.getKeys(false).isEmpty() && mobsConfig.getConfigurationSection("mobs") == null) {
            logging.warn("'" + ConfigService.MOBS_CONFIG_FILENAME + "' is empty or missing 'mobs' section. No vanilla mob stat overrides loaded.");
        } else {
            loadVanillaMobStatOverrides(mobsConfig);
        }

        configService.subscribeToReload(ConfigService.MOBS_CONFIG_FILENAME, this::loadVanillaMobStatOverrides);

        // The EntityLifecycleListener is now part of CustomMobService or a shared listener.
        // If CustomMobLifecycleListener handles applyStatsToEntity, ensure it does so correctly.
        // For Part 6, CustomMobLifecycleListener will call this service.
        // core.registerListener(new EntityLifecycleListener(this, core)); // This might be removed if CustomMobLifecycleListener covers it

        logging.info(getServiceName() + " initialized. Loaded " + vanillaMobTypeStatOverrides.size() + " vanilla mob stat overrides.");
    }

    // Modify applyStatsToEntity:
    @Override
    public void applyStatsToEntity(LivingEntity entity) {
        if (entity instanceof Player || entity instanceof ArmorStand || !entity.isValid() || entity.isDead()) {
            return;
        }
        if (activeEntityStatsCache.containsKey(entity.getUniqueId())) {
            return; // Already processed
        }

        EntityStats statsToApply = null;
        CustomMobType customType = null;

        // Check NBT for CUSTOM_MOB_TYPE_ID_KEY first
        String customMobId = NBTService.get(entity.getPersistentDataContainer(), NBTService.CUSTOM_MOB_TYPE_ID_KEY, PersistentDataType.STRING, null);

        if (customMobId != null) {
            if (this.customMobService == null) { // Safety check if CustomMobService wasn't ready during init
                try { this.customMobService = core.getService(CustomMobService.class); }
                catch (IllegalStateException e) { logging.warn("CustomMobService still not available when trying to apply stats for existing custom mob NBT."); }
            }
            if (this.customMobService != null) {
                customType = customMobService.getCustomMobType(customMobId);
                if (customType != null) {
                    statsToApply = customType.stats(); // Use stats defined in CustomMobType
                    if (logging.isDebugMode()) logging.debug("Applying stats from CustomMobType '" + customMobId + "' to " + entity.getType() + " (" + entity.getUniqueId() + ")");
                } else {
                    logging.warn("Entity " + entity.getUniqueId() + " has custom_mob_type_id NBT '" + customMobId +
                            "' but no matching CustomMobType found in CustomMobService registry. Will check vanilla overrides.");
                }
            }
        }

        // Fallback to vanilla overrides if not a known custom mob type with stats, or if customType.stats() was null
        if (statsToApply == null) {
            statsToApply = vanillaMobTypeStatOverrides.get(entity.getType());
            if (statsToApply != null && logging.isDebugMode()) {
                logging.debug("Applying vanilla override stats to " + entity.getType() + " (" + entity.getUniqueId() + ")");
            }
        }

        if (statsToApply == null) {
            activeEntityStatsCache.put(entity.getUniqueId(), EntityStats.createDefault(entity));
            // Don't apply Bukkit attributes if no specific stats found, let it be vanilla.
            // Health bar might still be created with vanilla stats.
            VisualFeedbackService vfs = core.getService(VisualFeedbackService.class);
            if (vfs != null) vfs.updateHealthBar(entity);
            return;
        }

        storeOriginalAttributes(entity); // Store before modifying

        AttributeInstance healthInstance = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthInstance != null) {
            healthInstance.setBaseValue(statsToApply.maxHealth());
            entity.setHealth(statsToApply.maxHealth());
        }

        AttributeInstance attackInstance = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attackInstance != null) {
            attackInstance.setBaseValue(statsToApply.strength());
        }

        AttributeInstance speedInstance = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedInstance != null) {
            Double originalBaseSpeed = originalBaseValues.getOrDefault(entity.getUniqueId(), Collections.emptyMap())
                    .get(Attribute.GENERIC_MOVEMENT_SPEED);
            if (originalBaseSpeed == null) originalBaseSpeed = speedInstance.getDefaultValue();
            double newBaseSpeed = originalBaseSpeed * (1.0 + (statsToApply.speedPercent() / 100.0));
            speedInstance.setBaseValue(Math.max(0.0001, newBaseSpeed));
        }

        activeEntityStatsCache.put(entity.getUniqueId(), statsToApply);

        VisualFeedbackService vfs = core.getService(VisualFeedbackService.class);
        if (vfs != null) {
            vfs.updateHealthBar(entity);
        }
    }

    // Modify getEntityStats:
    @Override
    public EntityStats getEntityStats(LivingEntity entity) {
        if (entity instanceof Player || entity instanceof ArmorStand) return EntityStats.createZeroed();

        EntityStats cachedStats = activeEntityStatsCache.get(entity.getUniqueId());
        if (cachedStats != null) return cachedStats;

        EntityStats intendedStats = null;
        String customMobId = NBTService.get(entity.getPersistentDataContainer(), NBTService.CUSTOM_MOB_TYPE_ID_KEY, PersistentDataType.STRING, null);

        if (customMobId != null) {
            if (this.customMobService == null) { // Safety check
                try { this.customMobService = core.getService(CustomMobService.class); }
                catch (IllegalStateException ignored) {}
            }
            if (this.customMobService != null) {
                CustomMobType customType = customMobService.getCustomMobType(customMobId);
                if (customType != null && customType.stats() != null) {
                    intendedStats = customType.stats();
                    // No need to log here if cache miss, could be frequent for entities without persistent stat changes
                }
            }
        }

        if (intendedStats == null) {
            intendedStats = vanillaMobTypeStatOverrides.get(entity.getType());
        }

        return (intendedStats != null) ? intendedStats : EntityStats.createDefault(entity);
    }


    @Override
    public void shutdown() {
        // Attempt to restore original Bukkit attributes for any remaining managed entities
        new ArrayList<>(activeEntityStatsCache.keySet()).forEach(uuid -> {
            // Bukkit.getEntity() might be slow if called many times, but necessary here.
            org.bukkit.entity.Entity entity = core.getPlugin().getServer().getEntity(uuid);
            if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
                clearStatsFromEntity(living, false); // false: don't try to remove from cache again
            }
        });
        vanillaMobTypeStatOverrides.clear();
        activeEntityStatsCache.clear();
        originalBaseValues.clear();
        originalModifiers.clear();
        logging.info(getServiceName() + " shutdown complete.");
    }

    private void loadVanillaMobStatOverrides(FileConfiguration config) {
        vanillaMobTypeStatOverrides.clear();
        ConfigurationSection mobsSection = config.getConfigurationSection("mobs");
        if (mobsSection == null) return;

        for (String entityTypeName : mobsSection.getKeys(false)) {
            try {
                EntityType type = EntityType.valueOf(entityTypeName.toUpperCase());
                ConfigurationSection statsConf = mobsSection.getConfigurationSection(entityTypeName);
                if (statsConf != null) {
                    EntityStats stats = new EntityStats(
                            statsConf.getDouble("maxHealth", EntityStats.createDefault(null).maxHealth()),
                            statsConf.getInt("defense", EntityStats.createDefault(null).defense()),
                            statsConf.getInt("strength", EntityStats.createDefault(null).strength()),
                            statsConf.getInt("critChance", EntityStats.createDefault(null).critChance()),
                            statsConf.getInt("critDamage", EntityStats.createDefault(null).critDamage()),
                            statsConf.getInt("speedPercent", EntityStats.createDefault(null).speedPercent()),
                            statsConf.getInt("maxMana", EntityStats.createDefault(null).maxMana())
                    );
                    vanillaMobTypeStatOverrides.put(type, stats);
                }
            } catch (IllegalArgumentException e) {
                logging.warn("Invalid EntityType '" + entityTypeName + "' in " + ConfigService.MOBS_CONFIG_FILENAME + ". Skipping override.");
            }
        }
        logging.info("Reloaded " + vanillaMobTypeStatOverrides.size() + " vanilla mob stat overrides from " + ConfigService.MOBS_CONFIG_FILENAME);
    }

    public void applyStatsToEntity(LivingEntity entity) {
        if (entity instanceof Player || entity instanceof ArmorStand || !entity.isValid() || entity.isDead()) {
            return;
        }
        // Avoid re-applying if already cached (unless force flag is added later)
        if (activeEntityStatsCache.containsKey(entity.getUniqueId())) {
            // logging.debug("Stats already applied to " + entity.getType() + " (" + entity.getUniqueId() + ")");
            return;
        }

        EntityStats statsToApply = null;
        CustomMobType customType = null;

        // In Part 6, CustomMobService will be primary source for CustomMobType
        String customMobId = NBTService.get(entity.getPersistentDataContainer(), NBTService.CUSTOM_MOB_TYPE_ID_KEY, PersistentDataType.STRING, null);
        if (customMobId != null && customMobService != null) {
            customType = customMobService.getCustomMobType(customMobId);
            if (customType != null) {
                statsToApply = customType.stats();
                logging.debug("Applying stats from CustomMobType '" + customMobId + "' to " + entity.getType() + " (" + entity.getUniqueId() + ")");
            } else {
                logging.warn("Entity " + entity.getUniqueId() + " has NBT custom_mob_type_id '" + customMobId + "' but type not found in CustomMobService registry.");
            }
        }

        // Fallback to vanilla overrides if not a known custom type or custom type has no stats
        if (statsToApply == null) {
            statsToApply = vanillaMobTypeStatOverrides.get(entity.getType());
            if (statsToApply != null) {
                logging.debug("Applying vanilla override stats to " + entity.getType() + " (" + entity.getUniqueId() + ")");
            }
        }

        if (statsToApply == null) {
            // No specific stats defined for this entity type (neither custom nor vanilla override).
            // We cache a "default" stat object so getEntityStats() always returns something.
            // This default won't modify Bukkit attributes unless explicitly designed to.
            activeEntityStatsCache.put(entity.getUniqueId(), EntityStats.createDefault(entity));
            return;
        }

        storeOriginalAttributes(entity); // Store before modifying

        // Apply Max Health
        AttributeInstance healthInstance = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthInstance != null) {
            healthInstance.setBaseValue(statsToApply.maxHealth());
            entity.setHealth(statsToApply.maxHealth()); // Heal to new max immediately
        }

        // Apply Attack Damage (Strength)
        AttributeInstance attackInstance = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attackInstance != null) {
            attackInstance.setBaseValue(statsToApply.strength());
        }

        // Apply Movement Speed
        AttributeInstance speedInstance = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedInstance != null) {
            Double originalBaseSpeed = originalBaseValues.getOrDefault(entity.getUniqueId(), Collections.emptyMap())
                    .get(Attribute.GENERIC_MOVEMENT_SPEED);
            if (originalBaseSpeed == null) originalBaseSpeed = speedInstance.getDefaultValue(); // Fallback

            double newBaseSpeed = originalBaseSpeed * (1.0 + (statsToApply.speedPercent() / 100.0));
            speedInstance.setBaseValue(Math.max(0.0001, newBaseSpeed)); // Ensure speed is positive
        }

        activeEntityStatsCache.put(entity.getUniqueId(), statsToApply);

        // Notify VisualFeedbackService to create/update health bar
        VisualFeedbackService vfs = core.getService(VisualFeedbackService.class);
        if (vfs != null) {
            vfs.updateHealthBar(entity);
        }
    }

    private void storeOriginalAttributes(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        if (originalBaseValues.containsKey(uuid)) return; // Already stored

        Map<Attribute, Double> baseValues = new EnumMap<>(Attribute.class);
        Map<Attribute, List<AttributeModifier>> modifiers = new EnumMap<>(Attribute.class);

        AttributeInstance attr;
        // Max Health
        attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) { baseValues.put(Attribute.GENERIC_MAX_HEALTH, attr.getBaseValue()); modifiers.put(Attribute.GENERIC_MAX_HEALTH, new ArrayList<>(attr.getModifiers()));}
        // Attack Damage
        attr = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attr != null) { baseValues.put(Attribute.GENERIC_ATTACK_DAMAGE, attr.getBaseValue()); modifiers.put(Attribute.GENERIC_ATTACK_DAMAGE, new ArrayList<>(attr.getModifiers()));}
        // Movement Speed
        attr = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr != null) { baseValues.put(Attribute.GENERIC_MOVEMENT_SPEED, attr.getBaseValue()); modifiers.put(Attribute.GENERIC_MOVEMENT_SPEED, new ArrayList<>(attr.getModifiers()));}
        // Add others if needed: ARMOR, ARMOR_TOUGHNESS, KNOCKBACK_RESISTANCE etc.

        if (!baseValues.isEmpty()) originalBaseValues.put(uuid, baseValues);
        if (!modifiers.isEmpty()) originalModifiers.put(uuid, modifiers);
        logging.debug("Stored original Bukkit attributes for " + entity.getType() + " (" + uuid + ")");
    }

    public void clearStatsFromEntity(LivingEntity entity, boolean removeFromCache) {
        UUID uuid = entity.getUniqueId();
        // Check if we even managed this entity
        if (!activeEntityStatsCache.containsKey(uuid) && !originalBaseValues.containsKey(uuid)) {
            return;
        }

        logging.debug("Clearing/Restoring stats for " + entity.getType() + " (" + uuid + ")");
        restoreOriginalAttributes(entity);

        if (removeFromCache) {
            activeEntityStatsCache.remove(uuid);
        }
        originalBaseValues.remove(uuid);
        originalModifiers.remove(uuid);
    }

    private void restoreOriginalAttributes(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        Map<Attribute, Double> storedBases = originalBaseValues.get(uuid);
        Map<Attribute, List<AttributeModifier>> storedMods = originalModifiers.get(uuid);

        // Helper to restore a single attribute
        BiConsumer<Attribute, AttributeInstance> restoreFunc = (attrEnum, attrInst) -> {
            if (attrInst == null) return;
            // Clear any modifiers we might have added (or others, be careful)
            // For simplicity, if we are only setting baseValue, this might not be needed if we didn't add modifiers.
            // However, if other plugins add modifiers based on our baseValue, this could be complex.
            // For now, assume we primarily set baseValue.
            // clearOurModifiers(attrInst); // If we added named modifiers

            Double originalBase = (storedBases != null) ? storedBases.get(attrEnum) : null;
            attrInst.setBaseValue(originalBase != null ? originalBase : attrInst.getDefaultValue());

            // Reapply original vanilla/other plugin modifiers if stored
            List<AttributeModifier> originalEntityModifiers = (storedMods != null) ? storedMods.get(attrEnum) : null;
            if (originalEntityModifiers != null) {
                // Remove any currently existing modifiers first to avoid duplicates if some are persistent
                new ArrayList<>(attrInst.getModifiers()).forEach(attrInst::removeModifier);
                originalEntityModifiers.forEach(attrInst::addModifier);
            }
        };

        restoreFunc.accept(Attribute.GENERIC_MAX_HEALTH, entity.getAttribute(Attribute.GENERIC_MAX_HEALTH));
        if (entity.isValid() && !entity.isDead() && entity.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) { // Ensure health is valid after base change
            if (entity.getHealth() > entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()) {
                entity.setHealth(entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            }
        }
        restoreFunc.accept(Attribute.GENERIC_ATTACK_DAMAGE, entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE));
        restoreFunc.accept(Attribute.GENERIC_MOVEMENT_SPEED, entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED));
        // Restore other attributes if managed
    }


    /**
     * Retrieves the effective {@link EntityStats} for a given living entity.
     * This considers custom mob type definitions (Part 6) and vanilla overrides.
     * @param entity The living entity.
     * @return The {@link EntityStats} for the entity; never null (returns a default if no specific stats).
     */
    public EntityStats getEntityStats(LivingEntity entity) {
        if (entity instanceof Player || entity instanceof ArmorStand) return EntityStats.createZeroed();

        EntityStats cachedStats = activeEntityStatsCache.get(entity.getUniqueId());
        if (cachedStats != null) return cachedStats;

        // If not in active cache, it means applyStatsToEntity wasn't called or entity despawned.
        // We should try to determine its intended stats.
        EntityStats intendedStats = null;
        String customMobId = NBTService.get(entity.getPersistentDataContainer(), NBTService.CUSTOM_MOB_TYPE_ID_KEY, PersistentDataType.STRING, null);

        if (customMobId != null && customMobService != null) {
            CustomMobType customType = customMobService.getCustomMobType(customMobId);
            if (customType != null && customType.stats() != null) {
                intendedStats = customType.stats();
            }
        }

        if (intendedStats == null) {
            intendedStats = vanillaMobTypeStatOverrides.get(entity.getType());
        }

        return (intendedStats != null) ? intendedStats : EntityStats.createDefault(entity);
    }
}