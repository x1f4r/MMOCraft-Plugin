// Corrected package to match the import in MMOCraft.java
package io.github.x1f4r.mmocraft.player.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

public class PlayerSaturationListener implements Listener {

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            // Always keep food level and saturation full to effectively disable hunger
            event.setFoodLevel(20);
            player.setSaturation(20f);
            // Alternatively, to just prevent level drop but allow eating for effects (if any):
            // if (event.getFoodLevel() < player.getFoodLevel()) { // If food level is trying to decrease
            //    event.setCancelled(true);
            //    player.setFoodLevel(20); // Keep it topped up
            //    player.setSaturation(20f); // Keep saturation high
            // }
        }
    }
}