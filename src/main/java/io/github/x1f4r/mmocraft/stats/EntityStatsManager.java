package io.github.x1f4r.mmocraft.stats;

import io.github.x1f4r.mmocraft.MMOCraft;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class EntityStatsManager {

    private final MMOCraft plugin;
    private final Map<UUID, EntityStats> entityStatsCache = new HashMap<>();
    private final Map<EntityType, EntityStats> defaultMobStats = new HashMap<>();
    private final Map<UUID, AttributeModifier> mobSpeedModifiers = new HashMap<>();
    private static final String MOB_SPEED_MODIFIER_UUID_NAMESPACE = "mmocraft_mob_speed_"; // Ensure unique


    public EntityStatsManager(MMOCraft plugin) {
        this.plugin = plugin;
        loadDefaultMobStats();
    }

    private void loadDefaultMobStats() {
        defaultMobStats.clear(); // Clear before reloading
        File mobsFile = new File(plugin.getDataFolder(), "mobs.yml");
        if (!mobsFile.exists()) {
            plugin.getLogger().info("mobs.yml not found, saving default. Please configure mob stats in mobs.yml.");
            plugin.saveResource("mobs.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(mobsFile);
        ConfigurationSection mobsSection = config.getConfigurationSection("mobs");
        if (mobsSection == null) {
            plugin.getLogger().warning("Could not find 'mobs' section in mobs.yml! Mobs will have vanilla stats unless overridden by other means.");
            return;
        }

        for (String mobKey : mobsSection.getKeys(false)) {
            try {
                EntityType type = EntityType.valueOf(mobKey.toUpperCase());
                ConfigurationSection mobConfig = mobsSection.getConfigurationSection(mobKey);
                if (mobConfig != null) {
                    double maxHealth = mobConfig.getDouble("maxHealth", 20.0);
                    int defense = mobConfig.getInt("defense", 0);
                    int strength = mobConfig.getInt("strength", 0);
                    int speed = mobConfig.getInt("speed", 0);
                    int maxMana = mobConfig.getInt("maxMana", 0);
                    int critChance = mobConfig.getInt("critChance", 0);
                    int critDamage = mobConfig.getInt("critDamage", 0); // Default crit damage for mobs can be 0 or 50 like players

                    EntityStats stats = new EntityStats(maxHealth, defense, strength, speed, maxMana, critChance, critDamage);
                    defaultMobStats.put(type, stats);
                    // plugin.getLogger().fine("Loaded default stats for mob type: " + type.name());
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid EntityType key in mobs.yml: " + mobKey);
            }
        }
        plugin.getLogger().info("Finished loading " + defaultMobStats.size() + " default mob stat configurations.");
    }

    public EntityStats getStats(LivingEntity entity) {
        if (entity instanceof Player) {
            // This manager is primarily for non-player entities regarding stat definition.
            // Player stats are managed by PlayerStatsManager and influenced by items/armor.
            // However, a player IS a LivingEntity, so for damage calculations involving a player as victim/attacker,
            // the damage listener might query PlayerStatsManager.getStats(player) instead.
            // This getStats here should mainly be for mobs.
            plugin.getLogger().log(Level.WARNING, "EntityStatsManager.getStats called for a Player. This is unexpected for mob stat management.");
            return null; // Or handle appropriately if this manager should also wrap player stats for some reason.
        }
        // For mobs, compute if absent to ensure they get stats upon first query after spawn.
        return entityStatsCache.computeIfAbsent(entity.getUniqueId(), uuid -> initializeStatsForEntity(entity));
    }

    private EntityStats initializeStatsForEntity(LivingEntity entity) {
        EntityStats baseStatsToCopy = defaultMobStats.get(entity.getType());
        if (baseStatsToCopy == null) {
            // plugin.getLogger().fine("No default stats configured for " + entity.getType() + ". Creating generic base stats.");
            baseStatsToCopy = EntityStats.base();
        }
        // Create a new instance for the cache, copying values from the default template
        EntityStats newEntityStats = new EntityStats(
                baseStatsToCopy.getMaxHealth(), baseStatsToCopy.getDefense(), baseStatsToCopy.getStrength(),
                baseStatsToCopy.getSpeed(), baseStatsToCopy.getMaxMana(), baseStatsToCopy.getCritChance(), baseStatsToCopy.getCritDamage()
        );

        applyVanillaAttributes(entity, newEntityStats); // Apply to the live entity
        return newEntityStats;
    }

    public void applyVanillaAttributes(LivingEntity entity, EntityStats stats) {
        if (entity == null || stats == null || entity instanceof Player) {
            // Do not manage Player attributes here; PlayerStatsManager handles player speed/health attributes.
            return;
        }

        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(stats.getMaxHealth());
            entity.setHealth(stats.getMaxHealth()); // Set current health to max
        }

        AttributeInstance speedAttr = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            UUID modifierUUID = UUID.nameUUIDFromBytes((MOB_SPEED_MODIFIER_UUID_NAMESPACE + entity.getUniqueId().toString()).getBytes());

            AttributeModifier oldModifier = mobSpeedModifiers.remove(entity.getUniqueId());
            if (oldModifier != null) {
                removeModifier(speedAttr, modifierUUID);
            }

            if (stats.getSpeed() != 0) { // Assuming 0 means no change from mob's base vanilla speed
                double modifierAmount = (double) stats.getSpeed() / 100.0; // Convert percentage to multiplier
                AttributeModifier newModifier = new AttributeModifier(
                        modifierUUID,
                        "mmocraft_mob_speed_buff", // Name of the modifier
                        modifierAmount,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1 // NewValue = BaseValue * (1 + Amount)
                );
                if (!hasModifier(speedAttr, modifierUUID)) { // Avoid duplicate modifiers
                    speedAttr.addModifier(newModifier);
                    mobSpeedModifiers.put(entity.getUniqueId(), newModifier);
                }
            }
        }

        // Control GENERIC_ATTACK_DAMAGE based on strength stat
        // This makes the 'strength' stat directly set the mob's base vanilla attack damage.
        // If strength is intended as an additive bonus, this should be handled in the damage listener.
        AttributeInstance attackDamageAttr = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attackDamageAttr != null) {
            // If strength from mobs.yml should be the mob's raw attack damage
            if (stats.getStrength() > 0) { // Only apply if strength is positive for base damage
                attackDamageAttr.setBaseValue(stats.getStrength());
            }
            // If strength is just a bonus, don't set base attack damage here.
            // The damage listener will add stats.getStrength() to damage.
        }
    }


    public void registerEntity(LivingEntity entity) {
        if (entity instanceof Player) return;
        if (!entityStatsCache.containsKey(entity.getUniqueId())) {
            // plugin.getLogger().fine("Registering entity " + entity.getType() + " (" + entity.getUniqueId() + ") with EntityStatsManager.");
            EntityStats stats = initializeStatsForEntity(entity);
            entityStatsCache.put(entity.getUniqueId(), stats);
        }
    }

    public void unregisterEntity(LivingEntity entity) {
        if (entity instanceof Player) return;
        // plugin.getLogger().fine("Unregistering entity " + entity.getType() + " (" + entity.getUniqueId() + ") from EntityStatsManager.");
        entityStatsCache.remove(entity.getUniqueId());

        AttributeInstance speedAttr = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            AttributeModifier currentModifier = mobSpeedModifiers.remove(entity.getUniqueId());
            if (currentModifier != null) {
                removeModifier(speedAttr, currentModifier.getUniqueId());
            }
        }
    }

    private void removeModifier(AttributeInstance attributeInstance, UUID modifierUuid) {
        if (attributeInstance == null || modifierUuid == null) return;
        AttributeModifier toRemove = null;
        for (AttributeModifier modifier : attributeInstance.getModifiers()) {
            if (modifier.getUniqueId().equals(modifierUuid)) {
                toRemove = modifier;
                break;
            }
        }
        if (toRemove != null) {
            try { attributeInstance.removeModifier(toRemove); }
            catch (IllegalStateException e) { /* ignore */ }
        }
    }

    private boolean hasModifier(AttributeInstance attributeInstance, UUID modifierUuid) {
        if (attributeInstance == null || modifierUuid == null) return false;
        for (AttributeModifier modifier : attributeInstance.getModifiers()) {
            if (modifier.getUniqueId().equals(modifierUuid)) {
                return true;
            }
        }
        return false;
    }

    public void reloadMobsConfig() {
        plugin.getLogger().info("Reloading mobs.yml configuration...");
        loadDefaultMobStats(); // This clears and reloads defaultMobStats

        // Re-apply stats to currently managed online non-player entities
        // Iterate over a copy of keys to avoid ConcurrentModificationException if an entity despawns during this
        for (UUID entityId : new java.util.ArrayList<>(entityStatsCache.keySet())) {
            LivingEntity entity = (LivingEntity) plugin.getServer().getEntity(entityId);
            if (entity != null && entity.isValid() && !(entity instanceof Player)) {
                // plugin.getLogger().fine("Re-applying stats to cached entity: " + entity.getType());
                EntityStats newStats = initializeStatsForEntity(entity); // This re-reads from new defaults and applies attributes
                entityStatsCache.put(entityId, newStats); // Update the cache with the potentially new stat object
            } else {
                // Entity is no longer valid or became a player somehow, remove from mob cache
                entityStatsCache.remove(entityId);
                mobSpeedModifiers.remove(entityId);
            }
        }
        plugin.getLogger().info("Mobs configuration reloaded and stats reapplied to online mobs.");
    }
}