package io.github.x1f4r.mmocraft.player.listeners;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.display.DamageAndHealthDisplayManager;
import io.github.x1f4r.mmocraft.player.PlayerStats;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import io.github.x1f4r.mmocraft.stats.EntityStats;
import io.github.x1f4r.mmocraft.stats.EntityStatsManager;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;
import java.util.logging.Level; // For logging errors

public class PlayerDamageListener implements Listener {

    private final PlayerStatsManager playerStatsManager;
    private final EntityStatsManager entityStatsManager;
    private final DamageAndHealthDisplayManager displayManager;
    private final Random random = new Random();
    private final MMOCraft plugin;

    public PlayerDamageListener(MMOCraft plugin, DamageAndHealthDisplayManager displayManager) {
        this.plugin = plugin;
        if (plugin.getPlayerStatsManager() == null) {
            throw new IllegalStateException("PlayerStatsManager has not been initialized in MMOCraft plugin main class!");
        }
        if (plugin.getEntityStatsManager() == null) {
            throw new IllegalStateException("EntityStatsManager has not been initialized in MMOCraft plugin main class!");
        }
        this.playerStatsManager = plugin.getPlayerStatsManager();
        this.entityStatsManager = plugin.getEntityStatsManager();
        this.displayManager = displayManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) // ignoreCancelled for safety
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        double originalDamage = event.getDamage();
        double calculatedDamage = originalDamage; // Start with vanilla damage
        LivingEntity victim = (LivingEntity) event.getEntity();
        LivingEntity attacker = null;
        Projectile projectileSource = null;
        boolean isTrueDamage = false;
        boolean isCrit = false;

        if (event.getDamager() instanceof LivingEntity) {
            attacker = (LivingEntity) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            projectileSource = (Projectile) event.getDamager();
            if (projectileSource.getShooter() instanceof LivingEntity) {
                attacker = (LivingEntity) projectileSource.getShooter();
            }
        }

        // --- Tree Bow Damage Multiplier ---
        if (projectileSource instanceof Arrow) {
            Arrow arrow = (Arrow) projectileSource;
            PersistentDataContainer arrowPDC = arrow.getPersistentDataContainer();
            if (NBTKeys.PROJECTILE_SOURCE_BOW_TYPE_KEY != null &&
                    "tree_bow".equals(arrowPDC.get(NBTKeys.PROJECTILE_SOURCE_BOW_TYPE_KEY, PersistentDataType.STRING))) {
                if (NBTKeys.PROJECTILE_DAMAGE_MULTIPLIER_KEY != null &&
                        arrowPDC.has(NBTKeys.PROJECTILE_DAMAGE_MULTIPLIER_KEY, PersistentDataType.INTEGER)) {
                    int multiplier = arrowPDC.get(NBTKeys.PROJECTILE_DAMAGE_MULTIPLIER_KEY, PersistentDataType.INTEGER);
                    calculatedDamage *= multiplier;
                }
            }
        }

        if (attacker != null) {
            int attackerStrength = 0;
            int attackerCritChance = 0;
            int attackerCritDamage = 0;

            if (attacker instanceof Player) {
                PlayerStats pStats = playerStatsManager.getStats((Player) attacker);
                attackerStrength = pStats.getStrength();
                attackerCritChance = pStats.getCritChance();
                attackerCritDamage = pStats.getCritDamage();
            } else {
                EntityStats eStats = entityStatsManager.getStats(attacker); // Ensures stats are loaded/initialized
                if (eStats != null) {
                    attackerStrength = eStats.getStrength();
                    attackerCritChance = eStats.getCritChance();
                    attackerCritDamage = eStats.getCritDamage();
                }
            }

            calculatedDamage += attackerStrength;

            if (attackerCritChance > 0 && random.nextDouble() * 100.0 < attackerCritChance) {
                isCrit = true;
                double critMultiplierValue = 1.0 + (attackerCritDamage / 100.0);
                calculatedDamage *= critMultiplierValue;

                if (attacker instanceof Player) {
                    ((Player) attacker).playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
                }
                if (victim.isValid()) {
                    victim.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC, victim.getLocation().add(0, victim.getHeight() / 2, 0), 15, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }

        // True Damage Check
        if (attacker != null) {
            if (NBTKeys.TRUE_DAMAGE_FLAG_KEY != null && attacker.getPersistentDataContainer().has(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE) &&
                    attacker.getPersistentDataContainer().get(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE) == 1) {
                isTrueDamage = true;
            }
            if (!isTrueDamage && attacker.getEquipment() != null) {
                ItemStack weapon = attacker.getEquipment().getItemInMainHand();
                if (weapon != null && weapon.hasItemMeta() && NBTKeys.TRUE_DAMAGE_FLAG_KEY != null) {
                    ItemMeta meta = weapon.getItemMeta();
                    if (meta.getPersistentDataContainer().has(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE) &&
                            meta.getPersistentDataContainer().get(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE) == 1) {
                        isTrueDamage = true;
                    }
                }
            }
        }
        if (!isTrueDamage && projectileSource != null && NBTKeys.TRUE_DAMAGE_FLAG_KEY != null) {
            if (projectileSource.getPersistentDataContainer().has(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE) &&
                    projectileSource.getPersistentDataContainer().get(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE) == 1) {
                isTrueDamage = true;
            }
        }

        if (!isTrueDamage) {
            int victimCustomDefense = 0;
            if (victim instanceof Player) {
                PlayerStats pStats = playerStatsManager.getStats((Player) victim);
                victimCustomDefense = pStats.getDefense();
            } else {
                EntityStats eStats = entityStatsManager.getStats(victim);
                if (eStats != null) {
                    victimCustomDefense = eStats.getDefense();
                }
            }
            if (victimCustomDefense > 0) {
                calculatedDamage = calculatedDamage * (100.0 / (100.0 + victimCustomDefense));
            }
        }

        if (Double.isNaN(calculatedDamage) || Double.isInfinite(calculatedDamage)) {
            plugin.getLogger().log(Level.WARNING, "Damage calculation resulted in NaN or Infinity for victim: " + victim.getName() + " by attacker: " + (attacker != null ? attacker.getName() : "Unknown") + ". Defaulting damage to 1. Original event damage: " + originalDamage);
            calculatedDamage = 1.0;
        }

        final double finalDamageToApply = Math.max(0.0, calculatedDamage);
        event.setDamage(finalDamageToApply);

        if (!(victim instanceof org.bukkit.entity.ArmorStand) && finalDamageToApply > 0) {
            if (displayManager != null) {
                displayManager.createFloatingDamageIndicator(victim, finalDamageToApply, isCrit, isTrueDamage);
            }
        }
    }
}