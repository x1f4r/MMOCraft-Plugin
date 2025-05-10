package io.github.x1f4r.mmocraft.display;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.ArrayList; // <<< ADDED IMPORT
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class DamageAndHealthDisplayManager implements Listener { // Implement Listener

    private final MMOPlugin plugin;
    private final Logger log;

    private final Map<UUID, ArmorStand> healthBars = new ConcurrentHashMap<>();
    private static final String HEALTH_BAR_METADATA_KEY = "mmocraft_health_bar";
    private static final String DAMAGE_INDICATOR_METADATA_KEY = "mmocraft_damage_indicator";
    private final DecimalFormat damageFormat = new DecimalFormat("#,##0.#"); // Format with commas
    private final DecimalFormat healthFormat = new DecimalFormat("#,##0"); // Format with commas

    private final double healthBarYOffset; // = 0.6; // Configurable?
    private final int healthBarUpdateInterval; // = 5; // Ticks
    private final int damageIndicatorDuration; // = 30; // Ticks
    private final double damageIndicatorRisePerTick; // = 0.05;

    public DamageAndHealthDisplayManager(MMOCore core) {
        this.plugin = core.getPlugin();
        this.log = MMOPlugin.getMMOLogger();
        // this.entityStatsManager = core.getEntityStatsManager(); // Get manager from core

        // Load config values
        this.healthBarYOffset = plugin.getConfig().getDouble("display.health_bar.y_offset", 0.6);
        this.healthBarUpdateInterval = plugin.getConfig().getInt("display.health_bar.update_interval_ticks", 5);
        this.damageIndicatorDuration = plugin.getConfig().getInt("display.damage_indicator.duration_ticks", 30);
        this.damageIndicatorRisePerTick = plugin.getConfig().getDouble("display.damage_indicator.rise_per_tick", 0.05);
    }

    public void initialize() {
        startHealthBarUpdaterTask();
        log.info("DamageAndHealthDisplayManager initialized.");
    }

    // --- Floating Damage Numbers ---

    public void createFloatingDamageIndicator(LivingEntity victim, double damage, boolean isCrit, boolean isTrueDamage) {
        if (victim == null || victim.isDead() || damage <= 0 || victim instanceof ArmorStand) {
            return;
        }

        Location loc = victim.getLocation().add(
                (Math.random() * 0.8) - 0.4, // Random X offset
                victim.getHeight() * 0.8 + (Math.random() * 0.4), // Slightly above midpoint + random Y
                (Math.random() * 0.8) - 0.4  // Random Z offset
        );

        // Ensure the armor stand doesn't spawn inside blocks
        if (!loc.getBlock().isPassable()) {
            loc = victim.getEyeLocation().add(0, 0.5, 0); // Fallback spawn location
        }


        ArmorStand armorStand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setSmall(true);
            as.setMarker(true); // Essential: prevents interaction and collision
            as.setInvulnerable(true);
            as.setPersistent(false); // Don't save to disk
            as.setMetadata(DAMAGE_INDICATOR_METADATA_KEY, new FixedMetadataValue(plugin, true));

            Component damageTextComponent;
            // Using brighter colors and bold for crits
            if (isTrueDamage) {
                // White with maybe subtle markers
                damageTextComponent = Component.text("✧" + damageFormat.format(damage) + "✧", NamedTextColor.WHITE); // Changed marker
            } else if (isCrit) {
                // Bright Yellow/Gold with bold and crit markers
                damageTextComponent = Component.text("✧", NamedTextColor.GOLD, TextDecoration.BOLD)
                                .append(Component.text(damageFormat.format(damage), NamedTextColor.YELLOW, TextDecoration.BOLD))
                                .append(Component.text("✧", NamedTextColor.GOLD, TextDecoration.BOLD)); // Changed marker
            } else {
                // Standard Gray
                damageTextComponent = Component.text(damageFormat.format(damage), NamedTextColor.GRAY);
            }
            as.customName(damageTextComponent);
            as.setCustomNameVisible(true);
        });

        new BukkitRunnable() {
            private int ticksLived = 0;

            @Override
            public void run() {
                if (ticksLived >= damageIndicatorDuration || !armorStand.isValid() || armorStand.isDead()) {
                    if (armorStand.isValid()) armorStand.remove();
                    this.cancel();
                    return;
                }
                // Move slightly up each tick
                armorStand.teleport(armorStand.getLocation().add(0, damageIndicatorRisePerTick, 0));
                ticksLived++;
            }
        }.runTaskTimer(plugin, 0L, 1L); // Run every tick
    }

    // --- Health Indicators ---

    // Listener method now part of this class
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Ignore players, armor stands, and specific non-mob entities if needed
        if (event.getEntity() instanceof Player || event.getEntity() instanceof ArmorStand) {
            return;
        }
        // Also check if health bars are enabled in config?
        // if (!plugin.getConfig().getBoolean("display.health_bar.enabled", true)) return;

        // Delay slightly to allow EntityStatsManager to potentially process the entity first
        final LivingEntity spawnedEntity = event.getEntity();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (spawnedEntity.isValid() && !spawnedEntity.isDead()) {
                addHealthBar(spawnedEntity);
            }
        }, 2L); // 2 ticks delay
    }

    // Listener method now part of this class
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity && !(event.getEntity() instanceof Player || event.getEntity() instanceof ArmorStand)) {
            LivingEntity victim = (LivingEntity) event.getEntity();
            // Update health bar on the next tick to reflect the damage taken
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (victim.isValid() && !victim.isDead()) {
                    updateHealthBar(victim);
                } else {
                    // If damage killed the entity, ensure bar is removed
                    removeHealthBar(victim.getUniqueId());
                }
            }, 1L);
        }
    }

    // Listener method now part of this class
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof LivingEntity && !(event.getEntity() instanceof Player)) {
            removeHealthBar(event.getEntity().getUniqueId());
        }
    }

    // Listener method now part of this class
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            // Check if it's an entity we might have a health bar for
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                ArmorStand healthBar = healthBars.remove(entity.getUniqueId());
                if (healthBar != null && healthBar.isValid()) {
                    healthBar.remove();
                    log.finer("Removed health bar for entity in unloaded chunk: " + entity.getUniqueId());
                }
            }
            // Also clean up any stray damage indicators in the chunk
            if (entity instanceof ArmorStand && entity.hasMetadata(DAMAGE_INDICATOR_METADATA_KEY)) {
                if (entity.isValid()) {
                    entity.remove();
                    log.finer("Removed stray damage indicator in unloaded chunk: " + entity.getUniqueId());
                }
            }
        }
    }

    private void addHealthBar(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.isDead() || healthBars.containsKey(entity.getUniqueId())) {
            return;
        }
        log.finer("Adding health bar for: " + entity.getType() + " (" + entity.getUniqueId() + ")");

        // Ensure stats are loaded by EntityStatsManager (should happen via EntitySpawnListener ideally)
        // EntityStats stats = entityStatsManager.getStats(entity); // This ensures initialization

        Location barLocation = entity.getLocation().add(0, entity.getHeight() + healthBarYOffset, 0);

        ArmorStand healthBar = entity.getWorld().spawn(barLocation, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setSmall(true);
            as.setMarker(true);
            as.setInvulnerable(true);
            as.setCustomNameVisible(true);
            as.setPersistent(false);
            as.setMetadata(HEALTH_BAR_METADATA_KEY, new FixedMetadataValue(plugin, true));
        });

        healthBars.put(entity.getUniqueId(), healthBar);
        updateHealthBarInternal(entity, healthBar); // Perform initial update
    }

    private void updateHealthBar(LivingEntity entity) {
        ArmorStand healthBar = healthBars.get(entity.getUniqueId());
        if (healthBar != null && healthBar.isValid() && entity.isValid() && !entity.isDead()) {
            updateHealthBarInternal(entity, healthBar);
        } else if (healthBar != null) { // If bar exists but entity is invalid/dead
            removeHealthBar(entity.getUniqueId());
        }
        // If bar doesn't exist but entity is valid, maybe recreate it? Or assume addHealthBar handles it.
    }

    private void updateHealthBarInternal(LivingEntity entity, ArmorStand healthBar) {
        if (entity == null || !entity.isValid() || healthBar == null || !healthBar.isValid()) {
            if(healthBar != null && healthBar.isValid()) healthBar.remove(); // Clean up bar if entity is gone
            if(entity != null) healthBars.remove(entity.getUniqueId()); // Remove from map
            return;
        }

        // Get Bukkit attributes first
        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double currentHealth = entity.getHealth();
        double maxHealth = (maxHealthAttr != null) ? maxHealthAttr.getValue() : 20.0; // Bukkit's current max health

        // Note: We rely on EntityStatsManager having already SET the base health attribute.
        // We display based on the entity's CURRENT Bukkit health attributes.
        // EntityStats stats = entityStatsManager.getStats(entity); // Get our cached stats if needed for display logic


        // Clamp current health just in case
        currentHealth = Math.max(0, Math.min(currentHealth, maxHealth));

        NamedTextColor healthColor;
        double percentage = (maxHealth > 0) ? (currentHealth / maxHealth) : 0;
        if (percentage > 0.75) healthColor = NamedTextColor.GREEN;
        else if (percentage > 0.40) healthColor = NamedTextColor.YELLOW;
        else if (percentage > 0.15) healthColor = NamedTextColor.RED;
        else healthColor = NamedTextColor.DARK_RED;

        Component entityNameDisplay = Component.empty();
        Component currentCustomName = entity.customName();
        if (currentCustomName != null && !LegacyComponentSerializer.legacySection().serialize(currentCustomName).isEmpty()) {
            entityNameDisplay = currentCustomName.append(Component.text(" ")); // Add space after name
        }

        Component healthTextComponent = Component.text(healthFormat.format(currentHealth), healthColor)
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(healthFormat.format(maxHealth), NamedTextColor.GREEN));

        healthBar.customName(entityNameDisplay.append(healthTextComponent));

        // Teleport the bar to follow the entity
        Location newBarLocation = entity.getLocation().add(0, entity.getHeight() + healthBarYOffset, 0);
        // Optimization: Only teleport if location significantly changed? Maybe not needed with marker ArmorStands.
        healthBar.teleport(newBarLocation);
    }


    private void removeHealthBar(UUID entityId) {
        ArmorStand healthBar = healthBars.remove(entityId);
        if (healthBar != null && healthBar.isValid()) {
            healthBar.remove();
            log.finer("Removed health bar for entity: " + entityId);
        }
    }

    public void cleanupOnDisable() {
        log.info("Cleaning up health bars and damage indicators...");
        // Remove all managed health bars
        for (ArmorStand healthBar : healthBars.values()) {
            if (healthBar != null && healthBar.isValid()) {
                healthBar.remove();
            }
        }
        healthBars.clear();

        // Also remove any stray damage indicators across all worlds
        Bukkit.getWorlds().forEach(world -> {
            world.getEntitiesByClass(ArmorStand.class).forEach(as -> {
                if (as.hasMetadata(DAMAGE_INDICATOR_METADATA_KEY)) {
                    if (as.isValid()) as.remove();
                }
                // Optional: Clean up health bars missed by the map?
                // if (as.hasMetadata(HEALTH_BAR_METADATA_KEY)) {
                //    if (as.isValid()) as.remove();
                // }
            });
        });
        log.info("Display cleanup finished.");
    }

    private void startHealthBarUpdaterTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Iterate over a copy of the keys to allow safe removal within the loop
                for (UUID entityId : new ArrayList<>(healthBars.keySet())) { // <<< USES IMPORT
                    ArmorStand healthBar = healthBars.get(entityId);
                    Entity entity = Bukkit.getEntity(entityId); // More efficient than iterating all world entities

                    if (entity instanceof LivingEntity && entity.isValid() && !entity.isDead() && healthBar != null && healthBar.isValid()) {
                        // Entity and bar are valid, update the bar
                        updateHealthBarInternal((LivingEntity) entity, healthBar);
                    } else {
                        // Entity is invalid, dead, or bar is invalid - remove the entry
                        removeHealthBar(entityId);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, healthBarUpdateInterval); // Initial delay, then repeat interval
    }
}
