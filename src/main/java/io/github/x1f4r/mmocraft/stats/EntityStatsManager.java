package io.github.x1f4r.mmocraft.stats;

import io.github.x1f4r.mmocraft.MMOCraft;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
// import java.util.logging.Level; // Only if detailed logging is needed

public class EntityStatsManager {

    private final MMOCraft plugin;
    private final Map<UUID, EntityStats> entityStatsCache = new HashMap<>();
    private final Map<EntityType, EntityStats> mobConfigStats = new HashMap<>();
    private File mobsFile;
    // private FileConfiguration mobsConfig; // Not strictly needed as a field if only loaded once or in reload
    private final Map<UUID, Map<Attribute, List<AttributeModifier>>> originalModifiers = new HashMap<>();

    public EntityStatsManager(MMOCraft plugin) {
        this.plugin = plugin;
        this.mobsFile = new File(plugin.getDataFolder(), "mobs.yml");
        if (!mobsFile.exists()) {
            plugin.saveResource("mobs.yml", false);
        }
        reloadMobsConfig();
    }

    public void reloadMobsConfig() {
        mobConfigStats.clear();
        if (!mobsFile.exists()) {
            plugin.getLogger().warning("mobs.yml not found. Cannot load custom mob stats.");
            return;
        }
        FileConfiguration mobsConfigInstance = YamlConfiguration.loadConfiguration(mobsFile); // Local instance
        ConfigurationSection mobsSection = mobsConfigInstance.getConfigurationSection("mobs");
        if (mobsSection == null) {
            plugin.getLogger().info("No 'mobs' section in mobs.yml or file is empty.");
            return;
        }

        for (String entityTypeName : mobsSection.getKeys(false)) {
            try {
                EntityType type = EntityType.valueOf(entityTypeName.toUpperCase());
                ConfigurationSection statsConf = mobsSection.getConfigurationSection(entityTypeName);
                if (statsConf != null) {
                    EntityStats stats = new EntityStats(
                            statsConf.getDouble("maxHealth", 20.0),
                            statsConf.getInt("defense", 0),
                            statsConf.getInt("strength", 0),
                            statsConf.getInt("speed", 0),
                            statsConf.getInt("maxMana", 0),
                            statsConf.getInt("critChance", 0),
                            statsConf.getInt("critDamage", 0)
                    );
                    mobConfigStats.put(type, stats);
                    // plugin.getLogger().info("Loaded stats for mob type: " + type); // Can be verbose
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid entity type '" + entityTypeName + "' in mobs.yml.");
            }
        }
        plugin.getLogger().info("Custom mob stats (re)loaded from mobs.yml: " + mobConfigStats.size() + " types configured.");
    }

    public void registerEntity(LivingEntity entity) {
        if (entity == null || entityStatsCache.containsKey(entity.getUniqueId())) {
            return;
        }
        initializeStatsForEntity(entity);
    }

    public void unregisterEntity(LivingEntity entity) {
        if (entity != null) {
            entityStatsCache.remove(entity.getUniqueId());
            // Only restore attributes if the entity isn't already fully dead and processed by vanilla.
            // The EntityDeathEvent should handle drops and XP, vanilla handles the actual removal.
            // Restoring attributes on an entity that is truly "dead" can cause issues.
            if (!entity.isDead()) { // MOB DEATH FIX: Check if entity is already dead
                restoreOriginalAttributes(entity);
            }
            originalModifiers.remove(entity.getUniqueId()); // Clean up stored original modifiers regardless
        }
    }

    private void storeAndRemoveOriginalAttributeModifiers(LivingEntity entity, Attribute attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;

        // Store a copy of current modifiers before removing them
        List<AttributeModifier> currentModifiersCopy = new ArrayList<>(instance.getModifiers());
        originalModifiers.computeIfAbsent(entity.getUniqueId(), k -> new HashMap<>())
                .put(attribute, currentModifiersCopy);

        // Remove all modifiers to apply a clean base value
        for (AttributeModifier modifier : currentModifiersCopy) { // Iterate over the copy
            try {
                instance.removeModifier(modifier);
            } catch (Exception e) {
                // plugin.getLogger().log(Level.FINEST, "Could not remove modifier " + modifier.getName() + " for " + attribute.toString() + " on " + entity.getType());
            }
        }
    }

    private void applyCustomStatsAsBaseAttributes(LivingEntity entity, EntityStats stats) {
        AttributeInstance healthInstance = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthInstance != null) {
            storeAndRemoveOriginalAttributeModifiers(entity, Attribute.GENERIC_MAX_HEALTH);
            healthInstance.setBaseValue(stats.getMaxHealth());
            entity.setHealth(stats.getMaxHealth()); // Set current health to new max health
        }

        AttributeInstance speedInstance = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedInstance != null && stats.getSpeed() != 0) {
            storeAndRemoveOriginalAttributeModifiers(entity, Attribute.GENERIC_MOVEMENT_SPEED);
            double defaultBaseSpeed = speedInstance.getDefaultValue();
            double newBaseSpeed = defaultBaseSpeed * (1.0 + (stats.getSpeed() / 100.0));
            speedInstance.setBaseValue(newBaseSpeed);
        }
        // Other attributes like attack damage can be handled similarly if needed
    }

    private void restoreOriginalAttributes(LivingEntity entity) {
        // MOB DEATH FIX: If entity is already dead, don't try to modify its attributes further.
        if (entity.isDead()) {
            // plugin.getLogger().fine("Skipping attribute restoration for already dead entity: " + entity.getUniqueId());
            return;
        }

        Map<Attribute, List<AttributeModifier>> attributesToRestore = originalModifiers.get(entity.getUniqueId());
        if (attributesToRestore == null) return;

        for (Map.Entry<Attribute, List<AttributeModifier>> entry : attributesToRestore.entrySet()) {
            AttributeInstance instance = entity.getAttribute(entry.getKey());
            if (instance == null) continue;

            // Clear any modifiers that might have been added by this plugin or others since storing
            new ArrayList<>(instance.getModifiers()).forEach(mod -> {
                try {instance.removeModifier(mod);} catch (Exception e) { /* Log if needed */ }
            });

            // Reset to default base value first
            instance.setBaseValue(instance.getDefaultValue());

            // Re-apply original modifiers
            for (AttributeModifier modifier : entry.getValue()) {
                try {
                    // Check if a modifier with the same UUID already exists to prevent duplicates
                    // This check might be redundant if all modifiers were cleared properly above.
                    boolean present = false;
                    for(AttributeModifier existingMod : instance.getModifiers()){
                        if(existingMod.getUniqueId().equals(modifier.getUniqueId())){
                            present = true;
                            break;
                        }
                    }
                    if(!present){
                        instance.addModifier(modifier);
                    }
                } catch (IllegalArgumentException e) {
                    // plugin.getLogger().warning("Could not re-apply original modifier " + modifier.getName() + " for " + entry.getKey() + " on " + entity.getType() + ": " + e.getMessage());
                }
            }
        }
        // After restoring health attribute, ensure current health is capped by the new max health.
        AttributeInstance healthInstance = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthInstance != null && entity.getHealth() > healthInstance.getValue()) {
            entity.setHealth(healthInstance.getValue());
        }
    }


    public void initializeStatsForEntity(LivingEntity entity) {
        EntityStats configStats = mobConfigStats.get(entity.getType());
        EntityStats statsToApply;

        if (configStats != null) {
            statsToApply = new EntityStats( // Create a new instance from config
                    configStats.getMaxHealth(), configStats.getDefense(), configStats.getStrength(),
                    configStats.getSpeed(), configStats.getMaxMana(), configStats.getCritChance(),
                    configStats.getCritDamage()
            );
        } else {
            statsToApply = EntityStats.base(); // Use base if no config
        }
        entityStatsCache.put(entity.getUniqueId(), statsToApply);
        applyCustomStatsAsBaseAttributes(entity, statsToApply); // Apply to entity
    }

    public EntityStats getStats(LivingEntity entity) {
        if (entity == null) return EntityStats.base(); // Should not happen if called correctly
        // Compute if absent ensures that initializeStatsForEntity is called if not in cache
        return entityStatsCache.computeIfAbsent(entity.getUniqueId(), uuid -> {
            // This lambda is called if the entity is not in the cache.
            // It should initialize and return the stats.
            EntityStats calculatedStats;
            EntityStats configStats = mobConfigStats.get(entity.getType());
            if (configStats != null) {
                calculatedStats = new EntityStats(
                        configStats.getMaxHealth(), configStats.getDefense(), configStats.getStrength(),
                        configStats.getSpeed(), configStats.getMaxMana(), configStats.getCritChance(),
                        configStats.getCritDamage()
                );
            } else {
                calculatedStats = EntityStats.base();
            }
            // Apply these stats to the live entity's attributes
            applyCustomStatsAsBaseAttributes(entity, calculatedStats);
            return calculatedStats;
        });
    }
}
