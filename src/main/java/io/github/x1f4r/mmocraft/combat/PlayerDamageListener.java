package io.github.x1f4r.mmocraft.combat; // New package

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import io.github.x1f4r.mmocraft.display.DamageAndHealthDisplayManager;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import io.github.x1f4r.mmocraft.stats.EntityStats;
import io.github.x1f4r.mmocraft.stats.EntityStatsManager;
import io.github.x1f4r.mmocraft.stats.PlayerStats; // Correct import
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand; // <<< ADDED IMPORT
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent; // Import base event
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;
import java.util.logging.Logger;

public class PlayerDamageListener implements Listener {

    private final Logger log;
    private final PlayerStatsManager playerStatsManager;
    private final EntityStatsManager entityStatsManager;
    private final DamageAndHealthDisplayManager displayManager;
    private final Random random = new Random();

    public PlayerDamageListener(MMOCore core) {
        this.log = MMOPlugin.getMMOLogger();
        this.playerStatsManager = core.getPlayerStatsManager();
        this.entityStatsManager = core.getEntityStatsManager();
        this.displayManager = core.getDamageAndHealthDisplayManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCustomDamageCalculation(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return; // Victim must be a living entity
        }

        LivingEntity victim = (LivingEntity) event.getEntity();
        LivingEntity attacker = null;
        Projectile projectileSource = null;
        ItemStack weapon = null;

        // Determine the attacker and projectile (if any)
        if (event.getDamager() instanceof LivingEntity) {
            attacker = (LivingEntity) event.getDamager();
            if (attacker.getEquipment() != null) {
                weapon = attacker.getEquipment().getItemInMainHand();
            }
        } else if (event.getDamager() instanceof Projectile) {
            projectileSource = (Projectile) event.getDamager();
            if (projectileSource.getShooter() instanceof LivingEntity) {
                attacker = (LivingEntity) projectileSource.getShooter();
                // We might need the bow used, but that's harder to get here reliably.
                // We'll rely on NBT tags applied to the projectile itself.
            }
        }

        // If no identifiable living attacker, maybe apply default Bukkit damage or exit
        if (attacker == null) {
            log.finest("Damage to " + victim.getName() + " from non-living entity " + event.getDamager().getType() + ". Skipping custom calculation.");
            // Optionally, still apply victim defense calculations here if desired
            // applyVictimDefense(event, victim, event.getDamage(), false);
            return;
        }

        // --- Start Damage Calculation ---
        double baseDamage = event.getDamage(); // Start with Bukkit's calculated damage
        double calculatedDamage = baseDamage;
        boolean isCrit = false;
        boolean isTrueDamage = false; // Assume not true damage initially

        // --- Attacker Offensive Stats ---
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
            attackerStrength = eStats.getStrength();
            attackerCritChance = eStats.getCritChance();
            attackerCritDamage = eStats.getCritDamage();
        }

        // --- Projectile Specific Modifiers ---
        if (projectileSource != null) {
            PersistentDataContainer projPdc = projectileSource.getPersistentDataContainer();
            // Example: Tree Bow Power Multiplier
            if (NBTKeys.PROJECTILE_SOURCE_ITEM_ID_KEY != null && NBTKeys.PROJECTILE_DAMAGE_MULTIPLIER_KEY != null) {
                String sourceItemId = projPdc.get(NBTKeys.PROJECTILE_SOURCE_ITEM_ID_KEY, PersistentDataType.STRING);
                if ("tree_bow".equals(sourceItemId)) { // Check if it came from a tree_bow
                    int multiplier = projPdc.getOrDefault(NBTKeys.PROJECTILE_DAMAGE_MULTIPLIER_KEY, PersistentDataType.INTEGER, 1);
                    calculatedDamage *= Math.max(1, multiplier); // Apply multiplier from NBT
                    log.finer("Applied Tree Bow multiplier (" + multiplier + "x) to projectile damage.");
                }
            }
            // Check for True Damage flag on projectile
            if (NBTKeys.TRUE_DAMAGE_FLAG_KEY != null && projPdc.getOrDefault(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE, (byte)0) == 1) {
                isTrueDamage = true;
            }
        }

        // --- Apply Strength ---
        // Strength adds flat damage AFTER multipliers like projectile power, but BEFORE crit/defense
        calculatedDamage += attackerStrength;
        log.finer("Damage after strength (" + attackerStrength + "): " + calculatedDamage);


