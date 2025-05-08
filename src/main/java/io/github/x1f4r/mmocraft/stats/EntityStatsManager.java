package io.github.x1f4r.mmocraft.stats;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EntityStatsManager {

    private final MMOCore core;
    private final MMOPlugin plugin;
    private final Logger log;
    private final Map<UUID, EntityStats> entityStatsCache = new HashMap<>();
    private final Map<EntityType, EntityStats> mobConfigStats = new HashMap<>();
    private final File mobsFile;
    // Map to store original modifiers BEFORE we apply our base stats
    private final Map<UUID, Map<Attribute, List<AttributeModifier>>> originalModifiers = new HashMap<>();

    public EntityStatsManager(MMOCore core) {
        this.core = core;
        this.plugin = core.getPlugin();
        this.log = MMOPlugin.getMMOLogger();
        this.mobsFile = new File(plugin.getDataFolder(), "mobs.yml");
    }

    public void initialize() {
        reloadMobsConfig();
        log.info("EntityStatsManager initialized.");
        // Note: We don't process existing entities here, EntitySpawnListener handles new ones.
        // If needed on reload, you'd iterate Bukkit.getWorlds()...getLivingEntities()
    }

    public void reloadMobsConfig() {
        mobConfigStats.clear();
        // Mobs file should have been saved by MMOCore already
        if (!mobsFile.exists()) {
            log.warning("mobs.yml not found. Cannot load custom mob stats.");
            return;
        }
        FileConfiguration mobsConfigInstance = YamlConfiguration.loadConfiguration(mobsFile);
        ConfigurationSection mobsSection = mobsConfigInstance.getConfigurationSection("mobs");
        if (mobsSection == null) {
            log.info("No 'mobs' section in mobs.yml or file is empty.");
            return;
        }

        int count = 0;
        for (String entityTypeName : mobsSection.getKeys(false)) {
            try {
                EntityType type = EntityType.valueOf(entityTypeName.toUpperCase());
                ConfigurationSection statsConf = mobsSection.getConfigurationSection(entityTypeName);
                if (statsConf != null) {
                    // Use defaults from config or fallback values if keys are missing
                    double maxHealth = statsConf.getDouble("maxHealth", 20.0); // Default 20 health
                    int defense = statsConf.getInt("defense", 0);
                    int strength = statsConf.getInt("strength", 0);
                    int speed = statsConf.getInt("speed", 0); // 0% change default
                    int maxMana = statsConf.getInt("maxMana", 0);
                    int critChance = statsConf.getInt("critChance", 0);
                    int critDamage = statsConf.getInt("critDamage", 0); // Often 0 or 50 for mobs

                    EntityStats stats = new EntityStats(maxHealth, defense, strength, speed, maxMana, critChance, critDamage);
                    mobConfigStats.put(type, stats);
                    count++;
                }
            } catch (IllegalArgumentException e) {
                log.warning("Invalid entity type '" + entityTypeName + "' in mobs.yml.");
            } catch (Exception e) {
                log.log(Level.SEVERE, "Error parsing stats for mob type '" + entityTypeName + "' in mobs.yml", e);
            }
        }
        log.info("Loaded stats for " + count + " mob types from mobs.yml.");
    }

    /**
     * Registers an entity, applying configured stats if available.
     * Should be called when an entity spawns.
     */
    public void registerEntity(LivingEntity entity) {
        if (entity == null || entityStatsCache.containsKey(entity.getUniqueId()) || entity.isDead()) {
            return;
        }
        log.finer("Registering entity: " + entity.getType() + " (" + entity.getUniqueId() + ")");
        initializeStatsForEntity(entity);
    }

    /**
     * Unregisters an entity, typically on death or unload, restoring original attributes.
     */
    public void unregisterEntity(LivingEntity entity) {
        if (entity != null) {
            UUID uuid = entity.getUniqueId();
            if (entityStatsCache.containsKey(uuid)) {
                 log.finer("Unregistering entity: " + entity.getType() + " (" + uuid + ")");
                 entityStatsCache.remove(uuid);
                 // Restore attributes ONLY if the entity is not already marked as dead by Bukkit.
                 // Trying to modify attributes of a truly dead entity can cause errors.
                 if (!entity.isDead()) {
                     restoreOriginalAttributes(entity);
                 }
                 originalModifiers.remove(uuid); // Always clear stored modifiers
            }
        }
    }

    /**
     * Gets the cached EntityStats for an entity. If not cached, it initializes them.
     */
    public EntityStats getStats(LivingEntity entity) {
        if (entity == null) return EntityStats.createDefault();
        // computeIfAbsent handles the initialization logic if the entity isn't already cached.
        return entityStatsCache.computeIfAbsent(entity.getUniqueId(), uuid -> {
            log.finer("Entity not in cache, initializing stats for: " + entity.getType() + " (" + uuid + ")");
            return initializeStatsForEntity(entity); // Return the newly created/applied stats
        });
    }

    /**
     * Initializes stats for an entity based on config or defaults, applies them, and caches the result.
     * Returns the applied EntityStats object.
     */
    private EntityStats initializeStatsForEntity(LivingEntity entity) {
        EntityStats configStats = mobConfigStats.get(entity.getType());
        EntityStats statsToApply;

        if (configStats != null) {
            // Create a new instance based on the config template
            statsToApply = new EntityStats(
                    configStats.getMaxHealth(), configStats.getDefense(), configStats.getStrength(),
                    configStats.getSpeed(), configStats.getMaxMana(), configStats.getCritChance(),
                    configStats.getCritDamage()
            );
            log.finer("Applying configured stats to " + entity.getType());
        } else {
            // Use default stats if no configuration exists for this mob type
            statsToApply = EntityStats.createDefault();
            log.finer("Applying default stats to " + entity.getType());
            // We still might want to apply these defaults to override vanilla, especially health.
            // If you only want to modify mobs listed in mobs.yml, return null or handle differently here.
        }

        // Apply these stats as base values to the entity's attributes
        applyCustomStatsAsBaseAttributes(entity, statsToApply);

        // Put the applied stats object into the cache
        entityStatsCache.put(entity.getUniqueId(), statsToApply);
        return statsToApply;
    }

    private void applyCustomStatsAsBaseAttributes(LivingEntity entity, EntityStats stats) {
        // Max Health
        AttributeInstance healthInstance = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthInstance != null) {
            storeAndRemoveOriginalAttributeModifiers(entity, Attribute.GENERIC_MAX_HEALTH);
            healthInstance.setBaseValue(stats.getMaxHealth());
            // Set current health to the new max health, respecting Bukkit's potential cap
            entity.setHealth(Math.min(entity.getHealth(), stats.getMaxHealth()));
            // Ensure health is at least 1 if maxHealth allows
             if (stats.getMaxHealth() >= 1.0 && entity.getHealth() < 1.0) {
                 entity.setHealth(1.0);
             }
             // Set to max if current health is higher than new max
             if(entity.getHealth() > stats.getMaxHealth()){
                 entity.setHealth(stats.getMaxHealth());
             }
        } else {
            log.warning("Entity " + entity.getType() + " missing Max Health attribute!");
        }

        // Movement Speed
        AttributeInstance speedInstance = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedInstance != null) {
             // Only modify speed if the config has a non-zero value
            if (stats.getSpeed() != 0) {
                storeAndRemoveOriginalAttributeModifiers(entity, Attribute.GENERIC_MOVEMENT_SPEED);
                double defaultBaseSpeed = speedInstance.getDefaultValue(); // Get the mob's default speed
                // Apply the percentage change to the default speed
                double newBaseSpeed = defaultBaseSpeed * (1.0 + (stats.getSpeed() / 100.0));
                speedInstance.setBaseValue(Math.max(0.001, newBaseSpeed)); // Ensure speed doesn't become zero or negative
            } else {
                 // If speed is 0 in config, ensure no custom modifiers remain but keep vanilla base
                 storeAndRemoveOriginalAttributeModifiers(entity, Attribute.GENERIC_MOVEMENT_SPEED); // Clears our potential old ones
                 speedInstance.setBaseValue(speedInstance.getDefaultValue()); // Reset to default base
            }
        } else {
             log.finer("Entity " + entity.getType() + " missing Movement Speed attribute (might be normal for some).");
        }

        // TODO: Apply other stats like Attack Damage if needed in mobs.yml
        // AttributeInstance attackInstance = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        // if (attackInstance != null && stats.getStrength() != 0) { // Example: If strength directly sets base damage
        //     storeAndRemoveOriginalAttributeModifiers(entity, Attribute.GENERIC_ATTACK_DAMAGE);
        //     attackInstance.setBaseValue(stats.getStrength()); // Or apply as modifier depending on design
        // }
    }


    private void storeAndRemoveOriginalAttributeModifiers(LivingEntity entity, Attribute attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;

        UUID entityUuid = entity.getUniqueId();
        // Check if we haven't already stored modifiers for this entity and attribute
        if (!originalModifiers.containsKey(entityUuid) || !originalModifiers.get(entityUuid).containsKey(attribute)) {
            // Store a copy of current modifiers BEFORE removing them
            List<AttributeModifier> currentModifiersCopy = new ArrayList<>(instance.getModifiers());
             originalModifiers.computeIfAbsent(entityUuid, k -> new HashMap<>())
                    .put(attribute, currentModifiersCopy);
             log.finest("Stored " + currentModifiersCopy.size() + " original modifiers for " + attribute + " on " + entity.getType());

            // Remove all existing modifiers to apply a clean base value later
            for (AttributeModifier modifier : currentModifiersCopy) {
                try {
                    instance.removeModifier(modifier);
                } catch (Exception e) {
                    log.log(Level.FINEST, "Non-critical: Could not remove modifier " + modifier.getName() + " for " + attribute + " on " + entity.getType());
                }
            }
        } else {
             log.finest("Original modifiers for " + attribute + " on " + entity.getType() + " already stored. Skipping removal.");
             // If already stored, we might still need to clear CURRENT modifiers if they were added AFTER our initial store.
             // This is safer to handle during restoration.
        }
    }

   private void restoreOriginalAttributes(LivingEntity entity) {
        UUID entityUuid = entity.getUniqueId();
        Map<Attribute, List<AttributeModifier>> attributesToRestore = originalModifiers.get(entityUuid);
        if (attributesToRestore == null) {
            log.finer("No original attributes stored for " + entity.getType() + " (" + entityUuid + "), skipping restore.");
            return;
        }
         log.finer("Restoring original attributes for " + entity.getType() + " (" + entityUuid + ")");

        for (Map.Entry<Attribute, List<AttributeModifier>> entry : attributesToRestore.entrySet()) {
            Attribute attribute = entry.getKey();
            List<AttributeModifier> originalMods = entry.getValue();
            AttributeInstance instance = entity.getAttribute(attribute);

            if (instance == null) {
                 log.warning("Cannot restore attribute " + attribute + " for " + entity.getType() + ", instance is null.");
                 continue;
            }

            // 1. Remove ALL current modifiers to ensure a clean slate.
            // Iterate over a copy to avoid ConcurrentModificationException
            List<AttributeModifier> currentMods = new ArrayList<>(instance.getModifiers());
            for (AttributeModifier currentMod : currentMods) {
                try {
                    instance.removeModifier(currentMod);
                } catch (Exception e) {
                     log.log(Level.FINEST, "Non-critical: Error removing current modifier during restore: " + currentMod.getName() + " for " + attribute + " on " + entity.getType());
                }
            }

            // 2. Reset base value to the default vanilla value for that attribute.
            instance.setBaseValue(instance.getDefaultValue());

            // 3. Re-apply the stored original modifiers.
            for (AttributeModifier originalMod : originalMods) {
                try {
                    // Check if a modifier with the same UUID somehow exists (shouldn't after step 1, but belt-and-suspenders)
                    boolean alreadyPresent = false;
                    for(AttributeModifier existingMod : instance.getModifiers()){
                        if(existingMod.getUniqueId().equals(originalMod.getUniqueId())){
                            alreadyPresent = true;
                            break;
                        }
                    }
                    if (!alreadyPresent) {
                        instance.addModifier(originalMod);
                         log.finest("Re-applied original modifier " + originalMod.getName() + " for " + attribute);
                    } else {
                         log.finest("Skipped re-applying duplicate original modifier " + originalMod.getName() + " for " + attribute);
                    }
                } catch (IllegalArgumentException e) {
                    log.warning("Could not re-apply original modifier " + originalMod.getName() + " for " + attribute + " on " + entity.getType() + ": " + e.getMessage());
                } catch (Exception e) {
                     log.log(Level.SEVERE, "Unexpected error re-applying original modifier " + originalMod.getName() + " for " + attribute + " on " + entity.getType(), e);
                }
            }
        }

        // After restoring health attribute, ensure current health is capped by the new max health.
        AttributeInstance healthInstance = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthInstance != null && entity.getHealth() > healthInstance.getValue()) {
            entity.setHealth(healthInstance.getValue());
        }
         log.finer("Finished restoring attributes for " + entity.getType() + " (" + entityUuid + ")");
    }
}

