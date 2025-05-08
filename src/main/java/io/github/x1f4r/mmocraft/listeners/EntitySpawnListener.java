package io.github.x1f4r.mmocraft.listeners; // General listeners package

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.stats.EntityStatsManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand; // Import ArmorStand
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class EntitySpawnListener implements Listener {

    private final EntityStatsManager entityStatsManager;

    public EntitySpawnListener(MMOCore core) {
        this.entityStatsManager = core.getEntityStatsManager();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true) // Process early to apply stats
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled()) return;

        LivingEntity entity = event.getEntity();

        // Ignore players and armor stands (used for display)
        if (entity instanceof Player || entity instanceof ArmorStand) {
            return;
        }
        // Let the manager handle registration and stat application
        entityStatsManager.registerEntity(entity);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) // Monitor death to clean up last
    public void onEntityDeath(EntityDeathEvent event) {
        // Unregister stats from the cache and restore original attributes
        // Note: DamageAndHealthDisplayManager also listens to remove health bar
        entityStatsManager.unregisterEntity(event.getEntity());
    }

    // Optional: Handle ChunkUnloadEvent to unregister entities if necessary,
    // although death event should cover most cases. Be careful with performance.
    /*
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player || entity instanceof ArmorStand)) {
                entityStatsManager.unregisterEntity((LivingEntity) entity);
            }
        }
    }
    */
}

