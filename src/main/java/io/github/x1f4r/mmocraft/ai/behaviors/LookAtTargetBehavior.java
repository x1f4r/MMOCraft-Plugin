package io.github.x1f4r.mmocraft.ai.behaviors;

import io.github.x1f4r.mmocraft.ai.AIController;
import io.github.x1f4r.mmocraft.ai.AbstractAIBehavior;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.entities.CustomMobType;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public class LookAtTargetBehavior extends AbstractAIBehavior {
    private final double maxLookRangeSquared;

    public LookAtTargetBehavior(String behaviorId, double maxLookRange) {
        super(behaviorId, 50, false, true, 5); // Mid priority, ticks fairly often (every 1/4 second)
        this.maxLookRangeSquared = maxLookRange * maxLookRange;
    }

    @Override
    public boolean shouldExecute(LivingEntity entity, CustomMobType mobType, MMOCore core, AIController controller) {
        return controller.getSharedTarget() != null &&
                entity.getLocation().distanceSquared(controller.getSharedTarget().getLocation()) <= maxLookRangeSquared;
    }

    @Override
    public void onTick(LivingEntity entity, CustomMobType mobType, MMOCore core, AIController controller) {
        LivingEntity target = controller.getSharedTarget();
        if (target == null || !target.isValid() || target.isDead()) return;

        Location entityLoc = entity.getEyeLocation();
        Location targetLoc = target.getEyeLocation();

        Vector directionToTarget = targetLoc.toVector().subtract(entityLoc.toVector()).normalize();

        // Calculate yaw and pitch
        // Bukkit's Location.setDirection uses these calculations internally.
        Location newLookLocation = entityLoc.clone();
        newLookLocation.setDirection(directionToTarget);

        // Apply the new facing direction to the entity's location
        // This directly sets where the entity is looking.
        // For mobs, this will affect their head rotation.
        // For players, it can also change their view (but usually players control their own view).
        if (entity.getLocation().getYaw() != newLookLocation.getYaw() || entity.getLocation().getPitch() != newLookLocation.getPitch()) {
            Location currentEntityLocation = entity.getLocation();
            currentEntityLocation.setYaw(newLookLocation.getYaw());
            currentEntityLocation.setPitch(newLookLocation.getPitch());
            // Teleporting to the same location but with new yaw/pitch updates the facing direction.
            // For mobs, this is generally safe. For players, it can be jarring.
            // This is primarily for mobs.
            entity.teleport(currentEntityLocation);
            // Alternatively, for mobs specifically, if NMS is an option, setting head/body yaw directly might be smoother.
            // Purpur might have API for this: entity.setRotation(yaw, pitch);
        }
    }
}