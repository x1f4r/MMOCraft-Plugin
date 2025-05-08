package io.github.x1f4r.mmocraft.mobs;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.items.ItemManager;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask; // Import BukkitTask

import java.util.UUID; // Import UUID
import java.util.logging.Logger;

public class MobDropListener implements Listener {

    private final MMOCraft plugin;
    private final ItemManager itemManager;
    private static final Logger logger = MMOCraft.getPlugin(MMOCraft.class).getLogger();


    public MobDropListener(MMOCraft plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return;
        }
        EnderDragon dragon = (EnderDragon) event.getEntity();
        UUID dragonUUID = dragon.getUniqueId(); // Get UUID for AI task management

        String mobType = null;
        if (NBTKeys.MOB_TYPE_KEY != null) {
            mobType = dragon.getPersistentDataContainer().get(NBTKeys.MOB_TYPE_KEY, PersistentDataType.STRING);
        } else {
            logger.severe("MOB_TYPE_KEY from NBTKeys was null! Cannot check mob type for drops.");
            // Attempt to cancel AI task even if NBT key is null, if it's in the map
            cancelDragonAITask(dragonUUID);
            return;
        }


        if (mobType != null && mobType.equals("elder_dragon")) {
            logger.info("Custom Elder Dragon killed! UUID: " + dragonUUID + ". Processing custom drops and stopping AI...");

            event.getDrops().clear();
            event.setDroppedExp(500);

            ItemStack aotd = itemManager.getItem("aspect_of_the_dragons");
            if (aotd != null) {
                event.getDrops().add(aotd);
                logger.info("Dropped Aspect of the Dragons for Elder Dragon kill.");
            } else {
                logger.warning("Attempted to drop AOTD, but item 'aspect_of_the_dragons' is not loaded/defined in ItemManager!");
            }

            // Cancel and remove the AI task for this dragon
            cancelDragonAITask(dragonUUID);

        } else {
            // If it's a vanilla dragon or some other custom dragon not handled here,
            // still try to cancel an AI task if one was associated with it by mistake.
            cancelDragonAITask(dragonUUID);
        }
    }

    private void cancelDragonAITask(UUID dragonUUID) {
        BukkitTask aiTask = plugin.getActiveDragonAIs().remove(dragonUUID);
        if (aiTask != null) {
            if (!aiTask.isCancelled()) {
                aiTask.cancel();
                logger.info("Successfully cancelled and removed AI task for dragon " + dragonUUID);
            } else {
                logger.info("AI task for dragon " + dragonUUID + " was already cancelled but removed from map.");
            }
        } else {
            // logger.info("No active AI task found in map for dragon " + dragonUUID + " upon death (might be normal if AI wasn't custom or already stopped).");
        }
    }
}
