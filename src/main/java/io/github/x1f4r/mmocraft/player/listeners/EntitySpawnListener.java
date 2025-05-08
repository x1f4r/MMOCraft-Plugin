package io.github.x1f4r.mmocraft.player.listeners;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.stats.EntityStatsManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class EntitySpawnListener implements Listener {

    private final EntityStatsManager entityStatsManager;

    public EntitySpawnListener(MMOCraft plugin) {
        this.entityStatsManager = plugin.getEntityStatsManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        // Also ignore ArmorStand for stat registration, as they are used for display
        if (entity instanceof Player || entity instanceof org.bukkit.entity.ArmorStand) {
            return;
        }
        // Let EntityStatsManager handle the logic, including applying stats from mobs.yml
        entityStatsManager.registerEntity(entity);
        // Health bar addition is now fully handled by DamageAndHealthDisplayManager's own CreatureSpawnEvent listener.
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        entityStatsManager.unregisterEntity(entity);
        // Health bar removal is handled by DamageAndHealthDisplayManager's own EntityDeathEvent listener.
    }
}