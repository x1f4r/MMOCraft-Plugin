package io.github.x1f4r.mmocraft.listeners;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import net.kyori.adventure.text.format.NamedTextColor;

public class CompactorGUIListener implements Listener {

    private final Logger log;

    public static final String COMPACTOR_GUI_TITLE_PREFIX = "Personal Compactor - "; // Title prefix
    // Define all possible slot indices for filters in a 3x9 GUI (middle row, spread out)
    // Max 7 slots can comfortably fit in the middle row of a 27-slot GUI (9 slots wide).
    // Indices: 9  10 11 12 13 14 15 16 17 (middle row)
    // Example for 1 slot: 13
    // Example for 3 slots: 11, 13, 15
    // Example for 5 slots: 10, 11, 13, 15, 16 (no, this is too tight)
    // Let's use: 10, 11, 12, 13, 14, 15, 16 for up to 7
    private static final int[] ALL_POSSIBLE_FILTER_SLOT_INDICES = {10, 11, 12, 13, 14, 15, 16};
    private static final int MAX_DISPLAYABLE_FILTER_SLOTS = ALL_POSSIBLE_FILTER_SLOT_INDICES.length; // Should be 7
    private static final Material PLACEHOLDER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material EMPTY_FILTER_SLOT_MATERIAL = Material.BARRIER;

    public CompactorGUIListener(MMOCore core) {
        this.log = MMOPlugin.getMMOLogger();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItem(event.getHand() != null ? event.getHand() : EquipmentSlot.HAND);

        if (itemInHand == null || itemInHand.getType() == Material.AIR || !itemInHand.hasItemMeta()) {
            return;
        }

        ItemMeta meta = itemInHand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (pdc.has(NBTKeys.UTILITY_ID_KEY, PersistentDataType.STRING)) {
            String utilityId = pdc.get(NBTKeys.UTILITY_ID_KEY, PersistentDataType.STRING);
            if (utilityId != null && utilityId.startsWith("personal_compactor_")) {
                event.setCancelled(true);
                openCompactorGUI(player, itemInHand, utilityId);
            }
        }
    }

