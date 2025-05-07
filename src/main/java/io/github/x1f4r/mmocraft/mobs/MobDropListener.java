package io.github.x1f4r.mmocraft.mobs; // Updated package

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.items.ItemManager; // Updated import
import io.github.x1f4r.mmocraft.utils.NBTKeys;   // Updated import
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

// import java.util.Random; // Keep if used for chance-based drops later
import java.util.logging.Logger;

public class MobDropListener implements Listener {

    private final MMOCraft plugin;
    private final ItemManager itemManager;
    // private final Random random = new Random(); // Keep for future use
    private static final Logger logger = MMOCraft.getPlugin(MMOCraft.class).getLogger();


    public MobDropListener(MMOCraft plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager(); // Get ItemManager from plugin
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return; // Only interested in Ender Dragons for now
        }
        EnderDragon dragon = (EnderDragon) event.getEntity();

        String mobType = null;
        if (NBTKeys.MOB_TYPE_KEY != null) { // Use NBTKeys
            mobType = dragon.getPersistentDataContainer().get(NBTKeys.MOB_TYPE_KEY, PersistentDataType.STRING);
        } else {
            logger.severe("MOB_TYPE_KEY from NBTKeys was null! Cannot check mob type for drops.");
            return; 
        }


        if (mobType != null && mobType.equals("elder_dragon")) {
            logger.info("Custom Elder Dragon killed! Processing custom drops...");

            event.getDrops().clear(); // Clear default Ender Dragon drops
            event.setDroppedExp(500); // Example: Set custom XP drop

            // --- Drop Aspect of the Dragons - 100% Chance ---
            ItemStack aotd = itemManager.getItem("aspect_of_the_dragons"); 
            if (aotd != null) {
                event.getDrops().add(aotd);
                logger.info("Dropped Aspect of the Dragons for Elder Dragon kill.");
            } else {
                logger.warning("Attempted to drop AOTD, but item 'aspect_of_the_dragons' is not loaded/defined in ItemManager!");
            }
            // -----------------------------

            // Add other custom drops for the Elder Dragon here...
            // Example: ItemStack dragonScale = itemManager.getItem("dragon_scale");
            // if (dragonScale != null) {
            //    int amount = random.nextInt(3) + 1; // 1-3 scales
            //    dragonScale.setAmount(amount);
            //    event.getDrops().add(dragonScale);
            // }

        } else {
            // logger.info("Vanilla Ender Dragon killed. No custom drops added by MMOCraft specific logic.");
            // Vanilla drops will proceed as normal if not cleared.
        }
    }
}
