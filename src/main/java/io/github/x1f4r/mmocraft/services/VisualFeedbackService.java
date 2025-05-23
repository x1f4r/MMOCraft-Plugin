package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import io.github.x1f4r.mmocraft.visuals.HealthBarDisplayTask;
import io.github.x1f4r.mmocraft.visuals.listeners.VisualCleanupListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;


public class VisualFeedbackService implements Service {
    private MMOCore core;
    private LoggingService logging;
    private ConfigService configService;
    // NBTService is accessed via static keys
    private MMOCraft plugin;

    // --- Configurable Settings ---
    private boolean showDamageIndicators;
    private int damageIndicatorDurationTicks;
    private double damageIndicatorRisePerTick;
    private TextColor damageIndicatorColorNormal;
    private TextColor damageIndicatorColorCrit;
    private TextColor damageIndicatorColorTrueDamage;

    private boolean showHealthBars;
    private double healthBarYOffset;
    private long healthBarUpdateIntervalTicks;

    private final DecimalFormat damageFormat = new DecimalFormat("#,##0"); // No decimals for damage numbers usually
    private final DecimalFormat healthFormat = new DecimalFormat("#0"); // No decimals for health bars

    // --- Internal State ---
    // Entity UUID -> Health Bar ArmorStand
    protected final Map<UUID, ArmorStand> entityHealthBars = new ConcurrentHashMap<>();
    private BukkitTask healthBarUpdateTaskInstance; // Renamed from healthBarUpdateTask to avoid conflict
    private final Random random = new Random();


    public VisualFeedbackService(MMOCore core) {
        this.core = core;
    }

    @Override
    public void initialize(MMOCore core) {
        this.plugin = core.getPlugin();
        this.logging = core.getService(LoggingService.class);
        this.configService = core.getService(ConfigService.class);
        // NBTService static keys are initialized by NBTService itself.

        loadConfigSettings();
        startHealthBarUpdateTask(); // Start the task if enabled

        // Register listener for cleanup (e.g., on chunk unload)
        core.registerListener(new VisualCleanupListener(this));
        // EntityLifecycleListener (from EntityStatsService) will also call updateHealthBar on spawn
        // CombatService will call updateHealthBar on damage.
        logging.info(getServiceName() + " initialized. Damage Indicators: " + showDamageIndicators + ", Health Bars: " + showHealthBars);
    }

    private void loadConfigSettings() {
        ConfigurationSection visualsSection = configService.getMainConfigSection("entity_visuals");
        if (visualsSection == null) {
            logging.warn("Missing 'entity_visuals' section in config.yml. Using default visual settings.");
            // Create a dummy section in memory to avoid NPEs if defaults are attempted to be read later
            visualsSection = configService.getMainConfig().createSection("entity_visuals");
        }

        showDamageIndicators = visualsSection.getBoolean("damage_indicators.enabled", true);
        damageIndicatorDurationTicks = visualsSection.getInt("damage_indicators.duration_ticks", 25); // Slightly shorter
        damageIndicatorRisePerTick = visualsSection.getDouble("damage_indicators.rise_per_tick", 0.06);
        try {
            damageIndicatorColorNormal = TextColor.fromHexString(visualsSection.getString("damage_indicators.color_normal", "#AAAAAA")); // Gray
            damageIndicatorColorCrit = TextColor.fromHexString(visualsSection.getString("damage_indicators.color_crit", "#FFAA00"));   // Orange-Yellow
            damageIndicatorColorTrueDamage = TextColor.fromHexString(visualsSection.getString("damage_indicators.color_true_damage", "#FFFFFF")); // White
        } catch (Exception e) {
            logging.warn("Invalid hex color format in damage indicator config. Using defaults. Error: " + e.getMessage());
            damageIndicatorColorNormal = NamedTextColor.GRAY;
            damageIndicatorColorCrit = NamedTextColor.YELLOW;
            damageIndicatorColorTrueDamage = NamedTextColor.WHITE;
        }


        showHealthBars = visualsSection.getBoolean("health_bars.enabled", true);
        healthBarYOffset = visualsSection.getDouble("health_bars.y_offset", 0.5); // Slightly lower default
        healthBarUpdateIntervalTicks = visualsSection.getLong("health_bars.update_interval_ticks", 10L); // Update less frequently by default
        if(healthBarUpdateIntervalTicks <= 0) healthBarUpdateIntervalTicks = 10L;

        // Subscribe to config reloads for dynamic updates (if not already handled globally for this service)
        // For now, handled within initialize/reload logic for MMOCore.
        // If you had a reload command just for VisualFeedbackService, you'd call loadConfigSettings then.
    }

