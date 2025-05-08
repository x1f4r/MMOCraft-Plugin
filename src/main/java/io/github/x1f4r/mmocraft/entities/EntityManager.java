package io.github.x1f4r.mmocraft.entities;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import io.github.x1f4r.mmocraft.entities.ai.ElderDragonAI; // AI class
import org.bukkit.entity.EnderDragon;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList; // <<< ADDED IMPORT
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Manages custom entity behaviors, AI tasks, and potentially other entity-specific logic.
 */
public class EntityManager {

    private final MMOCore core;
    private final MMOPlugin plugin;
    private final Logger log;

    // Store active custom AI tasks
    private final Map<UUID, BukkitTask> activeAITasks = new HashMap<>();

    public EntityManager(MMOCore core) {
        this.core = core;
        this.plugin = core.getPlugin();
        this.log = MMOPlugin.getMMOLogger();
    }

    public void initialize() {
        log.info("EntityManager initialized.");
        // Load any persistent entity data if needed in the future
    }

    public void shutdown() {
        log.info("Stopping all active custom AI tasks...");
        int count = 0;
        // Create a copy of the values to iterate over, allowing removal from original map
        for (BukkitTask task : new ArrayList<>(activeAITasks.values())) { // <<< USES IMPORT
            if (task != null && !task.isCancelled()) {
                task.cancel();
                count++;
            }
        }
        activeAITasks.clear();
        log.info("Stopped " + count + " AI tasks.");
    }

    // --- Elder Dragon AI Management ---

    public void startElderDragonAI(EnderDragon dragon) {
        if (dragon == null || !dragon.isValid()) return;
        UUID dragonUUID = dragon.getUniqueId();

        // Cancel any existing AI for this dragon first
        stopAI(dragonUUID);

        log.info("Starting Elder Dragon AI for " + dragonUUID);
        ElderDragonAI dragonAI = new ElderDragonAI(core, dragon); // Pass core or plugin
        BukkitTask aiTask = dragonAI.runTaskTimer(plugin, 20L, 20L); // Example: Run every second
        activeAITasks.put(dragonUUID, aiTask);
    }

    // Generic method to stop AI for any entity UUID
    public void stopAI(UUID entityUUID) {
        BukkitTask existingTask = activeAITasks.remove(entityUUID);
        if (existingTask != null && !existingTask.isCancelled()) {
            existingTask.cancel();
            log.info("Stopped active AI task for entity " + entityUUID);
        }
    }

    // Called by MobDropListener or EntityDeathEvent handler in EntitySpawnListener
    public void handleEntityDeath(UUID entityUUID) {
        log.finer("Handling potential AI cleanup for dead entity: " + entityUUID);
        stopAI(entityUUID); // Ensure AI task is stopped and removed from map
    }

    // Add methods for managing other custom entities or AI types here...

}
