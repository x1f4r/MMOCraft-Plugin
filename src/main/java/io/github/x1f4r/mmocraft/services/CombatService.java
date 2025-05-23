package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.combat.listeners.DamageCalculationListener;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import io.github.x1f4r.mmocraft.entities.EntityStats;
import io.github.x1f4r.mmocraft.player.stats.CalculatedPlayerStats;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

public class CombatService implements Service {
    private MMOCore core;
    private LoggingService logging;
    private PlayerStatsService playerStatsService;
    private EntityStatsService entityStatsService;
    private VisualFeedbackService visualFeedbackService; // Nullable until VisualFeedbackService is guaranteed initialized
    private NBTService nbtService; // Static keys accessed via NBTService.KEY
    // private ItemService itemService; // For checking item templates for set bonuses, etc. (Part 2+)

    private final Random random = new Random();
    // Define which environmental damage types might be reduced by defense
    private final Set<EntityDamageEvent.DamageCause> defendableEnvironmentalCauses = EnumSet.noneOf(EntityDamageEvent.DamageCause.class);
            // EntityDamageEvent.DamageCause.CONTACT, // Example: Cactus, Berry Bush
            // EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, // If defense should reduce TNT/Creeper AoE
            // EntityDamageEvent.DamageCause.FIRE,
            // EntityDamageEvent.DamageCause.FIRE_TICK,
            // EntityDamageEvent.DamageCause.LAVA,
            // EntityDamageEvent.DamageCause.FALL,
            // EntityDamageEvent.DamageCause.HOT_FLOOR // Magma Block
            // By default, empty, meaning defense only applies to EntityDamageByEntityEvent
    );


    public CombatService(MMOCore core) {
        this.core = core;
    }

    @Override
    public void initialize(MMOCore core) {
        this.logging = core.getService(LoggingService.class);
        this.playerStatsService = core.getService(PlayerStatsService.class);
        this.entityStatsService = core.getService(EntityStatsService.class);
        // VisualFeedbackService might be initialized after this one, so get it carefully.
        try {
            this.visualFeedbackService = core.getService(VisualFeedbackService.class);
        } catch (IllegalStateException e) {
            logging.warn("CombatService initialized before VisualFeedbackService. Damage indicators might be delayed or unavailable until VFS is ready.");
            this.visualFeedbackService = null;
        }
        this.nbtService = core.getService(NBTService.class); // Ensure NBTService static fields are initialized
        // this.itemService = core.getService(ItemService.class); // When available

        core.registerListener(new DamageCalculationListener(this));
        logging.info(getServiceName() + " initialized.");
    }

    @Override
    public void shutdown() {
        // No specific shutdown tasks for CombatService
    }

    public void processDamageByEntityEvent(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            // logging.debug("Combat Victim is not a LivingEntity ("+event.getEntity().getType()+"), skipping custom calculation.");
            return;
        }
        if (victim.isDead() || event.isCancelled()) return;

        LivingEntity attacker = null;
        Projectile projectileSource = null;
        ItemStack weapon = null;

        if (event.getDamager() instanceof LivingEntity directAttacker) {
            attacker = directAttacker;
            if (attacker.getEquipment() != null) {
                weapon = attacker.getEquipment().getItemInMainHand();
            }
        } else if (event.getDamager() instanceof Projectile proj) {
            projectileSource = proj;
            if (proj.getShooter() instanceof LivingEntity shooter) {
                attacker = shooter;
                // Weapon for projectile source is tricky; rely on NBT on projectile itself for specific weapon stats
            }
        }

        if (attacker == null) {
            // Environmental damage caused by an entity (e.g. TNT lit by player, then exploding on mob)
            // We'll let handleEnvironmentalDamage deal with this if cause is defendable.
            handleEnvironmentalDamage(victim, event.getDamage(), event.getCause(), event);
            return;
        }

        // --- Fetch Stats ---
        CalculatedPlayerStats attackerPlayerStats = (attacker instanceof Player p) ? playerStatsService.getCalculatedStats(p) : null;
        EntityStats attackerEntityStats = (attacker instanceof Player) ? null : entityStatsService.getEntityStats(attacker);

        CalculatedPlayerStats victimPlayerStats = (victim instanceof Player p) ? playerStatsService.getCalculatedStats(p) : null;
        EntityStats victimEntityStats = (victim instanceof Player) ? null : entityStatsService.getEntityStats(victim);

        // --- Start Damage Calculation ---
        double baseDamage = event.getDamage(); // Bukkit's initial damage (includes vanilla weapon damage, strength pots)
        double calculatedDamage = baseDamage;
        if (logging.isDebugMode()) logging.debug(String.format("Combat Init: Victim %s, Attacker %s, BaseDmg %.2f, Cause %s", victim.getName(), attacker.getName(), baseDamage, event.getCause()));


        boolean isCrit = false;
        boolean isTrueDamage = false;

        // --- NBT Flags on Weapon/Projectile ---
        PersistentDataContainer sourcePdc = null;
        if (projectileSource != null) {
            sourcePdc = projectileSource.getPersistentDataContainer();
        } else if (weapon != null && weapon.hasItemMeta()) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta != null) sourcePdc = weaponMeta.getPersistentDataContainer();
        }

        if (sourcePdc != null) {
            if (NBTService.get(sourcePdc, NBTService.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE, (byte)0) == (byte)1) {
                isTrueDamage = true;
                if (logging.isDebugMode()) logging.debug("Source (Weapon/Projectile) flagged as TRUE_DAMAGE.");
            }
            // Projectile specific damage multiplier (e.g., from a custom bow's power)
            if (projectileSource != null) { // Only apply if it's actually a projectile
                Integer projMultiplier = NBTService.get(sourcePdc, NBTService.PROJECTILE_DAMAGE_MULTIPLIER, PersistentDataType.INTEGER, null);
                if (projMultiplier != null && projMultiplier != 1) { // Assume 1 is no change
                    calculatedDamage *= projMultiplier;
                    if (logging.isDebugMode()) logging.debug(String.format("Projectile Multiplier %.2fx applied. Dmg: %.2f", (double)projMultiplier, calculatedDamage));
                }
            }
        }

        // --- Attacker's MMOCraft Stats Application ---
        int attackerStrength = (attackerPlayerStats != null) ? attackerPlayerStats.strength() : (attackerEntityStats != null ? attackerEntityStats.strength() : 0);
        int attackerCritChance = (attackerPlayerStats != null) ? attackerPlayerStats.critChance() : (attackerEntityStats != null ? attackerEntityStats.critChance() : 0);
        int attackerCritDamage = (attackerPlayerStats != null) ? attackerPlayerStats.critDamage() : (attackerEntityStats != null ? attackerEntityStats.critDamage() : 0);

        // Apply MMOCraft Strength. Vanilla Strength potion is already in baseDamage.
        // Our strength is an additional flat bonus applied after multipliers like projectile.
        calculatedDamage += attackerStrength;
        if (attackerStrength != 0 && logging.isDebugMode()) logging.debug(String.format("MMO Strength %+d applied. Dmg: %.2f", attackerStrength, calculatedDamage));


        // --- Critical Hit ---
        if (attackerCritChance > 0 && random.nextInt(100) < attackerCritChance) {
            isCrit = true;
            double critMultiplier = 1.0 + (Math.max(0, attackerCritDamage) / 100.0);
            calculatedDamage *= critMultiplier;
            if (logging.isDebugMode()) logging.debug(String.format("CRITICAL HIT! CritDmg: %d%%, Multiplier: %.2fx. Dmg: %.2f", attackerCritDamage, critMultiplier, calculatedDamage));
        }

        // --- Victim's Defense ---
        if (!isTrueDamage) {
            int victimDefense = (victimPlayerStats != null) ? victimPlayerStats.defense() : (victimEntityStats != null ? victimEntityStats.defense() : 0);
            if (victimDefense > 0) {
                double defenseReductionFactor = 100.0 / (100.0 + victimDefense);
                calculatedDamage *= defenseReductionFactor;
                if (logging.isDebugMode()) logging.debug(String.format("Victim Defense %d applied (Factor: %.3f). Dmg: %.2f", victimDefense, defenseReductionFactor, calculatedDamage));
            }
        } else {
            if (logging.isDebugMode()) logging.debug("TRUE DAMAGE: Bypassing victim defense.");
        }

        // --- Final Adjustments ---
        calculatedDamage = Math.max(0.0, calculatedDamage); // No negative damage

        if (logging.isDebugMode()) logging.debug(String.format("Final Dmg: %.2f to %s. Crit: %b, TrueDmg: %b", calculatedDamage, victim.getName(), isCrit, isTrueDamage));
        event.setDamage(calculatedDamage);

        // --- Visual Feedback ---
        VisualFeedbackService vfs = getVisualFeedbackService(); // Handle potential null
        if (vfs != null) {
            if (calculatedDamage > 0.01) { // Only show indicator if damage is meaningful
                vfs.showDamageIndicator(victim, calculatedDamage, isCrit, isTrueDamage);
            }
            if (isCrit) {
                vfs.showCritEffects(attacker, victim);
            }
            // Schedule health bar update for the victim
            core.getPlugin().getServer().getScheduler().runTaskLater(core.getPlugin(), () -> {
                if (victim.isValid() && !victim.isDead() && vfs != null) vfs.updateHealthBar(victim);
            }, 1L); // Small delay for damage to fully apply
        }
    }

    public void handleEnvironmentalDamage(LivingEntity victim, double initialDamage, EntityDamageEvent.DamageCause cause, EntityDamageEvent eventToModify) {
        if (victim.isDead() || eventToModify.isCancelled()) return;
        double finalDamage = initialDamage;

        // Apply defense if the cause is in our defendable list
        if (defendableEnvironmentalCauses.contains(cause)) {
            CalculatedPlayerStats victimPlayerStats = (victim instanceof Player p) ? playerStatsService.getCalculatedStats(p) : null;
            EntityStats victimEntityStats = (victim instanceof Player) ? null : entityStatsService.getEntityStats(victim);
            int victimDefense = (victimPlayerStats != null) ? victimPlayerStats.defense() : (victimEntityStats != null ? victimEntityStats.defense() : 0);

            if (victimDefense > 0) {
                double defenseReductionFactor = 100.0 / (100.0 + victimDefense);
                finalDamage *= defenseReductionFactor;
                if (logging.isDebugMode()) logging.debug(String.format("Environmental Dmg (%s) to %s. Initial: %.2f. Defense %d (Factor: %.3f). Final: %.2f", cause, victim.getName(), initialDamage, victimDefense, defenseReductionFactor, finalDamage));
            }
        }
        finalDamage = Math.max(0.0, finalDamage);
        eventToModify.setDamage(finalDamage);

        VisualFeedbackService vfs = getVisualFeedbackService();
        if (vfs != null) {
            if (finalDamage > 0.01) {
                vfs.showDamageIndicator(victim, finalDamage, false, false); // Environmental: no crit, no true
            }
            core.getPlugin().getServer().getScheduler().runTaskLater(core.getPlugin(), () -> {
                if (victim.isValid() && !victim.isDead() && vfs != null) vfs.updateHealthBar(victim);
            }, 1L);
        }
    }

    private VisualFeedbackService getVisualFeedbackService() {
        if (this.visualFeedbackService == null) {
            try { // Attempt to fetch it if it was null during init
                this.visualFeedbackService = core.getService(VisualFeedbackService.class);
            } catch (IllegalStateException e) { /* Still not available */ }
        }
        return this.visualFeedbackService;
    }
}