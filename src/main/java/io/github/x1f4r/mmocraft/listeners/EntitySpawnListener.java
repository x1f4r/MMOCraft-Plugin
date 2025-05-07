package io.github.x1f4r.mmocraft.listeners;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.stats.EntityStatsManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
// Optional: For chunk unload cleanup
// import org.bukkit.event.world.ChunkUnloadEvent;
// import org.bukkit.entity.Entity;

public class EntitySpawnListener implements Listener {

    // private final MMOCraft plugin; // Only needed if you need direct plugin access here
    private final EntityStatsManager entityStatsManager;

    public EntitySpawnListener(MMOCraft plugin) {
        // this.plugin = plugin;
        this.entityStatsManager = plugin.getEntityStatsManager();
    }

    @EventHandler(priority = EventPriority.MONITOR) // Monitor, so we act after other plugins might have modified/spawned
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return; // Players are handled by PlayerStatsManager on join
        }

        // Register and apply custom stats to newly spawned mobs
        // The EntityStatsManager's initializeStatsForEntity (called by getStats or registerEntity)
        // will load from mobs.yml and apply vanilla attributes.
        // plugin.getLogger().fine("EntitySpawnListener: Creature spawned: " + entity.getType() + ", applying stats.");
        entityStatsManager.registerEntity(entity);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        // plugin.getLogger().fine("EntitySpawnListener: Entity died: " + entity.getType() + ", unregistering stats.");
        entityStatsManager.unregisterEntity(entity); // Clean up from cache
    }

    /* // Optional: Advanced cleanup for entities in unloaded chunks
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                entityStatsManager.unregisterEntity((LivingEntity) entity);
            }
        }
    }
    */
}