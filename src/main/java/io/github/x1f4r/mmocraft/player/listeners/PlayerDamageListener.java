package io.github.x1f4r.mmocraft.player.listeners; // Consider renaming package to io.github.x1f4r.mmocraft.listeners

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.player.PlayerStats;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import io.github.x1f4r.mmocraft.stats.EntityStats; // For general entity stats
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

public class PlayerDamageListener implements Listener { // Rename to GenericDamageListener eventually

    private final PlayerStatsManager playerStatsManager;
    private final EntityStatsManager entityStatsManager;
    private final Random random = new Random();
    // private final MMOCraft plugin; // For NBTKeys if they weren't static or for config

    public PlayerDamageListener(MMOCraft plugin) {
        // this.plugin = plugin;
        this.playerStatsManager = plugin.getPlayerStatsManager();
        this.entityStatsManager = plugin.getEntityStatsManager();
    }

    @EventHandler(priority = EventPriority.HIGH) // Adjust priority as needed
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        double calculatedDamage = event.getDamage(); // Start with damage after vanilla calculations (armor, enchantments)
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

        // --- I. Attacker's Offensive Calculation ---
        if (attacker != null) {
            int attackerStrength = 0;
            int attackerCritChance = 0;
            int attackerCritDamage = 0; // This is the bonus damage %, e.g. 50 means +50%

            if (attacker instanceof Player) {
                PlayerStats pStats = playerStatsManager.getStats((Player) attacker);
                attackerStrength = pStats.getStrength();
                attackerCritChance = pStats.getCritChance();
                attackerCritDamage = pStats.getCritDamage();
            } else { // Mob attacker
                EntityStats eStats = entityStatsManager.getStats(attacker); // getStats should handle initialization
                if (eStats != null) {
                    attackerStrength = eStats.getStrength();
                    attackerCritChance = eStats.getCritChance();
                    attackerCritDamage = eStats.getCritDamage();
                }
            }

            // Apply Strength (additive to current damage)
            calculatedDamage += attackerStrength;

            // Apply Critical Hit (multiplicative to current damage)
            if (attackerCritChance > 0 && random.nextDouble() * 100.0 < attackerCritChance) {
                double critMultiplier = 1.0 + (attackerCritDamage / 100.0); // e.g., 50 critDamage -> 1.5x
                calculatedDamage *= critMultiplier;

                // Feedback for crits
                if (attacker instanceof Player) {
                    ((Player) attacker).playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
                }
                if (victim.isValid()) { // Particle on victim
                    victim.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC, victim.getLocation().add(0, victim.getHeight() / 2, 0), 15, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }

        // --- II. Victim's Defensive Calculation ---
        int victimCustomDefense = 0;
        if (victim instanceof Player) {
            PlayerStats pStats = playerStatsManager.getStats((Player) victim);
            victimCustomDefense = pStats.getDefense();
        } else { // Mob victim
            EntityStats eStats = entityStatsManager.getStats(victim);
            if (eStats != null) {
                victimCustomDefense = eStats.getDefense();
            }
        }

        // True Damage Check (bypasses custom defense)
        boolean isTrueDamage = false;
        if (attacker != null) { // Check direct attacker or their weapon
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
        if (!isTrueDamage && projectileSource != null) { // Check projectile if it was the source
            if (projectileSource.getPersistentDataContainer().has(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE)) {
                isTrueDamage = true;
            }
        }

        if (!isTrueDamage && victimCustomDefense > 0) {
            // Apply custom defense formula: DamageTaken = CurrentDamage * (100 / (100 + Defense))
            calculatedDamage = calculatedDamage * (100.0 / (100.0 + victimCustomDefense));
        }

        // --- III. Finalize Damage ---
        event.setDamage(Math.max(0.0, calculatedDamage)); // Ensure damage is not negative
    }
}