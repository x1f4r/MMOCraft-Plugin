package io.github.x1f4r.mmocraft.entities.listeners;

import io.github.x1f4r.mmocraft.services.LoggingService;
import io.github.x1f4r.mmocraft.services.MobDropService;
import io.github.x1f4r.mmocraft.MMOCraft; // For logger access
import io.github.x1f4r.mmocraft.core.MMOCore;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

public class MobDropListener implements Listener {
    private final MobDropService mobDropService;
    private final LoggingService logging;

    public MobDropListener(MobDropService mobDropService) {
        this.mobDropService = Objects.requireNonNull(mobDropService);
        // Get logger instance safely
        LoggingService tempLogging = null;
        try {
            tempLogging = MMOCraft.getInstance().getCore().getService(LoggingService.class);
        } catch (IllegalStateException e) {
            MMOCraft.getPluginLogger().warning("MobDropListener could not fetch LoggingService during construction. Debug logs from this listener might be missing.");
        }
        this.logging = tempLogging;
    }

    // Priority HIGH to ensure our custom drops are set before other plugins might try to modify them,
    // or before vanilla drops are finalized if we are clearing them.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeathForCustomLoot(EntityDeathEvent event) {
        LivingEntity killedEntity = event.getEntity();
        Player killer = killedEntity.getKiller(); // This can be null

        // Check if MobDropService has a loot table defined for this entity (either custom or vanilla override)
        if (mobDropService.hasLootTableFor(killedEntity)) {
            if (logging != null && logging.isDebugMode()) {
                logging.debug("Entity " + killedEntity.getType().name() + " died. Attempting to generate custom loot.");
            }

            List<ItemStack> customDrops = mobDropService.generateLoot(killedEntity, killer);

            // Clear vanilla drops if we have custom drops OR if a loot table was defined (even if it results in empty drops)
            event.getDrops().clear();
            if (!customDrops.isEmpty()) {
                event.getDrops().addAll(customDrops);
            }
            // Optional: Set custom XP from LootTable definition if it has an XP field.
            // int customXp = mobDropService.getLootTableXp(killedEntity); // Method to be added to MobDropService
            // if (customXp >= 0) event.setDroppedExp(customXp);
            if (logging != null && logging.isDebugMode()) {
                logging.debug("Applied custom loot for " + killedEntity.getType().name() + ". Drops: " + customDrops.size() + " stacks.");
            }
        } else {
            if (logging != null && logging.isDebugMode()) {
                logging.debug("No custom loot table for " + killedEntity.getType().name() + ". Vanilla drops will occur.");
            }
            // If no loot table is defined by our plugin for this entity, we don't touch its drops.
        }
    }
}