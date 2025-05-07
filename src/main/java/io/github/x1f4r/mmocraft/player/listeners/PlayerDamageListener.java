package io.github.x1f4r.mmocraft.player.listeners;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.player.PlayerStats;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager; // Added import
import io.github.x1f4r.mmocraft.stats.EntityStats;
import io.github.x1f4r.mmocraft.stats.EntityStatsManager;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;

public class PlayerDamageListener implements Listener {

    private final PlayerStatsManager playerStatsManager; // This line should now work
    private final EntityStatsManager entityStatsManager;
    private final Random random = new Random();

    public PlayerDamageListener(MMOCraft plugin) {
        if (plugin.getPlayerStatsManager() == null) {
            throw new IllegalStateException("PlayerStatsManager has not been initialized in MMOCraft plugin main class!");
        }
        if (plugin.getEntityStatsManager() == null) {
            throw new IllegalStateException("EntityStatsManager has not been initialized in MMOCraft plugin main class!");
        }
        this.playerStatsManager = plugin.getPlayerStatsManager();
        this.entityStatsManager = plugin.getEntityStatsManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        double calculatedDamage = event.getDamage();
        LivingEntity victim = (LivingEntity) event.getEntity();
        LivingEntity attacker = null;
        Projectile projectileSource = null;


        if (event.getDamager() instanceof LivingEntity) {
            attacker = (LivingEntity) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            projectileSource = (Projectile) event.getDamager();
            if (projectileSource.getShooter() instanceof LivingEntity) {
                attacker = (LivingEntity) projectileSource.getShooter();
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
                EntityStats eStats = entityStatsManager.getStats(attacker);
                if (eStats != null) {
                    attackerStrength = eStats.getStrength();
                    attackerCritChance = eStats.getCritChance();
                    attackerCritDamage = eStats.getCritDamage();
                }
            }

            calculatedDamage += attackerStrength;

            if (attackerCritChance > 0 && random.nextDouble() * 100.0 < attackerCritChance) {
                double critMultiplier = 1.0 + (attackerCritDamage / 100.0);
                calculatedDamage *= critMultiplier;

                if (attacker instanceof Player) {
                    ((Player) attacker).playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
                }
                if (victim.isValid()) {
                    victim.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC, victim.getLocation().add(0, victim.getHeight() / 2, 0), 15, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }

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

        boolean isTrueDamage = false;
        if (attacker != null) {
            if (attacker.getPersistentDataContainer().has(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE)) {
                isTrueDamage = true;
            }
            if (!isTrueDamage && attacker.getEquipment() != null) {
                ItemStack weapon = attacker.getEquipment().getItemInMainHand();
                if (weapon != null && weapon.hasItemMeta()) {
                    ItemMeta meta = weapon.getItemMeta();
                    if (meta.getPersistentDataContainer().has(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE)) {
                        isTrueDamage = true;
                    }
                }
            }
        }
        if (!isTrueDamage && projectileSource != null) {
            if (projectileSource.getPersistentDataContainer().has(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE)) {
                isTrueDamage = true;
            }
        }

        if (!isTrueDamage && victimCustomDefense > 0) {
            calculatedDamage = calculatedDamage * (100.0 / (100.0 + victimCustomDefense));
        }

        event.setDamage(Math.max(0.0, calculatedDamage));
    }
}