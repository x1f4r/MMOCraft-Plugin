package io.github.x1f4r.mmocraft.player.listeners;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.player.PlayerStats;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import io.github.x1f4r.mmocraft.stats.EntityStats;
import io.github.x1f4r.mmocraft.stats.EntityStatsManager;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow; // Added for Tree Bow
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer; // Added for Tree Bow
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;

public class PlayerDamageListener implements Listener {

    private final PlayerStatsManager playerStatsManager;
    private final EntityStatsManager entityStatsManager;
    private final Random random = new Random();
    private final MMOCraft plugin; // Added to access logger if needed

    public PlayerDamageListener(MMOCraft plugin) {
        this.plugin = plugin; // Store plugin instance
        if (plugin.getPlayerStatsManager() == null) {
            throw new IllegalStateException("PlayerStatsManager has not been initialized in MMOCraft plugin main class!");
        }
        if (plugin.getEntityStatsManager() == null) {
            throw new IllegalStateException("EntityStatsManager has not been initialized in MMOCraft plugin main class!");
        }
        this.playerStatsManager = plugin.getPlayerStatsManager();
        this.entityStatsManager = plugin.getEntityStatsManager();
    }

    @EventHandler(priority = EventPriority.HIGH) // Keep HIGH to modify damage before final calculations
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        double calculatedDamage = event.getDamage();
        LivingEntity victim = (LivingEntity) event.getEntity();
        LivingEntity attacker = null;
        Projectile projectileSource = null;
        boolean isTrueDamage = false; // Initialize here


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
                    // plugin.getLogger().info("Tree Bow arrow hit! Base Damage: " + event.getDamage() + ", Multiplied Damage: " + calculatedDamage);
                }
            }
        }
        // --- End Tree Bow Damage Multiplier ---


        if (attacker != null) {
            int attackerStrength = 0;
            int attackerCritChance = 0;
            int attackerCritDamage = 0; // This is BONUS crit damage percent

            if (attacker instanceof Player) {
                PlayerStats pStats = playerStatsManager.getStats((Player) attacker);
                attackerStrength = pStats.getStrength();
                attackerCritChance = pStats.getCritChance();
                attackerCritDamage = pStats.getCritDamage();
            } else { // Mob attacker
                EntityStats eStats = entityStatsManager.getStats(attacker);
                if (eStats != null) {
                    attackerStrength = eStats.getStrength();
                    attackerCritChance = eStats.getCritChance();
                    attackerCritDamage = eStats.getCritDamage();
                }
            }

            // Add Strength from attacker (custom stat)
            calculatedDamage += attackerStrength;

            // Calculate Critical Hit
            if (attackerCritChance > 0 && random.nextDouble() * 100.0 < attackerCritChance) {
                // Base crit damage is 50% for players (vanilla-like), plus custom crit damage.
                // For mobs, their EntityStats.critDamage is the total bonus.
                // Let's use a more direct multiplier: 1.0 (base) + (total crit damage bonus / 100.0)
                double critMultiplierValue = 1.0 + (attackerCritDamage / 100.0);
                // If vanilla base crit (like +50%) should always apply and custom is additive to that:
                // double critMultiplierValue = 1.5 + (attackerCritDamage / 100.0); // If attackerCritDamage is PURELY custom bonus

                calculatedDamage *= critMultiplierValue;

                if (attacker instanceof Player) {
                    ((Player) attacker).playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
                }
                if (victim.isValid()) { // Play particle effect on victim
                    victim.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC, victim.getLocation().add(0, victim.getHeight() / 2, 0), 15, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }

        // --- True Damage Check (after all damage increases, before defense) ---
        // isTrueDamage = false; // Already initialized
        if (attacker != null) {
            // Check attacker entity itself (e.g. mob ability)
            if (NBTKeys.TRUE_DAMAGE_FLAG_KEY != null && attacker.getPersistentDataContainer().has(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE)) {
                if (attacker.getPersistentDataContainer().get(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE) == 1) {
                    isTrueDamage = true;
                }
            }
            // Check weapon in hand
            if (!isTrueDamage && attacker.getEquipment() != null) {
                ItemStack weapon = attacker.getEquipment().getItemInMainHand();
                if (weapon != null && weapon.hasItemMeta()) {
                    ItemMeta meta = weapon.getItemMeta();
                    if (NBTKeys.TRUE_DAMAGE_FLAG_KEY != null && meta.getPersistentDataContainer().has(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE)) {
                        if (meta.getPersistentDataContainer().get(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE) == 1) {
                            isTrueDamage = true;
                        }
                    }
                }
            }
        }
        // Check projectile itself
        if (!isTrueDamage && projectileSource != null) {
            if (NBTKeys.TRUE_DAMAGE_FLAG_KEY != null && projectileSource.getPersistentDataContainer().has(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE)) {
                if (projectileSource.getPersistentDataContainer().get(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE) == 1) {
                    isTrueDamage = true;
                }
            }
        }
        // --- End True Damage Check ---

        // Apply Victim's Custom Defense (if not true damage)
        if (!isTrueDamage) {
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

            if (victimCustomDefense > 0) {
                // Formula: Damage = Damage * (100 / (100 + Defense))
                calculatedDamage = calculatedDamage * (100.0 / (100.0 + victimCustomDefense));
            }
        }

        event.setDamage(Math.max(0.0, calculatedDamage)); // Ensure damage is not negative
    }
}