    private void openCompactorGUI(Player player, ItemStack compactorItem, String utilityId) {
        String guiTitle = COMPACTOR_GUI_TITLE_PREFIX + utilityId; // Store utilityId in title
        Inventory gui = Bukkit.createInventory(null, 27, Component.text(guiTitle));

        // Fill background
        ItemStack placeholderPane = new ItemStack(PLACEHOLDER_MATERIAL);
        ItemMeta placeholderMeta = placeholderPane.getItemMeta();
        placeholderMeta.displayName(Component.text(" "));
        placeholderPane.setItemMeta(placeholderMeta);
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, placeholderPane.clone());
        }

        PersistentDataContainer compactorPdc = compactorItem.getItemMeta().getPersistentDataContainer();
        int actualFilterSlots = compactorPdc.getOrDefault(NBTKeys.COMPACTOR_SLOT_COUNT_KEY, PersistentDataType.INTEGER, 1);
        actualFilterSlots = Math.min(actualFilterSlots, MAX_DISPLAYABLE_FILTER_SLOTS); // Cap at what we can display
        log.fine("Opening compactor " + utilityId + " GUI for " + player.getName() + " with " + actualFilterSlots + " filter slots.");

        // Determine which GUI slots to use based on actualFilterSlots
        // Simple approach: take the first 'actualFilterSlots' from ALL_POSSIBLE_FILTER_SLOT_INDICES
        // More complex logic could center them, etc.
        List<Integer> activeFilterSlotIndices = new ArrayList<>();
        for(int i=0; i < actualFilterSlots; i++) {
            activeFilterSlotIndices.add(ALL_POSSIBLE_FILTER_SLOT_INDICES[i]);
        }

        for (int filterKeyIndex = 0; filterKeyIndex < actualFilterSlots; filterKeyIndex++) {
            int guiSlotIndex = activeFilterSlotIndices.get(filterKeyIndex);
            NamespacedKey filterNbtKey = NBTKeys.getCompactorFilterKey(filterKeyIndex); 

            if (compactorPdc.has(filterNbtKey, PersistentDataType.STRING)) {
                String materialName = compactorPdc.get(filterNbtKey, PersistentDataType.STRING);
                try {
                    Material filterMaterial = Material.valueOf(materialName);
                    ItemStack filterDisplayItem = new ItemStack(filterMaterial);
                    ItemMeta filterDisplayMeta = filterDisplayItem.getItemMeta();
                    filterDisplayMeta.displayName(Component.text(filterMaterial.name()).color(NamedTextColor.GREEN));
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.text("Filter Slot " + (filterKeyIndex + 1) + ": ").color(NamedTextColor.YELLOW)
                        .append(Component.text(materialName).color(NamedTextColor.WHITE)));
                    lore.add(Component.text("Click with an item to change.").color(NamedTextColor.GRAY));
                    lore.add(Component.text("Click with empty hand to clear.").color(NamedTextColor.GRAY));
                    filterDisplayMeta.lore(lore);
                    filterDisplayItem.setItemMeta(filterDisplayMeta);
                    gui.setItem(guiSlotIndex, filterDisplayItem);
                } catch (IllegalArgumentException e) {
                    log.warning("[CompactorGUI] Invalid material stored in NBT for " + utilityId + " slotKey " + filterKeyIndex + ": " + materialName);
                    setEmptyFilterSlot(gui, guiSlotIndex, filterKeyIndex);
                }
            } else {
                setEmptyFilterSlot(gui, guiSlotIndex, filterKeyIndex);
            }
        }

        player.openInventory(gui);
    }

    private void setEmptyFilterSlot(Inventory gui, int guiSlotIndex, int filterIndex) {
        ItemStack emptyFilterItem = new ItemStack(EMPTY_FILTER_SLOT_MATERIAL);
        ItemMeta emptyMeta = emptyFilterItem.getItemMeta();
        emptyMeta.displayName(Component.text("Empty Filter Slot " + (filterIndex + 1)).color(NamedTextColor.RED));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Click with an item to set filter.").color(NamedTextColor.GRAY));
        emptyMeta.lore(lore);
        emptyFilterItem.setItemMeta(emptyMeta);
        gui.setItem(guiSlotIndex, emptyFilterItem);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getInventory();

        Component titleComponent = event.getView().title();
        String title = "";
        if (titleComponent instanceof net.kyori.adventure.text.TranslatableComponent) {
            // This might be more complex if the title is translatable with args
             title = ((net.kyori.adventure.text.TranslatableComponent) titleComponent).key(); // or fallback or serialize
        } else if (titleComponent instanceof net.kyori.adventure.text.TextComponent) {
            title = ((net.kyori.adventure.text.TextComponent) titleComponent).content();
        }

        if (!title.startsWith(COMPACTOR_GUI_TITLE_PREFIX)) {
            return;
        }

        event.setCancelled(true); // Cancel all clicks in this GUI by default

        String utilityIdFromTitle = title.substring(COMPACTOR_GUI_TITLE_PREFIX.length());
        ItemStack compactorItem = null;
        EquipmentSlot handSlot = null;

        // Find the compactor item in player's hands
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        ItemStack offHandItem = player.getInventory().getItemInOffHand();

        if (mainHandItem.hasItemMeta()) {
            String mainHandUtilId = mainHandItem.getItemMeta().getPersistentDataContainer().get(NBTKeys.UTILITY_ID_KEY, PersistentDataType.STRING);
            if (utilityIdFromTitle.equals(mainHandUtilId)) {
                compactorItem = mainHandItem;
                handSlot = EquipmentSlot.HAND;
            }
        }
        if (compactorItem == null && offHandItem.hasItemMeta()) {
            String offHandUtilId = offHandItem.getItemMeta().getPersistentDataContainer().get(NBTKeys.UTILITY_ID_KEY, PersistentDataType.STRING);
            if (utilityIdFromTitle.equals(offHandUtilId)) {
                compactorItem = offHandItem;
                handSlot = EquipmentSlot.OFF_HAND;
            }
        }

        if (compactorItem == null) {
            log.warning("[CompactorGUI] Could not find compactor item '" + utilityIdFromTitle + "' for player " + player.getName() + " after GUI click. Closing GUI.");
            player.closeInventory();
            player.sendMessage(Component.text("Error: Compactor item not found. Please try again.").color(NamedTextColor.RED));
            return;
        }
        
        PersistentDataContainer compactorPdcForSlots = compactorItem.getItemMeta().getPersistentDataContainer();
        int actualFilterSlots = compactorPdcForSlots.getOrDefault(NBTKeys.COMPACTOR_SLOT_COUNT_KEY, PersistentDataType.INTEGER, 1);
        actualFilterSlots = Math.min(actualFilterSlots, MAX_DISPLAYABLE_FILTER_SLOTS);

        List<Integer> activeFilterSlotIndices = new ArrayList<>();
        for(int i=0; i < actualFilterSlots; i++) {
            activeFilterSlotIndices.add(ALL_POSSIBLE_FILTER_SLOT_INDICES[i]);
        }

        int clickedSlot = event.getRawSlot();
        int filterKeyIndex = -1; // This is the 0-indexed key for NBT (e.g., 0, 1, 2...)
        for (int i = 0; i < activeFilterSlotIndices.size(); i++) {
            if (activeFilterSlotIndices.get(i) == clickedSlot) {
                filterKeyIndex = i;
                break;
            }
        }

        if (filterKeyIndex != -1) { // Clicked one of the active filter slots
            ItemStack cursorItem = event.getCursor();
            ItemMeta compactorMeta = compactorItem.getItemMeta();
            PersistentDataContainer compactorPdc = compactorMeta.getPersistentDataContainer();
            NamespacedKey nbtFilterKey = NBTKeys.getCompactorFilterKey(filterKeyIndex);

            log.fine("Player " + player.getName() + " clicked compactor GUI slot " + clickedSlot + ", mapped to filterKeyIndex " + filterKeyIndex);

            if (cursorItem != null && cursorItem.getType() != Material.AIR && cursorItem.getType().isItem()) { // Ensure it's an item
                Material newFilterMaterial = cursorItem.getType();
                compactorPdc.set(nbtFilterKey, PersistentDataType.STRING, newFilterMaterial.toString());
                compactorItem.setItemMeta(compactorMeta); 
                if (handSlot != null) player.getInventory().setItem(handSlot, compactorItem);
                log.fine("Set filter " + filterKeyIndex + " for compactor " + utilityIdFromTitle + " to " + newFilterMaterial.name());

                ItemStack filterDisplayItem = new ItemStack(newFilterMaterial);
                ItemMeta filterDisplayMeta = filterDisplayItem.getItemMeta();
                filterDisplayMeta.displayName(Component.text(newFilterMaterial.name()).color(NamedTextColor.GREEN));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Filter Slot " + (filterKeyIndex + 1) + ": ").color(NamedTextColor.YELLOW)
                    .append(Component.text(newFilterMaterial.toString()).color(NamedTextColor.WHITE)));
                lore.add(Component.text("Click with an item to change.").color(NamedTextColor.GRAY));
                lore.add(Component.text("Click with empty hand to clear.").color(NamedTextColor.GRAY));
                filterDisplayMeta.lore(lore);
                filterDisplayItem.setItemMeta(filterDisplayMeta);
                clickedInventory.setItem(clickedSlot, filterDisplayItem);

                ItemStack newCursorItem = cursorItem.clone();
                newCursorItem.setAmount(cursorItem.getAmount() - 1);
                event.getView().setCursor(newCursorItem.getAmount() > 0 ? newCursorItem : null);
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1.5f);

            } else if (compactorPdc.has(nbtFilterKey, PersistentDataType.STRING) && (cursorItem == null || cursorItem.getType() == Material.AIR)){
                compactorPdc.remove(nbtFilterKey);
                compactorItem.setItemMeta(compactorMeta); 
                if (handSlot != null) player.getInventory().setItem(handSlot, compactorItem);
                log.fine("Cleared filter " + filterKeyIndex + " for compactor " + utilityIdFromTitle);

                setEmptyFilterSlot(clickedInventory, clickedSlot, filterKeyIndex);
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1.0f);
            }
        } 
    }

    // TODO: Implement InventoryCloseEvent handler if needed (e.g., returning items)
} 