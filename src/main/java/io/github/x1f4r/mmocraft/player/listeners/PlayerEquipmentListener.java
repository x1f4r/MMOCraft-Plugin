package io.github.x1f4r.mmocraft.player.listeners;

// Using Paper API for PlayerArmorChangeEvent for better precision
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.services.LoggingService;
import io.github.x1f4r.mmocraft.services.PlayerStatsService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityPickupItemEvent;

/**
 * Listens for various player events that might indicate a change in equipped items
 * or other conditions affecting player stats, and triggers a stat recalculation.
 */
public class PlayerEquipmentListener implements Listener {

    private final PlayerStatsService playerStatsService;
    private final LoggingService logging;
    private final MMOCore core; // To get plugin instance for scheduler

    public PlayerEquipmentListener(PlayerStatsService playerStatsService, MMOCore core) {
        this.playerStatsService = playerStatsService;
        this.core = core;
        this.logging = core.getService(LoggingService.class); // Get LoggingService from MMOCore
    }

    private void triggerUpdate(Player player, String eventName, String detail) {
        if (player != null && player.isOnline()) { // Ensure player is valid
            if (logging.isDebugMode()) {
                logging.debug("PlayerEquipmentListener: Triggering stat update for " + player.getName() +
                        " due to " + eventName + (detail == null || detail.isEmpty() ? "" : " (" + detail + ")"));
            }
            playerStatsService.scheduleStatsUpdate(player);
        }
    }

    // --- Primary Event for Armor Changes (Paper API) ---
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerArmorChange(PlayerArmorChangeEvent event) {
        // This is a very reliable event for armor changes from any source.
        triggerUpdate(event.getPlayer(), "PlayerArmorChangeEvent",
                "Slot: " + event.getSlotType() + ", Old: " + (event.getOldItem() != null ? event.getOldItem().getType() : "EMPTY") +
                        ", New: " + (event.getNewItem() != null ? event.getNewItem().getType() : "EMPTY"));
    }

    // --- Held Item Changes ---
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        // Fires when player changes their selected hotbar slot (main hand).
        // The new item is in event.getPlayer().getInventory().getItem(event.getNewSlot())
        // Off-hand changes are not directly covered by this for general stat items,
        // but InventoryClickEvent or PlayerSwapHandItemsEvent might catch those.
        triggerUpdate(event.getPlayer(), "PlayerItemHeldEvent", "NewSlot: " + event.getNewSlot());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        // Fires when player swaps main and off-hand items (default F key).
        // Both main hand and off-hand are affected.
        triggerUpdate(event.getPlayer(), "PlayerSwapHandItemsEvent", null);
    }


    // --- Broader Inventory Interactions for Hands & Catch-alls ---
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // PlayerArmorChangeEvent handles armor slot clicks.
        // PlayerItemHeldEvent handles hotbar selection changes for main hand.
        // PlayerSwapHandItemsEvent handles F key swap.

        // We are interested in clicks that could change the item in hand or off-hand
        // NOT via the above more specific events, or general inventory changes
        // that might indirectly affect stats if we add more complex conditions later.
        InventoryType.SlotType slotType = event.getSlotType();
        boolean mightAffectEquipment = false;

        // 1. Click in player's own inventory (main inv or hotbar)
        if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
            if (slotType == InventoryType.SlotType.QUICKBAR || slotType == InventoryType.SlotType.CONTAINER) {
                // If it's the player's current held slot or offhand (slot 40 for offhand in player inv view)
                if (event.getSlot() == player.getInventory().getHeldItemSlot() || event.getSlot() == 40) {
                    mightAffectEquipment = true;
                }
            }
        }

        // 2. Shift-clicks can move items to/from equipment slots or hands.
        // PlayerArmorChangeEvent handles shift-clicks involving armor slots.
        // If shift-clicking to hotbar (potentially changing held item if it's the active slot)
        // or from hotbar (potentially clearing held item).
        if (event.isShiftClick()) {
            mightAffectEquipment = true;
        }

        // 3. Number key (HOTBAR_SWAP, HOTBAR_MOVE_AND_READD)
        if (event.getAction() == InventoryAction.HOTBAR_SWAP || event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
            mightAffectEquipment = true;
        }

        // 4. Dropping item with Q from inventory view (DROP_ONE_SLOT, DROP_ALL_SLOT)
        if (event.getAction() == InventoryAction.DROP_ONE_SLOT || event.getAction() == InventoryAction.DROP_ALL_SLOT) {
            if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
                // If dropping from held slot or offhand
                if (event.getSlot() == player.getInventory().getHeldItemSlot() || event.getSlot() == 40) {
                    mightAffectEquipment = true;
                }
            }
        }


        if (mightAffectEquipment) {
            triggerUpdate(player, "InventoryClickEvent", "Action: " + event.getAction() + ", Slot: " + event.getSlot());
        }
    }

    // Item pickup - item goes into inventory, could become new held item if hand was empty.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            // Item is event.getItem().getItemStack()
            // Since pickup might fill an empty hand or be immediately equipped via other plugins,
            // schedule an update. A small delay ensures the inventory state is settled.
            final String itemName = event.getItem().getItemStack().getType().name();
            Bukkit.getScheduler().runTaskLater(core.getPlugin(), () -> {
                if (player.isOnline()) triggerUpdate(player, "EntityPickupItemEvent (Delayed)", itemName);
            }, 1L);
        }
    }

    // Dropping an item from hand (using Q key while not in inventory screen)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        // PlayerArmorChangeEvent handles if an armor piece is "dropped" from its slot (e.g., by breaking).
        // This listener specifically handles when the player actively drops an item.
        // If the dropped item was from the main or off-hand, stats might change.
        triggerUpdate(event.getPlayer(), "PlayerDropItemEvent", event.getItemDrop().getItemStack().getType().name());
    }

    // --- Player State Changes that might require stat re-evaluation ---
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Equipment is re-applied by Bukkit/other plugins. Stats need full recalculation.
        final Player player = event.getPlayer();
        // Delay slightly to ensure Bukkit has fully restored inventory and attributes post-respawn.
        Bukkit.getScheduler().runTaskLater(core.getPlugin(), () -> {
            if (player.isOnline()) triggerUpdate(player, "PlayerRespawnEvent (Delayed)", null);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        // For environment-dependent stats (e.g., Ender Armor in The End).
        triggerUpdate(event.getPlayer(), "PlayerChangedWorldEvent", "To: " + event.getTo().getName());
    }

    // A general catch-all on inventory close for any complex manipulations
    // that might not be caught by more specific events (e.g., items moved by dragging).
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            triggerUpdate(player, "InventoryCloseEvent", "InvType: " + event.getInventory().getType());
        }
    }
}