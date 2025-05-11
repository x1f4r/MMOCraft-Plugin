package io.github.x1f4r.mmocraft.entities.listeners;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.services.EntityStatsService;
// CustomMobService will be added as a dependency in Part 6 for more advanced spawn handling
// import io.github.x1f4r.mmocraft.services.CustomMobService;
import io.github.x1f4r.mmocraft.services.VisualFeedbackService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class EntityLifecycleListener implements Listener {
    private final EntityStatsService entityStatsService;
    // private final CustomMobService customMobService; // For Part 6
    private final MMOCore core; // To access VisualFeedbackService

    // Constructor for Part 3 (without CustomMobService yet)
    public EntityLifecycleListener(EntityStatsService entityStatsService, MMOCore core) {
        this.entityStatsService = entityStatsService;
        this.core = core;
        // this.customMobService = null; // Explicitly null until Part 6
    }

    // Constructor for Part 6 (will include CustomMobService)
    // public EntityLifecycleListener(EntityStatsService entityStatsService, CustomMobService customMobService, MMOCore core) {
    //     this.entityStatsService = entityStatsService;
    //     this.customMobService = customMobService;
    //     this.core = core;
    // }

    /**
     * Handles creature spawns to apply custom stats or vanilla overrides.
     * In Part 6, this will interact with CustomMobService for potential vanilla mob replacement.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    // LOWEST to apply our stats before other plugins might react to the spawn with default stats.
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();

        // In Part 6, CustomMobService would be called here:
        // if (customMobService != null) {
        //     LivingEntity replacedEntity = customMobService.attemptReplaceVanillaSpawn(entity, event.getSpawnReason());
        //     if (replacedEntity != null) {
        //         event.setCancelled(true); // Original spawn cancelled, custom mob took its place
        //         // Stats and AI for 'replacedEntity' are handled by spawnCustomMob
        //         return; // Don't proceed with regular stat application for the original entity
        //     }
        // }

        // If not replaced (or CustomMobService not yet active), apply EntityStatsService logic.
        // This will apply vanilla overrides or stats based on NBT if already a custom mob.
        entityStatsService.applyStatsToEntity(entity);
    }

    /**
     * Handles entity deaths to clean up any managed stats or AI controllers.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    // MONITOR to act after all other plugins have processed the death.
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof LivingEntity livingEntity) {
            // In Part 6, CustomMobService would handle AIController cleanup:
            // if (customMobService != null) customMobService.handleEntityDespawn(livingEntity);

            entityStatsService.clearStatsFromEntity(livingEntity, true); // true to remove from cache

            // VisualFeedbackService is responsible for removing its own health bar on death,
            // either via its own EntityDeathEvent listener or its periodic task.
            VisualFeedbackService vfs = core.getService(VisualFeedbackService.class);
            if (vfs != null) {
                vfs.removeHealthBar(livingEntity.getUniqueId());
            }
        }
    }

    /**
     * Handles chunk unloads to clean up stats/AI for entities in the unloaded chunk.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof LivingEntity livingEntity && !(entity instanceof org.bukkit.entity.Player)) {
                // In Part 6:
                // if (customMobService != null) customMobService.handleEntityDespawn(livingEntity);
                entityStatsService.clearStatsFromEntity(livingEntity, true);

                VisualFeedbackService vfs = core.getService(VisualFeedbackService.class);
                if (vfs != null) {
                    // This direct call is okay, VisualFeedbackService's own listener might also catch this.
                    vfs.removeHealthBar(livingEntity.getUniqueId());
                }
            }
        }
    }
}