    private void startHealthBarUpdateTask() {
        if (healthBarUpdateTaskInstance != null && !healthBarUpdateTaskInstance.isCancelled()) {
            healthBarUpdateTaskInstance.cancel();
        }
        if (showHealthBars && healthBarUpdateIntervalTicks > 0) {
            healthBarUpdateTaskInstance = new HealthBarDisplayTask(this, plugin)
                    .runTaskTimer(plugin, healthBarUpdateIntervalTicks, healthBarUpdateIntervalTicks); // Initial delay same as interval
            logging.debug("Health bar periodic update task started (Interval: " + healthBarUpdateIntervalTicks + " ticks).");
        } else {
            logging.debug("Health bar periodic update task not started (disabled or invalid interval).");
        }
    }


    @Override
    public void shutdown() {
        if (healthBarUpdateTaskInstance != null && !healthBarUpdateTaskInstance.isCancelled()) {
            healthBarUpdateTaskInstance.cancel();
        }
        cleanupAllDamageIndicators();
        cleanupAllHealthBars();
        logging.info(getServiceName() + " shutdown complete. Visual elements cleaned up.");
    }

    public void showDamageIndicator(LivingEntity victim, double damage, boolean isCrit, boolean isTrueDamage) {
        if (!showDamageIndicators || victim instanceof ArmorStand || damage <= 0.009) return;

        Location spawnLoc = victim.getLocation().add(
                (random.nextDouble() * 0.7) - 0.35, // X offset a bit tighter
                victim.getHeight() * (0.5 + (random.nextDouble() * 0.4)), // Randomize Y slightly more
                (random.nextDouble() * 0.7) - 0.35  // Z offset
        );

        // Try to ensure the spawn location is not inside a solid block if possible
        if (!spawnLoc.getBlock().isPassable() && spawnLoc.clone().add(0,0.3,0).getBlock().isPassable()){
            spawnLoc.add(0,0.3,0); // Try slightly higher
        } else if (!spawnLoc.getBlock().isPassable()) {
            spawnLoc = victim.getEyeLocation().clone().add(random.nextGaussian() * 0.2, 0.1 + random.nextDouble() * 0.2, random.nextGaussian() * 0.2);
        }


        // Final check to ensure it doesn't spawn too far or clip into ground heavily
        if (spawnLoc.distanceSquared(victim.getEyeLocation()) > 9) { // Max ~3 blocks away
            spawnLoc = victim.getEyeLocation().add(0, 0.2, 0);
        }


        ArmorStand indicatorStand = victim.getWorld().spawn(spawnLoc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setSmall(true);
            as.setMarker(true); // Essential for performance and no collision/interaction
            as.setInvulnerable(true);
            as.setPersistent(false); // Don't save these to world files
            NBTService.set(as.getPersistentDataContainer(), NBTService.DAMAGE_INDICATOR_TAG_KEY, PersistentDataType.BYTE, (byte)1);

            TextColor color = isTrueDamage ? damageIndicatorColorTrueDamage : (isCrit ? damageIndicatorColorCrit : damageIndicatorColorNormal);
            String formattedDamage = damageFormat.format(damage); // No decimals

            Component damageText = Component.text(formattedDamage, color);
            if (isCrit && !isTrueDamage) { // Don't double-embellish true damage crits, true damage is primary info
                damageText = Component.text("✧ ", NamedTextColor.GOLD, TextDecoration.BOLD).append(damageText);
            } else if (isTrueDamage) {
                damageText = Component.text("◈ ", NamedTextColor.WHITE).append(damageText); // Different symbol for true damage
            }

            as.customName(damageText.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            as.setCustomNameVisible(true);
        });

        new BukkitRunnable() {
            private int ticksLived = 0;
            private final Vector riseVector = new Vector(0, damageIndicatorRisePerTick, 0);
            @Override
            public void run() {
                if (ticksLived >= damageIndicatorDurationTicks || !indicatorStand.isValid() || indicatorStand.isDead()) {
                    if (indicatorStand.isValid()) indicatorStand.remove();
                    this.cancel();
                    return;
                }
                try {
                    // For smoother appearance, especially with varying server TPS, teleport might be better than setVelocity
                    indicatorStand.teleport(indicatorStand.getLocation().add(riseVector));
                } catch (Exception e) {
                    if (indicatorStand.isValid()) indicatorStand.remove();
                    this.cancel();
                }
                ticksLived++;
            }
        }.runTaskTimer(plugin, 0L, 1L); // Run every tick for smooth rise
    }

