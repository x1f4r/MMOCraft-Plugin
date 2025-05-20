package io.github.x1f4r.mmocraft.entities.listeners;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.services.CustomMobService;
import io.github.x1f4r.mmocraft.services.EntityStatsService;
import io.github.x1f4r.mmocraft.services.LoggingService; // Added import
import io.github.x1f4r.mmocraft.services.VisualFeedbackService; // Optional, for direct VFS interaction if needed
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer; // Added import
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Objects;

public class CustomMobLifecycleListener implements Listener {
    private final CustomMobService customMobService;
    private final EntityStatsService entityStatsService;
    private final MMOCore core; // To access other services if needed

    public CustomMobLifecycleListener(CustomMobService customMobService, EntityStatsService entityStatsService, MMOCore core) {
        this.customMobService = Objects.requireNonNull(customMobService, "CustomMobService cannot be null.");
        this.entityStatsService = Objects.requireNonNull(entityStatsService, "EntityStatsService cannot be null.");
        this.core = Objects.requireNonNull(core, "MMOCore cannot be null.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) // High to replace before other plugins modify too much
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();

        // Attempt to replace vanilla spawn with a custom mob
        LivingEntity replacedEntity = customMobService.attemptReplaceVanillaSpawn(entity, event.getSpawnReason());

        if (replacedEntity != null) {
            // If replacement occurred, the original vanilla spawn event is cancelled,
            // and 'replacedEntity' is our new custom mob.
            event.setCancelled(true);
            // `spawnCustomMob` in CustomMobService already handles applying stats, AI, NBT, etc.
            // to `replacedEntity`. EntityStatsService.applyStatsToEntity is called within spawnCustomMob.
            // VisualFeedbackService is also triggered by EntityStatsService.
            String customNameString = "";
            if (replacedEntity.customName() != null) {
                customNameString = PlainTextComponentSerializer.plainText().serialize(replacedEntity.customName());
            }
            core.getService(LoggingService.class).debug("Vanilla spawn of " + entity.getType() +
                    " replaced by custom mob " + customNameString +
                    " (Type: " + replacedEntity.getType() + ")");
        } else {
            // No replacement occurred. This is either a vanilla mob we don't care about replacing,
            // or it's a custom mob being spawned by other means (e.g., command, spawner already tagged).
            // EntityStatsService will apply vanilla overrides or custom stats based on NBT if it's already custom.
            entityStatsService.applyStatsToEntity(entity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof LivingEntity livingEntity) {
            // Cleanup AI controllers and any specific custom mob data
            customMobService.handleEntityDespawnOrDeath(livingEntity);
            // Restore original Bukkit attributes
            entityStatsService.clearStatsFromEntity(livingEntity, true); // true to remove from active cache

            // VisualFeedbackService handles its own health bar removal either via its periodic task
            // or potentially its own death listener if it needs immediate action.
            // For safety, can also call it here:
            VisualFeedbackService vfs = core.getService(VisualFeedbackService.class);
            if (vfs != null) {
                vfs.removeHealthBar(livingEntity.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof LivingEntity livingEntity && !(entity instanceof Player)) {
                // If it's a custom mob with an AI controller, stop it
                customMobService.handleEntityDespawnOrDeath(livingEntity);
                // Revert any stat changes
                entityStatsService.clearStatsFromEntity(livingEntity, true);

                // VisualFeedbackService has its own chunk unload listener for its ArmorStands.
                // But we can also ensure our map tracking is cleared.
                VisualFeedbackService vfs = core.getService(VisualFeedbackService.class);
                if (vfs != null) {
                    vfs.removeHealthBar(livingEntity.getUniqueId());
                }
            }
        }
    }
}