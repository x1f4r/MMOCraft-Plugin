package com.x1f4r.mmocraft.combat.listeners;

import com.x1f4r.mmocraft.combat.model.DamageInstance;
import com.x1f4r.mmocraft.combat.model.DamageInstance;
import com.x1f4r.mmocraft.combat.model.DamageType;
import com.x1f4r.mmocraft.combat.service.DamageCalculationService;
import com.x1f4r.mmocraft.combat.service.MobStatProvider; // Added
import com.x1f4r.mmocraft.playerdata.PlayerDataService;
import com.x1f4r.mmocraft.playerdata.model.PlayerProfile;
import com.x1f4r.mmocraft.util.LoggingUtil;
import com.x1f4r.mmocraft.util.StringUtil;

import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;

public class PlayerCombatListener implements Listener {

    private final DamageCalculationService damageCalculationService;
    private final PlayerDataService playerDataService;
    private final LoggingUtil logger;
    private final MobStatProvider mobStatProvider; // Added

    private static final Map<Material, Double> VANILLA_WEAPON_BASE_DAMAGE = new HashMap<>();
    // private static final Map<org.bukkit.entity.EntityType, Double> MOB_BASE_DAMAGE = new HashMap<>(); // Replaced by MobStatProvider

    static {
        VANILLA_WEAPON_BASE_DAMAGE.put(Material.WOODEN_SWORD, 4.0);
        VANILLA_WEAPON_BASE_DAMAGE.put(Material.STONE_SWORD, 5.0);
        VANILLA_WEAPON_BASE_DAMAGE.put(Material.IRON_SWORD, 6.0);
        VANILLA_WEAPON_BASE_DAMAGE.put(Material.GOLDEN_SWORD, 4.0);
        VANILLA_WEAPON_BASE_DAMAGE.put(Material.DIAMOND_SWORD, 7.0);
        VANILLA_WEAPON_BASE_DAMAGE.put(Material.NETHERITE_SWORD, 8.0);
        VANILLA_WEAPON_BASE_DAMAGE.put(Material.WOODEN_AXE, 3.0); // Axes are typically a bit less than swords
        VANILLA_WEAPON_BASE_DAMAGE.put(Material.STONE_AXE, 4.0);
        VANILLA_WEAPON_BASE_DAMAGE.put(Material.IRON_AXE, 5.0);
        VANILLA_WEAPON_BASE_DAMAGE.put(Material.GOLDEN_AXE, 3.0);
        VANILLA_WEAPON_BASE_DAMAGE.put(Material.DIAMOND_AXE, 6.0);
        VANILLA_WEAPON_BASE_DAMAGE.put(Material.NETHERITE_AXE, 7.0);
        // Add other tools if they are to be used as weapons: Shovel, Pickaxe, Hoe
        VANILLA_WEAPON_BASE_DAMAGE.put(Material.TRIDENT, 9.0); // Melee Trident damage

        // Mob default damages (examples, can be configured or fetched from mob attributes)
        // MOB_BASE_DAMAGE.put(org.bukkit.entity.EntityType.ZOMBIE, 3.0); // Removed - Using MobStatProvider
        // MOB_BASE_DAMAGE.put(org.bukkit.entity.EntityType.SKELETON, 2.5); // Removed - Using MobStatProvider
        // MOB_BASE_DAMAGE map is removed, MobStatProvider will be used instead.
    }


