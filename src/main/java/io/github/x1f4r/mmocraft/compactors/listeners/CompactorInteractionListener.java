package io.github.x1f4r.mmocraft.compactors.listeners;

import io.github.x1f4r.mmocraft.MMOCraft; // Added import
import io.github.x1f4r.mmocraft.services.CompactorService;
import io.github.x1f4r.mmocraft.services.NBTService; // For NBTService.COMPACTOR_UTILITY_ID_KEY
import io.github.x1f4r.mmocraft.services.LoggingService; // For debugging
// MMOCore import was unused here, MMOCraft.getInstance().getCore() is used directly.
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer; // Changed import
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

public class CompactorInteractionListener implements Listener {
    private final CompactorService compactorService;
    private final LoggingService logging; // Optional for more detailed logs from listener

    public CompactorInteractionListener(CompactorService compactorService) {
        this.compactorService = Objects.requireNonNull(compactorService);
        this.logging = MMOCraft.getInstance().getCore().getService(LoggingService.class); // Get logger
    }

    // --- GUI Opening ---
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteractWithCompactorItem(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == null) return; // Ensure hand is involved

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItem(event.getHand());

        if (itemInHand == null || itemInHand.getType().isAir() || !itemInHand.hasItemMeta()) return;
        ItemMeta meta = itemInHand.getItemMeta(); // Already checked hasItemMeta
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String utilityId = NBTService.get(pdc, NBTService.COMPACTOR_UTILITY_ID_KEY, PersistentDataType.STRING, null);
        if (utilityId != null && utilityId.startsWith("personal_compactor_")) {
            event.setCancelled(true); // Prevent default block interaction if any
            compactorService.openCompactorGUI(player, itemInHand);
        }
    }

    // --- GUI Click Handling ---
    @EventHandler(priority = EventPriority.HIGH) // High to ensure we control the event
    public void onCompactorGUIClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Use Adventure Component for title check internally within CompactorService is good,
        // but for listener matching, checking string prefix is often simpler for Bukkit titles.
        // The CompactorService GUI_TITLE_COMPONENT should be used by CompactorService when creating the inventory.
        // Here, we check if the title *starts with* our known prefix.
        String viewTitleString = PlainTextComponentSerializer.plainText().serialize(event.getView().title()); // Changed to PlainTextComponentSerializer
        if (!viewTitleString.startsWith(CompactorService.COMPACTOR_GUI_TITLE_PREFIX.replace("§8",""))) { // Compare plain string
            return;
        }

        event.setCancelled(true); // Cancel all clicks by default; service will un-cancel if needed or handle item movement

        Inventory clickedInventory = event.getClickedInventory(); // Inventory actually clicked in (GUI or player's)
        Inventory topInventory = event.getView().getTopInventory(); // The Compactor GUI

        // Only interested if the click was in the Compactor GUI (top inventory)
        if (!topInventory.equals(clickedInventory)) {
            // If player clicks their own inventory while compactor GUI is open, allow it for now.
            // But if they try to shift-click INTO the compactor GUI, service needs to handle it.
            // For now, simply returning might be fine if compactor doesn't accept items this way.
            // Let's assume CompactorService.handleCompactorGUIClick handles all logic if top inv is ours.
            // If it's a click in player inv, AND an attempt to move to our GUI, it needs handling.
            // This simple check is not enough for shift-clicks from player inv to compactor GUI slots.
            // The current implementation of compactorService.handleCompactorGUIClick implies it only cares about clicks *within* its GUI.
            // Let's pass the click to the service if the top inventory is ours, regardless of where the click landed.
            // The service can then decide if the click is relevant (e.g. a filter slot, or if it's a shift-click from player inv).
            // No, this is wrong. The service method should only handle clicks *on its filter slots*.
            // Let's refine:
            if (topInventory.equals(event.getClickedInventory())) { // Click was in the GUI
                ItemStack compactorItem = findCompactorItemFromGuiTitle(player, viewTitleString);
                if (compactorItem == null) {
                    player.closeInventory(); // Safety close
                    logging.warn("Compactor GUI interaction by " + player.getName() + " but could not find associated compactor item in hand for title: " + viewTitleString);
                    return;
                }
                EquipmentSlot hand = player.getInventory().getItemInMainHand().equals(compactorItem) ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
                compactorService.handleCompactorGUIClick(player, topInventory, event.getSlot(), event.getCursor(), compactorItem, hand);
            } else {
                // Click was in player's inventory. Allow normal interaction (e.g. moving items in their own inv).
                // If it was a shift-click *into* our GUI, that would be InventoryAction.MOVE_TO_OTHER_INVENTORY.
                // The Compactor GUI does not currently support items being placed into it other than via cursor on filter slots.
                event.setCancelled(false); // Allow normal player inventory interaction
            }
            return;
        }
    }

    private ItemStack findCompactorItemFromGuiTitle(Player player, String guiTitle) {
        String utilityIdFromTitle = guiTitle.substring(CompactorService.COMPACTOR_GUI_TITLE_PREFIX.replace("§8","").length());
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.hasItemMeta()) {
            String mainUtilId = NBTService.get(mainHand.getItemMeta().getPersistentDataContainer(), NBTService.COMPACTOR_UTILITY_ID_KEY, PersistentDataType.STRING, null);
            if (utilityIdFromTitle.equals(mainUtilId)) return mainHand;
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.hasItemMeta()) {
            String offUtilId = NBTService.get(offHand.getItemMeta().getPersistentDataContainer(), NBTService.COMPACTOR_UTILITY_ID_KEY, PersistentDataType.STRING, null);
            if (utilityIdFromTitle.equals(offUtilId)) return offHand;
        }
        return null;
    }

    // --- Auto-Compacting on Pickup ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false) // High to act before item is fully in inv.
    public void onPlayerPickupItemForCompactor(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getItem().getItemStack().getAmount() <= 0) return; // No item or already processed

        // If another plugin cancelled it (e.g. auto-pickup to backpack), respect it
        // unless a config option allows overriding (not implemented here for simplicity).
        if (event.isCancelled()) {
            // logging.debug("[CompactorListener] Pickup event for " + player.getName() + " already cancelled. Skipping compaction.");
            return;
        }

        // Pass a clone of the item stack being picked up, as the service might modify its amount
        // or the event itself might if part is picked up and part is compacted.
        ItemStack pickedUpStackClone = event.getItem().getItemStack().clone();
        compactorService.attemptAutoCompact(player, pickedUpStackClone, event);
        // The compactorService.attemptAutoCompact method will:
        // - Call event.setCancelled(true) and event.getItem().remove() if the item is fully compacted.
        // - Or, modify event.getItem().setItemStack() if partially compacted and some remains to be picked up.
    }

    // Optional: Handle GUI close if specific actions are needed, though compactor settings are saved on the item itself.
    // @EventHandler
    // public void onCompactorGUIClose(InventoryCloseEvent event) {
    //     if (!(event.getPlayer() instanceof Player)) return;
    //     if (!PlainComponentSerializer.plain().serialize(event.getView().title()).startsWith(CompactorService.COMPACTOR_GUI_TITLE_PREFIX.replace("§8",""))) return;
    //     // e.g., play a sound, or if settings were temporarily cached, save them now.
    // }
}