package io.github.x1f4r.mmocraft.player.listeners;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager; // Added import
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

    private final PlayerStatsManager statsManager; // This line should now work

    public PlayerEquipmentListener(MMOCraft plugin) {
        if (plugin.getPlayerStatsManager() == null) {
            throw new IllegalStateException("PlayerStatsManager has not been initialized in MMOCraft plugin main class!");
        }
        this.statsManager = plugin.getPlayerStatsManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        statsManager.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        statsManager.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        statsManager.scheduleStatsUpdate(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        statsManager.scheduleStatsUpdate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        statsManager.scheduleStatsUpdate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            statsManager.scheduleStatsUpdate((Player) event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        InventoryType.SlotType slotType = event.getSlotType();
        InventoryType topInvType = event.getView().getTopInventory().getType();

        boolean mightAffectStats = false;
        if (slotType == InventoryType.SlotType.ARMOR ||
                slotType == InventoryType.SlotType.QUICKBAR ||
                slotType == InventoryType.SlotType.CONTAINER) {

            if (event.getClickedInventory() != null &&
                    (event.getClickedInventory().getType() == InventoryType.PLAYER || topInvType == InventoryType.CRAFTING)) {
                mightAffectStats = true;
            }
        }

        if (event.getAction().name().contains("SWAP") || event.getAction().name().contains("MOVE_TO_OTHER_INVENTORY")) {
            mightAffectStats = true;
        }

        if (mightAffectStats) {
            statsManager.scheduleStatsUpdate(player);
        }
    }
}