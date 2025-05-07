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
import java.util.logging.Level;

public class EntityStatsManager {

    private final MMOCraft plugin;
    private final Map<UUID, EntityStats> entityStatsCache = new HashMap<>();
    private final Map<EntityType, EntityStats> mobConfigStats = new HashMap<>();
    private File mobsFile;
    private FileConfiguration mobsConfig;
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
        mobsConfig = YamlConfiguration.loadConfiguration(mobsFile);
        ConfigurationSection mobsSection = mobsConfig.getConfigurationSection("mobs");
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
                    plugin.getLogger().info("Loaded stats for mob type: " + type);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid entity type '" + entityTypeName + "' in mobs.yml.");
            }
        }
        plugin.getLogger().info("Custom mob stats loaded from mobs.yml.");
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
            restoreOriginalAttributes(entity);
        }
    }

    private void storeAndRemoveOriginalAttributeModifiers(LivingEntity entity, Attribute attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;

        List<AttributeModifier> currentModifiers = new ArrayList<>(instance.getModifiers());
        originalModifiers.computeIfAbsent(entity.getUniqueId(), k -> new HashMap<>())
                .put(attribute, new ArrayList<>(currentModifiers));

        for (AttributeModifier modifier : currentModifiers) {
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
            entity.setHealth(stats.getMaxHealth());
        }

        AttributeInstance speedInstance = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedInstance != null && stats.getSpeed() != 0) { // Only modify if custom speed is set
            storeAndRemoveOriginalAttributeModifiers(entity, Attribute.GENERIC_MOVEMENT_SPEED); // Store/Remove existing
            double defaultBaseSpeed = speedInstance.getDefaultValue(); // CORRECTED
            // Assuming speed stat is a percentage change from default for this example:
            double newBaseSpeed = defaultBaseSpeed * (1.0 + (stats.getSpeed() / 100.0));
            speedInstance.setBaseValue(newBaseSpeed);
        }
    }

    private void restoreOriginalAttributes(LivingEntity entity) {
        Map<Attribute, List<AttributeModifier>> attributesToRestore = originalModifiers.remove(entity.getUniqueId());
        if (attributesToRestore == null) return;

        for (Map.Entry<Attribute, List<AttributeModifier>> entry : attributesToRestore.entrySet()) {
            AttributeInstance instance = entity.getAttribute(entry.getKey());
            if (instance == null) continue;

            new ArrayList<>(instance.getModifiers()).forEach(mod -> {
                try {instance.removeModifier(mod);} catch (Exception e) {}
            });

            instance.setBaseValue(instance.getDefaultValue()); // CORRECTED

            for (AttributeModifier modifier : entry.getValue()) {
                try {
                    if(!isModifierPresent(instance, modifier.getUniqueId())){
                        instance.addModifier(modifier);
                    }
                } catch (IllegalArgumentException e) {
                    // plugin.getLogger().warning("Could not re-apply original modifier " + modifier.getName() + " for " + entry.getKey() + " on " + entity.getType() + ": " + e.getMessage());
                }
            }
        }
        if (attributesToRestore.containsKey(Attribute.GENERIC_MAX_HEALTH)) {
            AttributeInstance healthInstance = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (healthInstance != null) entity.setHealth(healthInstance.getValue());
        }
    }

    private boolean isModifierPresent(AttributeInstance instance, UUID modifierUUID) {
        for (AttributeModifier mod : instance.getModifiers()) {
            if (mod.getUniqueId().equals(modifierUUID)) {
                return true;
            }
        }
        return false;
    }

    public void initializeStatsForEntity(LivingEntity entity) {
        EntityStats configStats = mobConfigStats.get(entity.getType());
        EntityStats statsToApply;

        if (configStats != null) {
            statsToApply = new EntityStats(
                    configStats.getMaxHealth(), configStats.getDefense(), configStats.getStrength(),
                    configStats.getSpeed(), configStats.getMaxMana(), configStats.getCritChance(),
                    configStats.getCritDamage()
            );
        } else {
            statsToApply = EntityStats.base();
        }
        entityStatsCache.put(entity.getUniqueId(), statsToApply);
        applyCustomStatsAsBaseAttributes(entity, statsToApply);
    }

    public EntityStats getStats(LivingEntity entity) {
        if (entity == null) return EntityStats.base();
        return entityStatsCache.computeIfAbsent(entity.getUniqueId(), uuid -> {
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
            applyCustomStatsAsBaseAttributes(entity, calculatedStats);
            return calculatedStats;
        });
    }
}