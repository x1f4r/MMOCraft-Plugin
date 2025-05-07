package io.github.x1f4r.mmocraft.player.listeners;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerEquipmentListener implements Listener {

    // private final MMOCraft plugin; // Not strictly needed
    private final PlayerStatsManager statsManager;

    public PlayerEquipmentListener(MMOCraft plugin) {
        // this.plugin = plugin;
        this.statsManager = plugin.getPlayerStatsManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        statsManager.handlePlayerJoin(event.getPlayer()); // Initializes and calculates stats
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        statsManager.handlePlayerQuit(event.getPlayer()); // Cleans up
    }
    
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Stats might need recalculation after respawn (e.g. if effects are cleared)
        statsManager.scheduleStatsUpdate(event.getPlayer());
    }
    
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        // Attributes are per-world sometimes, ensure stats are reapplied.
        statsManager.scheduleStatsUpdate(event.getPlayer());
    }


    // When player changes held item
    @EventHandler(priority = EventPriority.MONITOR) // Monitor to react after the change
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        statsManager.scheduleStatsUpdate(event.getPlayer());
    }

    // When player closes inventory (likely place for armor changes)
    // This is a broad event, but often used.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            // Check if it's the player's main inventory, not a chest etc.
            // Though any inventory close might affect stats if items were moved from/to player inv.
            statsManager.scheduleStatsUpdate((Player) event.getPlayer());
        }
    }
    
    // More granular check for armor/item changes
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        InventoryType.SlotType slotType = event.getSlotType();
        InventoryType topInvType = event.getView().getTopInventory().getType();

        // Check if an armor slot was clicked in the player's inventory
        // Or if items were shift-clicked into/out of armor slots
        // Or if an item was picked up/placed in main hand/off hand
        boolean mightAffectStats = false;
        if (slotType == InventoryType.SlotType.ARMOR || 
            slotType == InventoryType.SlotType.QUICKBAR || 
            slotType == InventoryType.SlotType.CONTAINER) { // Container for main inv slots
            
            // If the click was in the player's inventory (bottom or top if it's player inv itself)
            if (event.getClickedInventory() != null && 
                (event.getClickedInventory().getType() == InventoryType.PLAYER || topInvType == InventoryType.CRAFTING)) { // Crafting because player inv is visible
                 mightAffectStats = true;
            }
        }
        
        if (event.getAction().name().contains("SWAP") || event.getAction().name().contains("MOVE_TO_OTHER_INVENTORY")) {
            mightAffectStats = true; // Swapping with hotbar, or shift-clicking armor
        }


        if (mightAffectStats) {
            statsManager.scheduleStatsUpdate(player);
        }
    }
}
