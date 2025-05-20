package io.github.x1f4r.mmocraft.crafting.listeners;

import io.github.x1f4r.mmocraft.services.CraftingGUIService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor; // Added import
import org.bukkit.Material;
import org.bukkit.Sound; // Added import
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap; // Added import
import java.util.Objects;

public class CraftingGUIListener implements Listener {

    private final CraftingGUIService craftingService;
    // private final MMOCraft plugin; // Removed field

    public CraftingGUIListener(CraftingGUIService craftingService) { // Removed plugin parameter
        this.craftingService = Objects.requireNonNull(craftingService, "CraftingService cannot be null.");
        // this.plugin = Objects.requireNonNull(plugin, "Plugin instance cannot be null."); // Removed assignment
    }

    @EventHandler(priority = EventPriority.HIGH) // High to override vanilla table behavior if needed
    public void onPlayerInteractWithCraftingTable(PlayerInteractEvent event) {
        if (!craftingService.shouldOverrideVanillaCraftingTable()) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // Only main hand interaction
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null && clickedBlock.getType() == Material.CRAFTING_TABLE) {
            event.setCancelled(true); // Prevent vanilla GUI from opening
            craftingService.openCustomCraftingGUI(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClickInCustomCrafter(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check if the open inventory is our custom crafter using Adventure Component title
        if (!CraftingGUIService.GUI_TITLE_COMPONENT.equals(event.getView().title())) return;

        Inventory clickedInventory = event.getClickedInventory(); // The inventory that was clicked (GUI or player's)
        Inventory topInventory = event.getView().getTopInventory(); // The custom crafter GUI

        int rawSlot = event.getRawSlot(); // Slot index in the combined view (top + bottom)
        int slotInClickedInv = event.getSlot(); // Slot index within the clickedInventory itself

        // --- Handle clicks on the RESULT SLOT ---
        if (topInventory.equals(clickedInventory) && slotInClickedInv == CraftingGUIService.RESULT_SLOT) {
            event.setCancelled(true); // Always control interaction with result slot

            ItemStack resultItem = topInventory.getItem(CraftingGUIService.RESULT_SLOT);
            if (resultItem != null && !resultItem.getType().isAir() && !resultItem.isSimilar(CraftingGUIService.BARRIER_ITEM_NO_RECIPE)) {
                ItemStack cursorItem = event.getCursor();
                // Allow taking if cursor is empty OR can stack with result
                if (cursorItem == null || cursorItem.getType().isAir() ||
                        (cursorItem.isSimilar(resultItem) && cursorItem.getAmount() + resultItem.getAmount() <= resultItem.getMaxStackSize())) {
                    craftingService.attemptCraft(topInventory, player, event.getClick().isShiftClick());
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1f); // Sound import added
                }
            }
            return; // Result slot interaction handled
        }

        // --- Prevent interaction with decorative/arrow slots in the GUI ---
        if (topInventory.equals(clickedInventory)) { // Click was within the custom crafter GUI
            if (!CraftingGUIService.INPUT_SLOTS.contains(slotInClickedInv) && slotInClickedInv != CraftingGUIService.ARROW_SLOT) {
                event.setCancelled(true); // Clicked a filler pane or other non-interactive slot
                return;
            }
            if (slotInClickedInv == CraftingGUIService.ARROW_SLOT) {
                event.setCancelled(true); // Arrow is purely visual
                return;
            }
        }

        // --- If click affects input slots or player inventory, schedule a result update ---
        // This covers: placing item in input, taking from input, shift-clicking to/from input,
        // clicking in player inventory while custom GUI is open.
        boolean requiresUpdate = false;
        if (topInventory.equals(clickedInventory) && CraftingGUIService.INPUT_SLOTS.contains(slotInClickedInv)) {
            requiresUpdate = true; // Clicked an input slot
        } else if (event.getView().getBottomInventory().equals(clickedInventory)) {
            requiresUpdate = true; // Clicked player's inventory
        } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // This action moves items between inventories (e.g., shift-click)
            // If item moves into/out of an input slot, or from player inv to GUI, or GUI to player inv
            requiresUpdate = true;
        }

        // Number key actions (HOTBAR_SWAP, etc.)
        if (event.getClick() == ClickType.NUMBER_KEY) {
            requiresUpdate = true;
        }

        if (requiresUpdate) {
            craftingService.scheduleResultUpdate(topInventory, player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryDragInCustomCrafter(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!CraftingGUIService.GUI_TITLE_COMPONENT.equals(event.getView().title())) return;

        Inventory topInventory = event.getView().getTopInventory();
        boolean affectsCraftingGrid = false;

        // Check if any of the slots affected by the drag are input slots
        for (int rawSlot : event.getRawSlots()) { // Raw slots are in the context of the combined inventory view
            if (rawSlot < topInventory.getSize()) { // Dragged into the top inventory (our GUI)
                if (CraftingGUIService.INPUT_SLOTS.contains(rawSlot)) {
                    affectsCraftingGrid = true;
                } else {
                    // If dragging into a non-input slot (filler, arrow, result), cancel it.
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (affectsCraftingGrid) {
            craftingService.scheduleResultUpdate(topInventory, player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR) // MONITOR to act after event is processed by Bukkit
    public void onInventoryCloseCustomCrafter(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!CraftingGUIService.GUI_TITLE_COMPONENT.equals(event.getView().title())) return;

        Inventory topInventory = event.getInventory(); // This is the custom crafter GUI
        boolean itemsReturned = false;

        // Return items from input slots to player's inventory
        for (int slotIndex : CraftingGUIService.INPUT_SLOTS_ARRAY) {
            ItemStack item = topInventory.getItem(slotIndex);
            if (item != null && !item.getType().isAir()) {
                // Add item back to player's inventory, drop if full
                HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone()); // HashMap import added
                if (!leftovers.isEmpty()) {
                    leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                    if(!itemsReturned) player.sendMessage(Component.text("Items from crafter returned to your inventory (or dropped if full).", NamedTextColor.YELLOW)); // NamedTextColor import added
                }
                itemsReturned = true;
                topInventory.setItem(slotIndex, null); // Clear the slot in the GUI
            }
        }
        // Clear result slot as well, just in case
        topInventory.setItem(CraftingGUIService.RESULT_SLOT, null);

        if (itemsReturned) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f);
        }
    }
}