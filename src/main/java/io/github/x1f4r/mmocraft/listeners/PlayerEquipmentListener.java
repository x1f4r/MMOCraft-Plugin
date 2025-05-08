package io.github.x1f4r.mmocraft.listeners; // General listeners package

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityPickupItemEvent; // <<< ADDED IMPORT

public class PlayerEquipmentListener implements Listener {

    private final PlayerStatsManager statsManager;

    public PlayerEquipmentListener(MMOCore core) {
        this.statsManager = core.getPlayerStatsManager();
    }

    // --- Events that trigger a stat recalculation ---

    // Covers direct armor slots, inventory clicks, shift clicks
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        InventoryType.SlotType slotType = event.getSlotType();

        // Check if the click could affect equipped armor or held item
        if (slotType == InventoryType.SlotType.ARMOR || event.getSlot() == player.getInventory().getHeldItemSlot()) {
            statsManager.scheduleStatsUpdate(player);
            return;
        }
        // Check shift-clicks or number keys that move items to/from armor/hotbar slots
        if (event.isShiftClick() || event.getAction() == InventoryAction.HOTBAR_SWAP || event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
            // A bit broader, but safer to update if interaction involves player inv
            if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
                statsManager.scheduleStatsUpdate(player);
            } else if (event.getView().getBottomInventory().equals(player.getInventory())) {
                // If clicking in top inventory but shift-clicking (moves to player inv)
                statsManager.scheduleStatsUpdate(player);
            }
        }
        // Clicks involving the player's main inventory might affect held item if it moves
        if(event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER){
            statsManager.scheduleStatsUpdate(player);
        }
    }

    // When dropping items from armor slots or held slot
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        // Check if the dropped item was potentially from an equipment slot
        // This is tricky without knowing the exact slot it came from.
        // A scheduled update is relatively cheap.
        statsManager.scheduleStatsUpdate(event.getPlayer());
    }

    // When changing held item slot
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        statsManager.scheduleStatsUpdate(event.getPlayer());
    }

    // When closing inventory (catches drag events, etc.)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            statsManager.scheduleStatsUpdate((Player) event.getPlayer());
        }
    }

    // After respawning, equipment might change or effects reset
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        statsManager.scheduleStatsUpdate(event.getPlayer());
    }

    // World changes can affect environment-dependent stats (like Ender Armor)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        statsManager.scheduleStatsUpdate(event.getPlayer());
    }

    // When picking up items (might go into armor slots via auto-equip plugins or into hand)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) { // <<< USES IMPORT
        if (event.getEntity() instanceof Player) {
            statsManager.scheduleStatsUpdate((Player) event.getEntity());
        }
    }
}
