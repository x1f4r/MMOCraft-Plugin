package io.github.x1f4r.mmocraft.visuals;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.services.VisualFeedbackService;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.UUID;

/**
 * A BukkitRunnable task responsible for periodically updating the position
 * and display text of active health bars managed by VisualFeedbackService.
 */
public class HealthBarDisplayTask extends BukkitRunnable {
    private final VisualFeedbackService visualService;
    private final MMOCraft plugin; // To get Bukkit.getEntity()

    public HealthBarDisplayTask(VisualFeedbackService visualService, MMOCraft plugin) {
        this.visualService = visualService;
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (visualService == null) {
            this.cancel(); // Should not happen if service lifecycle is correct
            return;
        }

        // Iterate over a copy of the keys to allow safe removal from the original map by visualService.removeHealthBar()
        for (UUID entityId : new ArrayList<>(visualService.getManagedHealthBars().keySet())) {
            ArmorStand healthBarStand = visualService.getManagedHealthBars().get(entityId); // Get current AS from map
            Entity trackedEntity = plugin.getServer().getEntity(entityId); // Fetch entity by UUID

            if (trackedEntity instanceof LivingEntity livingTarget &&
                    livingTarget.isValid() && !livingTarget.isDead() &&
                    healthBarStand != null && healthBarStand.isValid() && !healthBarStand.isDead()) {

                // Both the tracked entity and its health bar armor stand are valid.
                // visualService.updateHealthBar() will handle teleporting the armor stand
                // and updating its name based on the livingTarget's current health.
                visualService.updateHealthBar(livingTarget);
            } else {
                // The tracked entity is no longer valid/alive, or its health bar armor stand is gone.
                // Remove the health bar and its entry from the map.
                visualService.removeHealthBar(entityId);
            }
        }
    }
}