    public void showCritEffects(LivingEntity attacker, LivingEntity victim) {
        if (attacker instanceof Player pAttacker) {
            pAttacker.playSound(pAttacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.3f);
        }
        if (victim.isValid()) {
            victim.getWorld().spawnParticle(Particle.CRIT_MAGIC, victim.getEyeLocation(), 15, 0.4, 0.4, 0.4, 0.1);
            victim.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, victim.getLocation().add(0, victim.getHeight() / 1.5, 0), 20, 0.5, 0.5, 0.5, 0.05);
        }
    }

    public void updateHealthBar(LivingEntity entity) {
        if (!showHealthBars || entity instanceof Player || entity instanceof ArmorStand || !entity.isValid() || entity.isDead()) {
            removeHealthBar(entity.getUniqueId()); // Cleanup if conditions not met
            return;
        }

        ArmorStand healthBarAS = entityHealthBars.computeIfAbsent(entity.getUniqueId(), uuid -> {
            if (logging.isDebugMode()) logging.debug("Creating new health bar for " + entity.getType() + " (" + uuid + ")");
            Location barInitialLocation = entity.getLocation().add(0, entity.getHeight() + healthBarYOffset, 0);
            return entity.getWorld().spawn(barInitialLocation, ArmorStand.class, as -> {
                as.setVisible(false);
                as.setGravity(false);
                as.setSmall(true);
                as.setMarker(true);
                as.setInvulnerable(true);
                as.setPersistent(false);
                NBTService.set(as.getPersistentDataContainer(), NBTService.HEALTH_BAR_TAG_KEY, PersistentDataType.BYTE, (byte)1);
                as.setCustomNameVisible(true); // Name will be set by update logic
            });
        });

        if (!healthBarAS.isValid() || healthBarAS.isDead()) {
            entityHealthBars.remove(entity.getUniqueId()); // Remove stale entry
            if(entity.isValid() && !entity.isDead()) updateHealthBar(entity); // Retry creation if entity is still valid
            return;
        }

        // Teleport armor stand to follow the entity
        Location newBarLocation = entity.getLocation().add(0, entity.getHeight() + healthBarYOffset, 0);
        if (healthBarAS.getLocation().distanceSquared(newBarLocation) > 0.01) { // Only teleport if moved significantly
            try {
                healthBarAS.teleport(newBarLocation);
            } catch (Exception e) {
                logging.warn("Error teleporting health bar for " + entity.getUniqueId() + ": " + e.getMessage() + ". Removing bar.");
                removeHealthBar(entity.getUniqueId());
                return;
            }
        }

        // Update display name (health text)
        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double currentHealth = entity.getHealth();
        double maxHealth = (maxHealthAttr != null) ? maxHealthAttr.getValue() : 20.0;
        currentHealth = Math.max(0, Math.min(currentHealth, maxHealth)); // Clamp health

        NamedTextColor healthTextColor;
        double healthPercentage = (maxHealth > 0.001) ? (currentHealth / maxHealth) : 0.0;
        if (healthPercentage > 0.66) healthTextColor = NamedTextColor.GREEN;
        else if (healthPercentage > 0.33) healthTextColor = NamedTextColor.YELLOW;
        else healthTextColor = NamedTextColor.RED;

        Component entityNameDisplay = entity.customName();
        if (entityNameDisplay == null) { // If entity has no custom name, use its type name
            String typeName = entity.getType().name().replace("_", " ");
            typeName = typeName.substring(0, 1).toUpperCase() + typeName.substring(1).toLowerCase();
            entityNameDisplay = Component.text(typeName, NamedTextColor.WHITE);
        } else {
            entityNameDisplay = entityNameDisplay.colorIfAbsent(NamedTextColor.WHITE); // Ensure it has a color
        }
        entityNameDisplay = entityNameDisplay.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);


        Component healthText = Component.text(healthFormat.format(currentHealth) + " / " + healthFormat.format(maxHealth) + " HP", healthTextColor);
        Component finalDisplayName = entityNameDisplay.append(Component.text(" - ", NamedTextColor.DARK_GRAY)).append(healthText);

        // Only update custom name if it has changed to reduce packet overhead
        if (!Objects.equals(healthBarAS.customName(), finalDisplayName)) {
            healthBarAS.customName(finalDisplayName);
        }
    }

    public void removeHealthBar(UUID entityId) {
        ArmorStand healthBarAS = entityHealthBars.remove(entityId);
        if (healthBarAS != null && healthBarAS.isValid()) {
            healthBarAS.remove();
            if (logging.isDebugMode()) logging.debug("Removed health bar for entity: " + entityId);
        }
    }

    public void cleanupAllHealthBars() {
        logging.info("Cleaning up all (" + entityHealthBars.size() + ") active health bars...");
        new ArrayList<>(entityHealthBars.values()).forEach(as -> {
            if (as.isValid()) as.remove();
        });
        entityHealthBars.clear();
    }

    public void cleanupAllDamageIndicators() {
        logging.info("Cleaning up active damage indicators across all worlds...");
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand as : world.getEntitiesByClass(ArmorStand.class)) {
                if (NBTService.has(as.getPersistentDataContainer(), NBTService.DAMAGE_INDICATOR_TAG_KEY, PersistentDataType.BYTE)) {
                    if (as.isValid()) as.remove();
                    count++;
                }
            }
        }
        if (count > 0) logging.info("Removed " + count + " damage indicators.");
    }

    // Called by VisualCleanupListener on chunk unload or other cleanup events
    public void cleanupVisualsInChunk(Chunk chunk) {
        int healthBarsCleaned = 0;
        int damageIndicatorsCleaned = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof ArmorStand as) {
                if (NBTService.has(as.getPersistentDataContainer(), NBTService.HEALTH_BAR_TAG_KEY, PersistentDataType.BYTE)) {
                    if (as.isValid()) as.remove();
                    // Try to find which entity this health bar belonged to if we need to remove from entityHealthBars map by entity UUID
                    // This is hard without storing entity UUID on the AS. For now, periodic task handles map cleanup.
                    healthBarsCleaned++;
                } else if (NBTService.has(as.getPersistentDataContainer(), NBTService.DAMAGE_INDICATOR_TAG_KEY, PersistentDataType.BYTE)) {
                    if (as.isValid()) as.remove();
                    damageIndicatorsCleaned++;
                }
            } else if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                // If a living entity itself is unloaded, ensure its bar is removed from tracking
                if (entityHealthBars.containsKey(entity.getUniqueId())) {
                    removeHealthBar(entity.getUniqueId()); // This will also remove the AS
                    healthBarsCleaned++; // Count it if removed this way
                }
            }
        }
        if (logging.isDebugMode() && (healthBarsCleaned > 0 || damageIndicatorsCleaned > 0)) {
            logging.debug("Cleaned " + healthBarsCleaned + " health bars and " + damageIndicatorsCleaned + " damage indicators in unloaded chunk: " + chunk.getX() + "," + chunk.getZ());
        }
    }

    // Getter for HealthBarDisplayTask to iterate over managed bars
    public Map<UUID, ArmorStand> getManagedHealthBars() {
        return Collections.unmodifiableMap(entityHealthBars); // Return unmodifiable view
    }
}