    public PlayerCombatListener(DamageCalculationService damageCalculationService,
                                PlayerDataService playerDataService, LoggingUtil logger,
                                MobStatProvider mobStatProvider) { // Added mobStatProvider
        this.damageCalculationService = damageCalculationService;
        this.playerDataService = playerDataService;
        this.logger = logger;
        this.mobStatProvider = mobStatProvider; // Added
        logger.debug("PlayerCombatListener initialized with MobStatProvider.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (!(victim instanceof LivingEntity)) {
            logger.finer("Victim is not a LivingEntity, skipping custom damage calculation.");
            return;
        }

        Entity actualAttacker = damager;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity) {
                actualAttacker = (Entity) shooter;
            } else {
                actualAttacker = null; // Projectile from dispenser or unknown source
            }
        }

        if (actualAttacker == null && !(damager instanceof Projectile)) {
             // If actualAttacker is still null here, it means non-projectile, non-entity source, or unresolvable projectile.
             // This case might be complex (e.g. TNT minecart from player action). For now, we might skip.
             logger.finer("Attacker is null and not a resolvable projectile, using default Bukkit damage for now.");
             return;
        }


        double baseWeaponDamage = event.getDamage(); // Fallback to Bukkit's original damage
        DamageType damageType = DamageType.PHYSICAL; // Default to physical for most direct entity interactions

        if (actualAttacker instanceof Player playerAttacker) {
            ItemStack mainHand = playerAttacker.getInventory().getItemInMainHand();
            if (mainHand != null && VANILLA_WEAPON_BASE_DAMAGE.containsKey(mainHand.getType())) {
                baseWeaponDamage = VANILLA_WEAPON_BASE_DAMAGE.get(mainHand.getType());
            } else if (mainHand == null || mainHand.getType() == Material.AIR) {
                baseWeaponDamage = 1.0; // Unarmed damage
            }
            // TODO: Add logic for custom weapons from MMOCraft
            // TODO: Add logic for magical attacks if player casts a spell (set DamageType.MAGICAL)
        } else if (actualAttacker instanceof LivingEntity) { // Mob attacker
            baseWeaponDamage = mobStatProvider.getBaseAttackDamage(actualAttacker.getType());
            if (damager instanceof Arrow) { // Arrow from mob (e.g. Skeleton)
                 // For projectiles from mobs, Bukkit's event.getDamage() might be more reliable
                 // or we can define projectile damages in MobStatProvider too.
                 // For now, let's assume MobStatProvider's base attack is for its primary/melee attack
                 // and use event.getDamage() for its projectiles as a starting point.
                 baseWeaponDamage = event.getDamage(); // Use Bukkit's calculated arrow damage as base
            }
        } else if (damager instanceof Projectile && actualAttacker == null) {
            // Projectile from unknown source (e.g. dispenser)
            // Use event.getDamage() or define specific damages for these projectiles
            baseWeaponDamage = event.getDamage();
            logger.finer("Damage from unowned projectile " + damager.getType() + ", using Bukkit base damage: " + baseWeaponDamage);
        }


        DamageInstance damageInstance = damageCalculationService.calculateDamage(actualAttacker != null ? actualAttacker : damager, victim, baseWeaponDamage, damageType);

        event.setDamage(damageInstance.finalDamage());

        // Logging and PlayerProfile updates (like health reduction)
        String attackerName = (actualAttacker != null) ? actualAttacker.getName() : (damager != null ? damager.getType().toString() : "Unknown Attacker");
        String victimName = victim.getName();

        if (damageInstance.evaded()) {
            logger.info(StringUtil.colorize(attackerName + "'s attack on " + victimName + " was &eEVADED&f."));
            if (victim instanceof Player && damageInstance.victimProfile() != null) {
                 ((Player) victim).sendActionBar(StringUtil.colorize("&7&oEvaded attack from " + attackerName));
            }
             if (actualAttacker instanceof Player && damageInstance.attackerProfile() != null) {
                 ((Player) actualAttacker).sendActionBar(StringUtil.colorize("&7&oYour attack was evaded by " + victimName));
            }
        } else {
            String critMessage = damageInstance.criticalHit() ? " &c(Critical!)&f" : "";
            String message = String.format("%s hit %s for %.2f %s damage%s. Base: %.2f. Mitigation: %s",
                    attackerName, victimName, damageInstance.finalDamage(),
                    damageInstance.type().name(), critMessage,
                    damageInstance.baseDamage(), damageInstance.mitigationDetails());
            logger.info(StringUtil.colorize(message));

            // Update PlayerProfile health if the victim is a player and took damage
            if (victim instanceof Player victimPlayer && damageInstance.finalDamage() > 0 && !damageInstance.evaded()) {
                PlayerProfile victimProfile = playerDataService.getPlayerProfile(victimPlayer.getUniqueId());
                if (victimProfile != null) {
                    // PlayerProfile's currentHealth will be updated based on the final damage.
                    // Bukkit's event.setDamage() handles the actual health reduction on the LivingEntity.
                    // We record this damage into our profile system.
                    // Note: This assumes that Bukkit's health and our profile health are 1:1.
                    // If PlayerProfile has a different health scale, this logic needs adjustment.
                    victimProfile.takeDamage(damageInstance.finalDamage());
                    logger.fine("Updated PlayerProfile health for " + victimName + " after taking " + String.format("%.2f", damageInstance.finalDamage()) + " damage. New profile health: " + victimProfile.getCurrentHealth());

                    // If victimProfile.getCurrentHealth() <= 0, you might trigger a custom death event or logic here
                    // For now, Bukkit will handle the death event.
                }
            }
        }
        // Future: if finalDamage > 0 and attacker is player, apply on-hit effects from equipment/buffs.
        // Future: if attacker used a skill for this attack, skill's onHit method could be called here.
    }
}