        // --- Critical Hit Calculation ---
        if (attackerCritChance > 0 && random.nextDouble() * 100.0 < attackerCritChance) {
            isCrit = true;
            double critMultiplier = 1.0 + (Math.max(0, attackerCritDamage) / 100.0); // Ensure crit damage isn't negative multiplier
            calculatedDamage *= critMultiplier;
            log.finer("Critical Hit! Multiplier: " + critMultiplier + ", Damage: " + calculatedDamage);

            // Crit Effects
            if (attacker instanceof Player) {
                ((Player) attacker).playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
            }
            if (victim.isValid()) {
                victim.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC, victim.getLocation().add(0, victim.getHeight() / 2, 0), 15, 0.3, 0.3, 0.3, 0.05);
            }
        }

        // --- True Damage Check (Weapon/Attacker) ---
        // Check weapon first, then attacker entity tag as fallback/override
        if (!isTrueDamage && weapon != null && weapon.hasItemMeta() && NBTKeys.TRUE_DAMAGE_FLAG_KEY != null) {
            ItemMeta meta = weapon.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().getOrDefault(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE, (byte)0) == 1) {
                isTrueDamage = true;
            }
        }
        if (!isTrueDamage && NBTKeys.TRUE_DAMAGE_FLAG_KEY != null && attacker.getPersistentDataContainer().getOrDefault(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE, (byte)0) == 1) {
            isTrueDamage = true; // Attacker itself deals true damage (e.g., special mob ability)
        }


        // --- Victim Defensive Stats ---
        if (!isTrueDamage) {
            applyVictimDefense(event, victim, calculatedDamage, isCrit); // Let helper handle final damage set and display
        } else {
            // Apply true damage directly, bypassing defense
            log.finer("Applying True Damage: " + calculatedDamage);
            event.setDamage(Math.max(0.0, calculatedDamage)); // Prevent negative damage
            if (displayManager != null && !(victim instanceof ArmorStand)) { // <<< USES IMPORT
                displayManager.createFloatingDamageIndicator(victim, event.getDamage(), isCrit, true);
            }
        }
    }

    // Helper method to apply defense and set final damage
    private void applyVictimDefense(EntityDamageEvent event, LivingEntity victim, double incomingDamage, boolean isCrit) {
        int victimDefense = 0;
        if (victim instanceof Player) {
            PlayerStats pStats = playerStatsManager.getStats((Player) victim);
            victimDefense = pStats.getDefense();
        } else {
            EntityStats eStats = entityStatsManager.getStats(victim);
            victimDefense = eStats.getDefense();
        }

        double finalDamage;
        if (victimDefense > 0) {
            // Formula: Damage * (1 - Defense / (Defense + 100)) or Damage * (100 / (100 + Defense))
            finalDamage = incomingDamage * (100.0 / (100.0 + victimDefense));
            log.finer("Applying Defense (" + victimDefense + "). Damage reduced to: " + finalDamage);
        } else {
            finalDamage = incomingDamage;
        }

        // Prevent negative damage
        finalDamage = Math.max(0.0, finalDamage);

        // Set the final damage on the event
        event.setDamage(finalDamage);

        // Display damage indicator (only if damage > 0 and not an armor stand)
        if (displayManager != null && finalDamage > 0 && !(victim instanceof ArmorStand)) { // <<< USES IMPORT
            displayManager.createFloatingDamageIndicator(victim, finalDamage, isCrit, false); // Not true damage here
        }
    }

    // Optional: Handle environmental damage types if you want defense to apply
     /*
     @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
     public void onEnvironmentalDamage(EntityDamageEvent event) {
         // Only handle if it's NOT EntityDamageByEntity (already handled)
         if (event instanceof EntityDamageByEntityEvent || !(event.getEntity() instanceof LivingEntity)) {
             return;
         }

         LivingEntity victim = (LivingEntity) event.getEntity();
         EntityDamageEvent.DamageCause cause = event.getCause();

         // List of causes where defense MIGHT apply (configurable?)
         Set<EntityDamageEvent.DamageCause> defendableCauses = EnumSet.of(
                 EntityDamageEvent.DamageCause.CONTACT,
                 EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                 EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
                 EntityDamageEvent.DamageCause.FALLING_BLOCK,
                 EntityDamageEvent.DamageCause.FIRE,
                 EntityDamageEvent.DamageCause.FIRE_TICK,
                 EntityDamageEvent.DamageCause.LAVA,
                 EntityDamageEvent.DamageCause.HOT_FLOOR,
                 EntityDamageEvent.DamageCause.LIGHTNING
                 // Add others like DROWNING, SUFFOCATION if desired? Probably not.
                 // POISON, WITHER? Maybe, depends on design.
         );

         if (defendableCauses.contains(cause)) {
             log.finer("Applying defense for environmental damage cause: " + cause);
             applyVictimDefense(event, victim, event.getDamage(), false); // Not a crit
         }
     }
     */

}
