package io.github.x1f4r.mmocraft.listeners; // Correct package

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/**
 * Listener to disable vanilla hunger/saturation decay.
 */
public class PlayerSaturationListener implements Listener {

    // No MMOCore needed if it just performs a simple action

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            // Option 1: Force full food/saturation always
            event.setFoodLevel(20);
            player.setSaturation(20f);

            // Option 2: Only cancel food level decrease, allow eating for effects
            // if (event.getFoodLevel() < player.getFoodLevel()) {
            //    event.setCancelled(true);
            //    // Optionally keep topped up
            //    // player.setFoodLevel(20);
            //    // player.setSaturation(20f);
            // }
        }
    }
}
