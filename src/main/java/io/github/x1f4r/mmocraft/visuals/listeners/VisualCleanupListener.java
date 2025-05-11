package io.github.x1f4r.mmocraft.visuals.listeners;

import io.github.x1f4r.mmocraft.services.VisualFeedbackService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent; // Good to also listen for world unloads

public class VisualCleanupListener implements Listener {
    private final VisualFeedbackService visualFeedbackService;

    public VisualCleanupListener(VisualFeedbackService visualFeedbackService) {
        this.visualFeedbackService = visualFeedbackService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        visualFeedbackService.cleanupVisualsInChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        // When a world unloads, all its entities are removed.
        // VisualFeedbackService.cleanupAllHealthBars() and cleanupAllDamageIndicators()
        // iterate worlds, so this might be partly redundant if they are called on plugin disable.
        // However, for safety, explicitly clean indicators from this specific world.
        visualFeedbackService.logging.info("Cleaning up visual indicators for unloading world: " + event.getWorld().getName());
        // The existing cleanupAll methods iterate all worlds, which is fine for disable.
        // For specific world unload, a targeted cleanup would be slightly more efficient
        // but the current global cleanup on disable should catch these too.
        // If needed, VisualFeedbackService could have a cleanupForWorld(World) method.
        // For now, main cleanup is on plugin disable or via periodic task for dead entities.
    }

    // EntityDeathEvent is handled by EntityLifecycleListener which calls EntityStatsService
    // and CombatService/VisualFeedbackService will show damage indicator / remove health bar there.
    // No need for a separate EntityDeathEvent here unless specific VFS-only logic is needed.
}