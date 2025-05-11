package io.github.x1f4r.mmocraft.ai.behaviors;

import io.github.x1f4r.mmocraft.ai.AIController;
import io.github.x1f4r.mmocraft.ai.AbstractAIBehavior;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.entities.CustomMobType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

import java.util.Random;

public class RandomStrollBehavior extends AbstractAIBehavior {
    private final double speedMultiplier;
    private final int maxStrollRadius;
    private final int averageStrollIntervalTicks; // Average time between choosing a new stroll target
    private long nextStrollTime = 0;
    private final Random random = new Random();

    public RandomStrollBehavior(String behaviorId, double speedMultiplier, int maxStrollRadius, int averageStrollIntervalTicks) {
        super(behaviorId, 100, false, true, 20); // Very low priority, ticks every second (20 game ticks)
        this.speedMultiplier = Math.max(0.1, speedMultiplier);
        this.maxStrollRadius = Math.max(3, maxStrollRadius);
        this.averageStrollIntervalTicks = Math.max(40, averageStrollIntervalTicks); // Min 2 seconds interval
    }

    @Override
    public boolean shouldExecute(LivingEntity entity, CustomMobType mobType, MMOCore core, AIController controller) {
        // This behavior should almost always be ready to execute if no higher priority task is running (like attacking).
        // It will only *act* (move) when nextStrollTime is met.
        return controller.getSharedTarget() == null; // Only stroll if no target
    }

    @Override
    public void onTick(LivingEntity entity, CustomMobType mobType, MMOCore core, AIController controller) {
        if (!(entity instanceof Mob m)) return;

        if (core.getPlugin().getServer().getCurrentTick() >= nextStrollTime) {
            if (m.getPathfinder().getCurrentPath() == null || m.getPathfinder().getCurrentPath().isFinished()) {
                // Mob is idle or finished previous path, find new stroll target
                Location currentLocation = entity.getLocation();
                double angle = random.nextDouble() * 2 * Math.PI;
                double radius = random.nextDouble() * maxStrollRadius;

                double dx = Math.cos(angle) * radius;
                double dz = Math.sin(angle) * radius;

                Location targetLocation = currentLocation.clone().add(dx, 0, dz);

                // Simple check for ground, find solid block below if possible
                for(int i=0; i<3; i++){ // Try up to 3 blocks down
                    Material blockBelow = targetLocation.clone().subtract(0,1,0).getBlock().getType();
                    if(blockBelow.isSolid() && targetLocation.getBlock().isPassable()){
                        break;
                    }
                    targetLocation.subtract(0,1,0); // Try one block lower
                }
                if(!targetLocation.getBlock().isPassable() || !targetLocation.clone().subtract(0,1,0).getBlock().getType().isSolid()){
                    // If still not a good spot, try near original location
                    targetLocation = entity.getLocation().add(random.nextInt(5)-2, 0, random.nextInt(5)-2);
                }


                m.getPathfinder().moveTo(targetLocation, speedMultiplier);
                // Vary next stroll time slightly
                nextStrollTime = core.getPlugin().getServer().getCurrentTick() + averageStrollIntervalTicks + random.nextInt(averageStrollIntervalTicks / 2) - (averageStrollIntervalTicks / 4);
                core.getService(LoggingService.class).debug(mobType.typeId() + " ("+entity.getUniqueId()+") strolling to " + targetLocation.toVector());
            }
        }
    }
}