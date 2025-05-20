package io.github.x1f4r.mmocraft.ai.behaviors;

import io.github.x1f4r.mmocraft.ai.AIController;
import io.github.x1f4r.mmocraft.ai.AbstractAIBehavior;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.entities.CustomMobType;
import io.github.x1f4r.mmocraft.services.LoggingService; // Added import
import org.bukkit.GameMode;
// NMS / CraftBukkit imports - assuming a version like 1.20.R1 for CraftBukkit path
// The actual version (v1_20_R1) might need adjustment based on the project's environment
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftMob;
import net.minecraft.world.entity.Mob debilitating_illness; // NMS Mob
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob; // For Bukkit pathfinding
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class BasicMeleeBehavior extends AbstractAIBehavior {
    private final double followSpeedMultiplier;
    private final double attackRangeSquared; // Store squared for efficiency
    private final double detectionRangeSquared;
    private long lastAttackTick = 0;
    private int attackCooldownTicks = 20; // Default 1 attack per second

    public BasicMeleeBehavior(String behaviorId, double followSpeedMultiplier, double attackRange, double detectionRange, int attackCooldownTicks) {
        super(behaviorId, 2, true, true, 1); // Priority 2, can interrupt, is interruptible, ticks every controller tick
        this.followSpeedMultiplier = Math.max(0.1, followSpeedMultiplier);
        this.attackRangeSquared = attackRange * attackRange;
        this.detectionRangeSquared = detectionRange * detectionRange;
        this.attackCooldownTicks = Math.max(5, attackCooldownTicks); // Min 5 ticks cooldown
    }

    // Simpler constructor with defaults for detection range and cooldown
    public BasicMeleeBehavior(String behaviorId, double followSpeedMultiplier, double attackRange) {
        this(behaviorId, followSpeedMultiplier, attackRange, 16.0, 20); // Default 16 block detection range, 20 tick cooldown
    }


    @Override
    public boolean shouldExecute(LivingEntity entity, CustomMobType mobType, MMOCore core, AIController controller) {
        LivingEntity target = controller.getSharedTarget();
        if (target == null || !target.isValid() || target.isDead()) {
            // Find new target if current is invalid
            target = findTarget(entity, core);
            controller.setSharedTarget(target);
        }
        return target != null; // Execute if we have a valid target
    }

    @Override
    public void onTick(LivingEntity entity, CustomMobType mobType, MMOCore core, AIController controller) {
        LivingEntity target = controller.getSharedTarget();
        if (target == null || !target.isValid() || target.isDead()) {
            // Target lost or died, controller will re-evaluate and potentially stop this behavior
            controller.setSharedTarget(null);
            if (entity instanceof Mob m) m.setTarget(null); // Clear Bukkit target
            return;
        }

        double distanceSquared = entity.getLocation().distanceSquared(target.getLocation());

        if (distanceSquared <= attackRangeSquared) {
            // In attack range
            if (entity instanceof org.bukkit.entity.Mob m) { // Clarify Bukkit Mob
                ((net.minecraft.world.entity.Mob)((CraftMob) m).getHandle()).getNavigation().stopPathfinding(); // Stop moving if in range
            }
            if (core.getPlugin().getServer().getCurrentTick() >= lastAttackTick + attackCooldownTicks) {
                if (hasLineOfSight(entity, target)) {
                    // Bukkit's attack method handles animation and sound generally.
                    if (entity instanceof Mob m) {
                        m.attack(target); // Mob attacks target
                    } else {
                        // For non-Mob LivingEntities, or if more control is needed:
                        // entity.swingMainHand(); // or offhand
                        // target.damage(entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getValue(), entity);
                        // This damage call bypasses CombatService event directly, which might not be desired.
                        // Prefer entity.attack(target) if entity is a Mob.
                    }
                    lastAttackTick = core.getPlugin().getServer().getCurrentTick();
                    core.getService(LoggingService.class).debug(mobType.typeId() + " ("+entity.getUniqueId()+") attacked " + target.getName());
                }
            }
        } else if (distanceSquared <= detectionRangeSquared) {
            // In detection range, but not attack range: follow
            if (entity instanceof Mob m) {
                // Use Bukkit's pathfinder
                // Adjust speed attribute temporarily for this behavior? Or rely on mob's base speed.
                // For custom speed:
                // AttributeInstance speedAttr = m.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
                // double originalSpeed = speedAttr.getBaseValue();
                // speedAttr.setBaseValue(originalSpeed * followSpeedMultiplier);
                // m.getPathfinder().moveTo(target, followSpeedMultiplier); // This uses the attribute speed * followSpeedMultiplier.

                m.getPathfinder().moveTo(target, this.followSpeedMultiplier); // Multiplies entity's current base speed attribute.

                // speedAttr.setBaseValue(originalSpeed); // Restore after pathfind call if modified
            }
        } else {
            // Target is too far, clear it
            controller.setSharedTarget(null);
            if (entity instanceof Mob m) m.setTarget(null);
        }
    }

    private LivingEntity findTarget(LivingEntity entity, MMOCore core) {
        double currentMinDistSq = detectionRangeSquared + 1.0; // Start above detection range
        Player closestPlayer = null;
        for (Player p : entity.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR ||
                    p.isDead() || !p.isValid() || p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                continue;
            }
            double distSq = entity.getLocation().distanceSquared(p.getLocation());
            if (distSq < currentMinDistSq && distSq <= detectionRangeSquared) {
                if (hasLineOfSight(entity, p)) {
                    currentMinDistSq = distSq;
                    closestPlayer = p;
                }
            }
        }
        return closestPlayer; // Can be null
    }

    private boolean hasLineOfSight(LivingEntity entity, LivingEntity target) {
        // Bukkit's hasLineOfSight can be a bit basic.
        // Purpur might have better LOS checks, or use RayTrace.
        return entity.hasLineOfSight(target);
    }

    @Override
    public void onEnd(LivingEntity entity, CustomMobType mobType, MMOCore core, AIController controller) {
        if (entity instanceof org.bukkit.entity.Mob m) { // Clarify Bukkit Mob
            ((net.minecraft.world.entity.Mob)((CraftMob) m).getHandle()).getNavigation().stopPathfinding(); // Ensure mob stops moving if this behavior ends
            // If speed was modified, restore it here.
        }
        // Don't clear target here, as another behavior might use the same target.
        // Controller should manage shared target if no behavior wants it.
    }
}