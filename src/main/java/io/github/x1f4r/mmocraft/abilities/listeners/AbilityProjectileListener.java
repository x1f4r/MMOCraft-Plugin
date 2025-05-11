package io.github.x1f4r.mmocraft.abilities.listeners;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.services.LoggingService;
import io.github.x1f4r.mmocraft.services.NBTService;
import io.github.x1f4r.mmocraft.services.VisualFeedbackService; // To show damage indicators from projectile
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Objects;

public class AbilityProjectileListener implements Listener {
    private final MMOCore core; // To access various services
    private final LoggingService logging;
    // NBTService keys are static

    public AbilityProjectileListener(MMOCore core) {
        this.core = Objects.requireNonNull(core);
        this.logging = core.getService(LoggingService.class);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAbilityProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        PersistentDataContainer pdc = projectile.getPersistentDataContainer();

        // Check if it's one of our ability projectiles
        if (NBTService.get(pdc, NBTService.PROJECTILE_IS_ABILITY_BOLT_FLAG, PersistentDataType.BYTE, (byte) 0) != (byte) 1) {
            return; // Not an ability projectile we manage
        }

        if (logging.isDebugMode()) {
            String shooterName = (projectile.getShooter() instanceof Player p) ? p.getName() : (projectile.getShooter() != null ? projectile.getShooter().toString() : "Unknown");
            logging.debug("Ability projectile (" + projectile.getType() + ") shot by " + shooterName + " hit something.");
        }

        // Retrieve custom data stored on the projectile by the ability that launched it
        Integer damage = NBTService.get(pdc, NBTService.PROJECTILE_CUSTOM_DAMAGE, PersistentDataType.INTEGER, 0);
        String effectTypeStr = NBTService.get(pdc, NBTService.PROJECTILE_CUSTOM_EFFECT_TYPE, PersistentDataType.STRING, "");
        Integer effectDuration = NBTService.get(pdc, NBTService.PROJECTILE_CUSTOM_EFFECT_DURATION, PersistentDataType.INTEGER, 0);
        Integer effectAmplifier = NBTService.get(pdc, NBTService.PROJECTILE_CUSTOM_EFFECT_AMPLIFIER, PersistentDataType.INTEGER, 0);
        boolean isAoe = NBTService.get(pdc, NBTService.PROJECTILE_CUSTOM_AOE_FLAG, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
        Double aoeRadius = NBTService.get(pdc, NBTService.PROJECTILE_CUSTOM_AOE_RADIUS_KEY, PersistentDataType.DOUBLE, 3.0);
        Integer aoeDamage = NBTService.get(pdc, NBTService.PROJECTILE_CUSTOM_AOE_DAMAGE_KEY, PersistentDataType.INTEGER, damage / 2); // Default AoE damage

        // Get the shooter, which should be a Player for player-cast abilities
        Player shooter = (projectile.getShooter() instanceof Player) ? (Player) projectile.getShooter() : null;

        Location hitLocation = projectile.getLocation();
        if (event.getHitBlock() != null) {
            hitLocation = event.getHitBlockFace() != null ?
                    event.getHitBlock().getRelative(event.getHitBlockFace()).getLocation().add(0.5,0.5,0.5) :
                    event.getHitBlock().getLocation().add(0.5,0.5,0.5);
        } else if (event.getHitEntity() != null) {
            hitLocation = event.getHitEntity().getLocation().add(0, event.getHitEntity().getHeight() / 2.0, 0);
        }
        World world = projectile.getWorld();

        // Default impact visuals (can be customized per ability type if projectile stores its visual effect type)
        world.spawnParticle(Particle.SNOWFLAKE, hitLocation, 30, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.ITEM_CRACK, hitLocation, 20, 0.3, 0.3, 0.3, 0.05, new ItemStack(Material.ICE));
        world.playSound(hitLocation, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.2f);

        LivingEntity directHitEntity = null;
        if (event.getHitEntity() instanceof LivingEntity hitLivingEntity) {
            if (hitLivingEntity.equals(shooter) || hitLivingEntity instanceof ArmorStand) { // Don't hit self or armor stands
                // Projectile might just pass through or stop without effect
            } else {
                directHitEntity = hitLivingEntity;
                applyEffectsToEntity(directHitEntity, damage, effectTypeStr, effectDuration, effectAmplifier, shooter);
            }
        }

        if (isAoe) {
            if (logging.isDebugMode()) logging.debug("Ability projectile AOE triggered. Radius: " + aoeRadius + ", AoE Damage: " + aoeDamage);
            world.spawnParticle(Particle.EXPLOSION_NORMAL, hitLocation, 10, aoeRadius * 0.5, aoeRadius * 0.5, aoeRadius * 0.5, 0.05); // Smaller explosion for AoE
            world.playSound(hitLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);

            for (Entity nearby : world.getNearbyEntities(hitLocation, aoeRadius, aoeRadius, aoeRadius)) {
                if (nearby instanceof LivingEntity nearbyLiving && !nearby.equals(shooter) && !nearby.equals(directHitEntity) && !(nearby instanceof ArmorStand)) {
                    // Basic line of sight check from explosion center to target's eye location
                    // This is a simple check; more complex cover checks might be needed for balance.
                    // RayTraceResult losCheck = world.rayTraceBlocks(hitLocation, nearbyLiving.getEyeLocation().subtract(hitLocation).toVector(), aoeRadius, FluidCollisionMode.NEVER, true);
                    // if (losCheck == null || losCheck.getHitBlock() == null || losCheck.getHitEntity() == nearbyLiving) { // No blocks in the way or hit the entity itself
                    applyEffectsToEntity(nearbyLiving, aoeDamage, effectTypeStr, effectDuration / 2, effectAmplifier, shooter); // Reduced duration for AoE effects
                    // }
                }
            }
        }
        projectile.remove(); // Clean up the projectile after it has hit and processed effects
    }

    private void applyEffectsToEntity(@NotNull LivingEntity target, int damageAmount,
                                      @Nullable String effectType, int duration, int amplifier,
                                      @Nullable Player source) {
        if (!target.isValid() || target.isDead()) return;

        if (damageAmount > 0) {
            // Using Bukkit's damage method allows other plugins to interact (e.g., protection, combat logging).
            // The 'source' player is important for kill credit and event context.
            target.damage(damageAmount, source); // This damage will NOT go through CombatService again unless CombatService listens to all EntityDamageEvents.
            // It's "ability direct damage".
            if (logging.isDebugMode()) {
                logging.debug("Applied " + damageAmount + " ability projectile damage to " + target.getName() +
                        " from source " + (source != null ? source.getName() : "Unknown"));
            }

            // Show a damage indicator for this ability damage
            VisualFeedbackService vfs = core.getService(VisualFeedbackService.class); // Get VFS from core
            if (vfs != null) {
                // Assuming ability projectile damage isn't inherently "crit" or "true" unless tagged
                vfs.showDamageIndicator(target, damageAmount, false, false);
            }
        }

        if (effectType != null && !effectType.isEmpty() && duration > 0) {
            PotionEffectType bukkitEffectType = PotionEffectType.getByName(effectType.toUpperCase());
            if (bukkitEffectType != null) {
                // Override existing lower-amplifier effects of the same type, or extend duration if amplifier is same/higher
                boolean applyEffect = true;
                PotionEffect existingEffect = target.getPotionEffect(bukkitEffectType);
                if (existingEffect != null) {
                    if (existingEffect.getAmplifier() > amplifier) {
                        applyEffect = false; // Don't override stronger effect
                    } else if (existingEffect.getAmplifier() == amplifier && existingEffect.getDuration() > duration) {
                        applyEffect = false; // Don't override longer duration of same strength
                    }
                }
                if(applyEffect) {
                    target.addPotionEffect(new PotionEffect(bukkitEffectType, duration, amplifier, true, true, true)); // Ambient, Particles, Icon
                    if (logging.isDebugMode()) {
                        logging.debug("Applied potion effect " + bukkitEffectType.getName() + " (Dur: " + duration + "t, Amp: " + amplifier + ") to " + target.getName());
                    }
                }
            } else {
                logging.warn("Unknown potion effect type '" + effectType + "' defined on ability projectile.");
            }
        }
    }
}