package com.x1f4r.mmocraft.crafting.ui;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.crafting.service.RecipeRegistryService;
import com.x1f4r.mmocraft.item.service.CustomItemRegistry;
import com.x1f4r.mmocraft.playerdata.PlayerDataService;
import com.x1f4r.mmocraft.util.LoggingUtil;
import com.x1f4r.mmocraft.util.StringUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages custom crafting UIs for players.
 * This is a placeholder for a more complex UI system.
 */
public class CraftingUIManager implements Listener { // Implement Listener to handle GUI interactions

    private final MMOCraftPlugin plugin;
    private final RecipeRegistryService recipeRegistryService;
    private final PlayerDataService playerDataService; // May not be needed directly by UI manager initially
    private final CustomItemRegistry customItemRegistry;
    private final LoggingUtil logger;

    // Map to track players who have the custom crafting UI open
    private final Map<UUID, Inventory> openCraftingUIs = new ConcurrentHashMap<>();

    private static final String CRAFTING_UI_TITLE = StringUtil.colorize("&8Custom Crafting Table");
    private static final int CRAFTING_GRID_SIZE = 3 * 3; // 3x3 grid
    // TODO: Define slot indices for grid, result, and other UI elements

    public CraftingUIManager(MMOCraftPlugin plugin, RecipeRegistryService recipeRegistryService,
                             PlayerDataService playerDataService, CustomItemRegistry customItemRegistry,
                             LoggingUtil logger) {
        this.plugin = plugin;
        this.recipeRegistryService = recipeRegistryService;
        this.playerDataService = playerDataService;
        this.customItemRegistry = customItemRegistry;
        this.logger = logger;
        plugin.getServer().getPluginManager().registerEvents(this, plugin); // Register this class as a listener
        logger.debug("CraftingUIManager initialized and listeners registered.");
    }

    /**
     * Opens a custom crafting UI for the given player.
     * (Placeholder - a real UI would be more complex, e.g., using AnvilGUI or a custom inventory).
     *
     * @param player The player to open the UI for.
     */
    public void openCraftingUI(Player player) {
        // Example: 5-row inventory. 3x3 grid, 1 result slot, rest are fillers/info.
        // Grid slots: 10, 11, 12, 19, 20, 21, 28, 29, 30
        // Result slot: 24
        // Craft button slot: 25 (example)
        Inventory craftingInv = Bukkit.createInventory(null, 9 * 5, CRAFTING_UI_TITLE);

        // Setup layout (filler items, borders, info panes)
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < craftingInv.getSize(); i++) {
            // Simplistic layout: leave grid and result slot empty, fill others
            if (!isGridSlot(i) && i != getResultSlotIndex() && i != getCraftButtonSlotIndex()) {
                 craftingInv.setItem(i, filler);
            }
        }

        ItemStack craftButton = new ItemStack(Material.ANVIL);
        ItemMeta craftMeta = craftButton.getItemMeta();
        if(craftMeta != null) {
            craftMeta.setDisplayName(StringUtil.colorize("&aCraft Item"));
            craftButton.setItemMeta(craftMeta);
        }
        craftingInv.setItem(getCraftButtonSlotIndex(), craftButton);


        player.openInventory(craftingInv);
        openCraftingUIs.put(player.getUniqueId(), craftingInv);
        logger.fine("Opened custom crafting UI for " + player.getName());
    }

    // Helper methods for slot indices (example for a 5-row inventory)
    private boolean isGridSlot(int slot) {
        // Example 3x3 grid slots: 10-12, 19-21, 28-30
        return (slot >= 10 && slot <= 12) || (slot >= 19 && slot <= 21) || (slot >= 28 && slot <= 30);
    }
    private int getResultSlotIndex() { return 24; } // Example slot for result
    private int getCraftButtonSlotIndex() { return 25; } // Example slot for a craft button


    /**
     * Handles a crafting attempt when a player clicks in the custom UI.
     * (Placeholder - this would be called by an InventoryClickListener).
     *
     * @param player The player attempting to craft.
     * @param craftingGridInv The inventory representing the crafting grid part of the UI.
     */
    public void handleCraftingAttempt(Player player, Inventory craftingGridInv) {
        // This method would be called from onInventoryClick when the player clicks the result slot or a craft button
        logger.info("Crafting attempt by " + player.getName() + " - Placeholder logic.");
        // 1. Get items from the crafting grid slots of craftingGridInv.
        // 2. Call recipeRegistryService.findMatchingRecipe(RecipeType.CUSTOM_SHAPED/SHAPELESS, gridInventoryView).
        // 3. If recipe found:
        //    a. Check permissions if (recipe.hasPermission() && !player.hasPermission(recipe.getPermissionRequired())).
        //    b. Consume ingredients (adjust quantities).
        //    c. Give output item.
        //    d. Handle remaining items (e.g., empty buckets).
        // 4. Update the result slot in the UI.
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (openCraftingUIs.containsKey(player.getUniqueId())) {
            Inventory topInventory = event.getView().getTopInventory();
            if (topInventory.equals(openCraftingUIs.get(player.getUniqueId()))) {
                logger.finer("Player " + player.getName() + " clicked in custom crafting UI. Slot: " + event.getRawSlot());
                // TODO: Implement logic for custom crafting UI interactions
                // - If craft button is clicked: call handleCraftingAttempt()
                // - If result slot is clicked: give item, consume ingredients
                // - Allow placing items in grid slots
                // - Prevent taking items from result slot directly if it's just for display until craft button
                // - Update result slot display based on current grid items (on-the-fly recipe checking)

                if (event.getRawSlot() == getCraftButtonSlotIndex()) {
                    event.setCancelled(true); // Prevent taking the button
                    handleCraftingAttempt(player, topInventory); // Pass the top inventory as the grid
                } else if (event.getRawSlot() == getResultSlotIndex()) {
                    // Logic for taking the crafted item
                    // This would involve consuming ingredients from the grid
                } else if (isGridSlot(event.getRawSlot())) {
                    // Allow placing items
                    // Potentially update result slot preview after a short delay
                } else if (event.getClickedInventory() == topInventory) {
                     event.setCancelled(true); // Clicked on a filler slot or outside defined areas in top inventory
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (openCraftingUIs.containsKey(player.getUniqueId())) {
            Inventory closedInv = event.getInventory();
            // Check if the closed inventory is indeed one of our custom crafting UIs
            if (closedInv.equals(openCraftingUIs.get(player.getUniqueId()))) {
                // Return items from grid to player's inventory if not crafted
                for (int i = 0; i < closedInv.getSize(); i++) {
                    if (isGridSlot(i)) {
                        ItemStack item = closedInv.getItem(i);
                        if (item != null && !item.getType().isAir()) {
                            player.getInventory().addItem(item).values()
                                  .forEach(remaining -> player.getWorld().dropItemNaturally(player.getLocation(), remaining));
                            closedInv.clear(i);
                        }
                    }
                }
                openCraftingUIs.remove(player.getUniqueId());
                logger.fine("Closed custom crafting UI for " + player.getName() + ". Items returned.");
            }
        }
    }
}
