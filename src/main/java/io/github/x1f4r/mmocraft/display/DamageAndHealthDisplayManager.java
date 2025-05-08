package io.github.x1f4r.mmocraft.display;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.stats.EntityStats;
import io.github.x1f4r.mmocraft.stats.EntityStatsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.CreatureSpawnEvent; // Corrected import if it was different
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DamageAndHealthDisplayManager implements Listener {

    private final MMOCraft plugin;
    private final EntityStatsManager entityStatsManager;
    private final Map<UUID, ArmorStand> healthBars = new ConcurrentHashMap<>();
    private static final String HEALTH_BAR_METADATA_KEY = "mmocraft_health_bar";
    private static final String DAMAGE_INDICATOR_METADATA_KEY = "mmocraft_damage_indicator";
    private final DecimalFormat damageFormat = new DecimalFormat("#.#");
    private final DecimalFormat healthFormat = new DecimalFormat("#");

    private final double healthBarYOffset = 0.5; // Adjust as needed, how high above the mob's head

    public DamageAndHealthDisplayManager(MMOCraft plugin) {
        this.plugin = plugin;
        this.entityStatsManager = plugin.getEntityStatsManager();
        // plugin.getServer().getPluginManager().registerEvents(this, plugin); // Registered in MMOCraft main
        startHealthBarUpdaterTask();
    }

    // --- Floating Damage Numbers ---

    public void createFloatingDamageIndicator(LivingEntity victim, double damage, boolean isCrit, boolean isTrueDamage) {
        if (victim == null || victim.isDead() || damage <= 0) {
            return;
        }

        Location loc = victim.getLocation().add(
                (Math.random() * 0.6) - 0.3, // Random X offset
                victim.getHeight() * 0.75 + (Math.random() * 0.3), // Slightly above midpoint + random Y
                (Math.random() * 0.6) - 0.3  // Random Z offset
        );

        ArmorStand armorStand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setSmall(true);
            as.setMarker(true);
            as.setInvulnerable(true);
            as.setMetadata(DAMAGE_INDICATOR_METADATA_KEY, new FixedMetadataValue(plugin, true));

            String damageText;
            if (isTrueDamage) {
                damageText = ChatColor.WHITE + "✧" + damageFormat.format(damage) + "✧";
            } else if (isCrit) {
                damageText = ChatColor.GOLD + "✧" + ChatColor.YELLOW + damageFormat.format(damage) + ChatColor.GOLD + "✧";
            } else {
                damageText = ChatColor.GRAY + damageFormat.format(damage);
            }
            as.setCustomName(damageText);
            as.setCustomNameVisible(true);
        });

        new BukkitRunnable() {
            private int ticksLived = 0;
            private final double risePerTick = 0.05; // How much it rises each tick
            private final int durationTicks = 30;   // About 1.5 seconds

            @Override
            public void run() {
                if (ticksLived >= durationTicks || !armorStand.isValid() || armorStand.isDead()) {
                    if (armorStand.isValid()) armorStand.remove();
                    this.cancel();
                    return;
                }
                armorStand.teleport(armorStand.getLocation().add(0, risePerTick, 0));
                ticksLived++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // --- Health Indicators ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Player || event.getEntity() instanceof ArmorStand) {
            return;
        }
        // Delay slightly to allow other plugins (including MMOCraft's EntityStatsManager) to modify the entity
        final LivingEntity spawnedEntity = event.getEntity();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (spawnedEntity.isValid() && !spawnedEntity.isDead()) { // Check validity before adding
                addHealthBar(spawnedEntity);
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity && !(event.getEntity() instanceof Player) && !(event.getEntity() instanceof ArmorStand)) {
            LivingEntity victim = (LivingEntity) event.getEntity();
            Bukkit.getScheduler().runTaskLater(plugin, () -> { // Run next tick to get updated health
                if (victim.isValid() && !victim.isDead()) {
                    updateHealthBar(victim);
                } else {
                    removeHealthBar(victim.getUniqueId());
                }
            }, 1L);
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof LivingEntity && !(event.getEntity() instanceof Player)) {
            removeHealthBar(event.getEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (healthBars.containsKey(entity.getUniqueId())) {
                ArmorStand healthBar = healthBars.remove(entity.getUniqueId());
                if (healthBar != null && healthBar.isValid()) {
                    healthBar.remove();
                }
            }
        }
    }

    public void addHealthBar(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.isDead() || entity instanceof Player || entity instanceof ArmorStand || healthBars.containsKey(entity.getUniqueId())) {
            return;
        }

        entityStatsManager.registerEntity(entity);

        Location barLocation = entity.getLocation().add(0, entity.getHeight() + healthBarYOffset, 0);

        ArmorStand healthBar = entity.getWorld().spawn(barLocation, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setSmall(true);
            as.setMarker(true);
            as.setInvulnerable(true);
            as.setCustomNameVisible(true);
            as.setMetadata(HEALTH_BAR_METADATA_KEY, new FixedMetadataValue(plugin, true));
        });

        healthBars.put(entity.getUniqueId(), healthBar);
        updateHealthBarInternal(entity, healthBar, true); // Initial update
    }

    public void updateHealthBar(LivingEntity entity) {
        ArmorStand healthBar = healthBars.get(entity.getUniqueId());
        if (healthBar != null && healthBar.isValid() && entity.isValid() && !entity.isDead()) {
            updateHealthBarInternal(entity, healthBar, false);
        } else if (healthBar != null && (!entity.isValid() || entity.isDead())) {
            removeHealthBar(entity.getUniqueId());
        }
    }

    private void updateHealthBarInternal(LivingEntity entity, ArmorStand healthBar, boolean isInitial) {
        if (entity == null || !entity.isValid() || healthBar == null || !healthBar.isValid()) {
            if (entity != null) removeHealthBar(entity.getUniqueId());
            else if (healthBar != null && healthBar.isValid()) healthBar.remove();
            return;
        }

        // Ensure entity stats are loaded, especially for max health
        EntityStats customStats = entityStatsManager.getStats(entity); // This will initialize if not present

        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double currentHealth = entity.getHealth();
        double maxHealth = (maxHealthAttr != null) ? maxHealthAttr.getValue() : 20.0; // Default to 20 if attribute is null

        // Use customStats' maxHealth if it's different from the attribute's base value
        // This assumes EntityStatsManager correctly sets the base attribute.
        if (customStats != null && customStats.getMaxHealth() > 0) {
            maxHealth = customStats.getMaxHealth();
        }

        currentHealth = Math.min(currentHealth, maxHealth); // Clamp current health
        currentHealth = Math.max(0, currentHealth);      // Ensure health isn't negative

        ChatColor healthColor;
        double percentage = (maxHealth > 0) ? (currentHealth / maxHealth) : 0;
        if (percentage > 0.75) healthColor = ChatColor.GREEN;
        else if (percentage > 0.40) healthColor = ChatColor.YELLOW;
        else if (percentage > 0.15) healthColor = ChatColor.RED;
        else healthColor = ChatColor.DARK_RED;

        String entityNameDisplay = ""; // Default to no name prefix
        if (entity.getCustomName() != null && !entity.getCustomName().isEmpty()) {
            entityNameDisplay = entity.getCustomName() + "\n";
        } else {
            // Optional: Use default entity type name if no custom name
            // entityNameDisplay = ChatColor.GRAY + entity.getType().name().toLowerCase().replace("_", " ") + "\n";
        }

        String healthText = healthColor + "❤ " + healthFormat.format(currentHealth) + ChatColor.GRAY + "/" + ChatColor.GREEN + healthFormat.format(maxHealth) + healthColor + " ❤";

        healthBar.setCustomName(entityNameDisplay + healthText);

        Location newBarLocation = entity.getLocation().add(0, entity.getHeight() + healthBarYOffset, 0);
        if (isInitial || !healthBar.getLocation().toVector().equals(newBarLocation.toVector())) {
            healthBar.teleport(newBarLocation);
        }
    }


    public void removeHealthBar(UUID entityId) {
        ArmorStand healthBar = healthBars.remove(entityId);
        if (healthBar != null && healthBar.isValid()) {
            healthBar.remove();
        }
    }

    public void removeAllHealthBars() {
        for (ArmorStand healthBar : healthBars.values()) {
            if (healthBar != null && healthBar.isValid()) {
                healthBar.remove();
            }
        }
        healthBars.clear();
    }

    private void startHealthBarUpdaterTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, ArmorStand> entry : new ConcurrentHashMap<>(healthBars).entrySet()) { // Iterate over a copy for safe removal
                    UUID entityId = entry.getKey();
                    ArmorStand healthBar = entry.getValue();
                    Entity entity = Bukkit.getEntity(entityId);

                    if (entity instanceof LivingEntity && entity.isValid() && !entity.isDead() && healthBar != null && healthBar.isValid()) {
                        updateHealthBarInternal((LivingEntity) entity, healthBar, false);
                    } else {
                        if (healthBar != null && healthBar.isValid()) {
                            healthBar.remove();
                        }
                        healthBars.remove(entityId);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    public void cleanupOnDisable() {
        removeAllHealthBars();
        // BukkitRunnables associated with this class instance are typically cancelled by the plugin disabling.
    }
}