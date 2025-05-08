package io.github.x1f4r.mmocraft.entities; // Changed package

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import io.github.x1f4r.mmocraft.items.ItemManager;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LivingEntity; // Import LivingEntity
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import java.util.logging.Logger;

public class MobDropListener implements Listener {

    private final MMOCore core;
    private final ItemManager itemManager;
    private final EntityManager entityManager; // Get EntityManager
    private final Logger log;

    public MobDropListener(MMOCore core) {
        this.core = core;
        this.itemManager = core.getItemManager();
        this.entityManager = core.getEntityManager(); // Get from core
        this.log = MMOPlugin.getMMOLogger();
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity deadEntity = event.getEntity();
        UUID entityUUID = deadEntity.getUniqueId();

        // Handle Elder Dragon drops and AI cleanup
        if (deadEntity instanceof EnderDragon) {
            EnderDragon dragon = (EnderDragon) deadEntity;
            String mobType = dragon.getPersistentDataContainer().get(NBTKeys.MOB_TYPE_KEY, PersistentDataType.STRING);

            if ("elder_dragon".equals(mobType)) {
                log.info("Custom Elder Dragon killed! Processing custom drops...");
                event.getDrops().clear(); // Clear default drops
                event.setDroppedExp(500); // Set custom XP

                ItemStack aotd = itemManager.getItem("aspect_of_the_dragons");
                if (aotd != null) {
                    event.getDrops().add(aotd);
                } else {
                    log.warning("Attempted to drop AOTD, but item 'aspect_of_the_dragons' not found!");
                }
                // Add other potential Elder Dragon drops here...
            }
            // Always try to clean up AI for any Ender Dragon death handled by this plugin
            entityManager.handleEntityDeath(entityUUID);
        }
        // Add logic for other custom mob drops here...
        /*
        else if (deadEntity instanceof Zombie) {
             String mobType = deadEntity.getPersistentDataContainer().get(NBTKeys.MOB_TYPE_KEY, PersistentDataType.STRING);
             if ("crypt_ghoul".equals(mobType)) { // Example
                 event.getDrops().clear();
                 event.setDroppedExp(10);
                 // Add crypt ghoul drops...
             }
             entityManager.handleEntityDeath(entityUUID); // Clean up potential AI
        }
        */
         else {
             // For any other non-player mob death, ensure potential AI tasks are cleaned up
             if (!(deadEntity instanceof org.bukkit.entity.Player)) {
                 entityManager.handleEntityDeath(entityUUID);
             }
         }
    }
}
