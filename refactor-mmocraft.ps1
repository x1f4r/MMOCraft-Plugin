# PowerShell Refactoring Script for MMOCraft Plugin
# WARNING: BACKUP YOUR PROJECT BEFORE RUNNING!
# Run this script from the root of your 'mmocraft' project directory.

Write-Host "Starting MMOCraft project refactoring..." -ForegroundColor Yellow
Write-Host "IMPORTANT: Ensure you have backed up your project!" -ForegroundColor Red
Read-Host -Prompt "Press Enter to continue if you have backed up your project, or CTRL+C to abort"

$ErrorActionPreference = "Stop" # Stop on any error

# Define base path for Java sources
$baseSrcPath = ".\src\main\java\io\github\x1f4r\mmocraft"
if (-not (Test-Path $baseSrcPath)) {
    Write-Error "Base source path not found: $baseSrcPath - Ensure you are running this script from the root of your 'mmocraft' project."
    exit 1
}

# --- Define New Directory Structure ---
$dirsToCreate = @(
    "$baseSrcPath\crafting",
    "$baseSrcPath\crafting\models",
    "$baseSrcPath\items",
    "$baseSrcPath\mobs",
    "$baseSrcPath\player",
    "$baseSrcPath\player\listeners",
    "$baseSrcPath\utils"
)

Write-Host "Creating new directory structure..."
foreach ($dir in $dirsToCreate) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        Write-Host "  Created: $dir"
    } else {
        Write-Host "  Exists: $dir"
    }
}

# --- Move Existing Files to New Locations ---
# Files that are definitely part of the original structure provided by the user
Write-Host "Moving existing files..."
$filesToMove = @{
    # Old Path Relative to $baseSrcPath       # New Path Relative to $baseSrcPath
    "MMOCraft.java"                           = "MMOCraft.java" # Stays in place, content will be overwritten
    "commands\CustomCraftCommand.java"        = "commands\CustomCraftCommand.java"
    "commands\SummonElderDragonCommand.java"  = "commands\SummonElderDragonCommand.java"
    "listeners\CraftingGUIListener.java"      = "crafting\CraftingGUIListener.java"
    "managers\RecipeManager.java"             = "crafting\RecipeManager.java"
    "models\CustomRecipe.java"                = "crafting\models\CustomRecipe.java"
    "models\RequiredItem.java"                = "crafting\models\RequiredItem.java"
    "managers\ItemManager.java"               = "items\ItemManager.java"
    "listeners\ArmorSetListener.java"         = "items\ArmorSetListener.java"
    "listeners\MobDropListener.java"          = "mobs\MobDropListener.java"
}

foreach ($entry in $filesToMove.GetEnumerator()) {
    $oldRelativePath = $entry.Key
    $newRelativePath = $entry.Value
    $oldFullPath = Join-Path $baseSrcPath $oldRelativePath
    $newFullPath = Join-Path $baseSrcPath $newRelativePath

    if ($oldRelativePath -eq $newRelativePath) { # File stays in place, content will be updated
        Write-Host "  Will update content for: $oldFullPath"
        continue
    }

    if (Test-Path $oldFullPath) {
        # Ensure the target directory for the new path exists (it should from the previous step)
        $newDirPath = Split-Path $newFullPath
        if (-not (Test-Path $newDirPath)) {
            New-Item -ItemType Directory -Path $newDirPath -Force | Out-Null
        }
        Move-Item -Path $oldFullPath -Destination $newFullPath -Force
        Write-Host "  Moved: $oldFullPath -> $newFullPath"
    } else {
        Write-Warning "  Original file not found (will create fresh if content provided): $oldFullPath"
    }
}

# PlayerAbilityListener.java and ElderDragonAI.java were introduced in the previous AI response.
# If they existed in a different (e.g., 'listeners' or 'ai') old location, they'd be handled by the general move.
# If they don't exist, they will be created fresh by the content writing step below.

# --- Define File Contents (Refactored Code) ---
# This section will be very long as it includes all Java code.

# MMOCraft.java
$mmocraftContent = @"
package io.github.x1f4r.mmocraft;

import io.github.x1f4r.mmocraft.commands.CustomCraftCommand;
import io.github.x1f4r.mmocraft.commands.SummonElderDragonCommand;
import io.github.x1f4r.mmocraft.crafting.CraftingGUIListener;
import io.github.x1f4r.mmocraft.crafting.RecipeManager;
import io.github.x1f4r.mmocraft.items.ArmorSetListener;
import io.github.x1f4r.mmocraft.items.ItemManager;
import io.github.x1f4r.mmocraft.items.PlayerAbilityListener;
import io.github.x1f4r.mmocraft.mobs.MobDropListener;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import io.github.x1f4r.mmocraft.player.listeners.PlayerDamageListener;
import io.github.x1f4r.mmocraft.player.listeners.PlayerEquipmentListener;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class MMOCraft extends JavaPlugin {

    private CraftingGUIListener craftingGUIListener; // Keep if commands need direct access
    private RecipeManager recipeManager;
    private ItemManager itemManager;
    private PlayerStatsManager playerStatsManager;

    @Override
    public void onEnable() {
        getLogger().info("MMOCraft Plugin enabling...");

        // Initialize Utilities First
        NBTKeys.init(this);

        // Ensure data folder exists for configs
        if (!getDataFolder().exists()) {
            if (!getDataFolder().mkdirs()) {
                getLogger().severe("Could not create plugin data folder!");
                // Consider disabling plugin if this fails
            }
        }

        // Initialize Managers
        this.itemManager = new ItemManager(this);         // Loads items.yml
        this.recipeManager = new RecipeManager(this);     // Loads recipes.yml
        this.playerStatsManager = new PlayerStatsManager(this);

        // Initialize and register Listeners
        this.craftingGUIListener = new CraftingGUIListener(this); // Used by CustomCraftCommand

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this.craftingGUIListener, this);
        pm.registerEvents(new MobDropListener(this), this);
        pm.registerEvents(new ArmorSetListener(this), this);
        pm.registerEvents(new PlayerAbilityListener(this), this);
        pm.registerEvents(new PlayerDamageListener(this), this);
        pm.registerEvents(new PlayerEquipmentListener(this), this);


        // Initialize and register Command(s)
        registerCommands();

        getLogger().info("MMOCraft Plugin has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("MMOCraft Plugin has been disabled!");
    }

    private void registerCommands() {
        try {
            Objects.requireNonNull(this.getCommand("customcraft"), "Command 'customcraft' not found!")
                   .setExecutor(new CustomCraftCommand(this.craftingGUIListener));
            Objects.requireNonNull(this.getCommand("summonelderdragon"), "Command 'summonelderdragon' not found!")
                   .setExecutor(new SummonElderDragonCommand(this));
        } catch (NullPointerException e) {
            getLogger().log(Level.SEVERE, "Failed to register command. Check plugin.yml and command setup.", e);
        }
    }

    // Getters for Managers (if needed by other parts, e.g. commands or other specific classes)
    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public PlayerStatsManager getPlayerStatsManager() {
        return playerStatsManager;
    }

    // Getter for Crafting GUI Listener (still needed by CustomCraftCommand)
    public CraftingGUIListener getCraftingGUIListener() {
        return craftingGUIListener;
    }
}
"@

# commands/CustomCraftCommand.java
$customCraftCommandContent = @"
package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.crafting.CraftingGUIListener; // Updated import
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CustomCraftCommand implements CommandExecutor {

    private final CraftingGUIListener guiListener;

    public CustomCraftCommand(CraftingGUIListener listener) {
        this.guiListener = listener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        if (this.guiListener != null) {
            guiListener.openCustomCraftingGUI(player);
        } else {
            player.sendMessage("Error: GUI Listener not initialized!");
            sender.getServer().getLogger().warning("CustomCraftCommand executed but guiListener was null!");
        }
        return true;
    }
}
"@

# commands/SummonElderDragonCommand.java
$summonElderDragonCommandContent = @"
package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.mobs.ElderDragonAI; // Updated import
import io.github.x1f4r.mmocraft.utils.NBTKeys; // Updated import
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public class SummonElderDragonCommand implements CommandExecutor {

    private final MMOCraft plugin;

    public SummonElderDragonCommand(MMOCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can summon the Elder Dragon.");
            return true;
        }
        Player player = (Player) sender;
        Location spawnLocation = player.getLocation();

        if (NBTKeys.MOB_TYPE_KEY == null) {
            plugin.getLogger().severe("MOB_TYPE_KEY is null! Cannot summon Elder Dragon properly.");
            player.sendMessage(ChatColor.RED + "Error: Mob Type Key not initialized. Please check server logs.");
            return true;
        }

        EnderDragon dragon = (EnderDragon) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.ENDER_DRAGON);

        dragon.setCustomName(ChatColor.translateAlternateColorCodes('&', "&5&lElder Dragon"));
        dragon.setCustomNameVisible(true);

        AttributeInstance maxHealthAttribute = dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(300.0); // Example health
            dragon.setHealth(300.0);
        } else {
            plugin.getLogger().warning("Could not set Max Health attribute for Elder Dragon!");
        }

        dragon.getPersistentDataContainer().set(NBTKeys.MOB_TYPE_KEY, PersistentDataType.STRING, "elder_dragon");
        plugin.getLogger().info("Tagged spawned dragon as elder_dragon.");

        // --- Start Custom AI ---
        if (dragon.isValid()) {
            ElderDragonAI dragonAI = new ElderDragonAI(plugin, dragon);
            dragonAI.start(); 
        }
        // --- End Custom AI ---

        player.sendMessage(ChatColor.GREEN + "An " + ChatColor.DARK_PURPLE + ChatColor.BOLD + "Elder Dragon" + ChatColor.GREEN + " with custom AI has been summoned!");
        return true;
    }
}
"@

# crafting/CraftingGUIListener.java
$craftingGUIListenerContent = @"
package io.github.x1f4r.mmocraft.crafting; // Updated package

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.crafting.models.CustomRecipe; // Updated import
import io.github.x1f4r.mmocraft.crafting.models.RequiredItem; // Updated import
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
// import org.bukkit.event.inventory.InventoryAction; // Keep if used, commented out if not explicitly
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
// import org.bukkit.inventory.meta.ItemMeta; // Keep if used, commented out if not explicitly

import java.util.*;
import java.util.logging.Logger;

public class CraftingGUIListener implements Listener {

    private final MMOCraft plugin;
    private final RecipeManager recipeManager;
    public static final String GUI_TITLE = "Custom Crafter";
    private static final Logger logger = MMOCraft.getPlugin(MMOCraft.class).getLogger();


    public CraftingGUIListener(MMOCraft plugin) {
        this.plugin = plugin;
        this.recipeManager = plugin.getRecipeManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        boolean isCustomCraftingGui = event.getView().getTitle().equals(GUI_TITLE) &&
                event.getView().getTopInventory().getType() == InventoryType.WORKBENCH;
        if (!isCustomCraftingGui) return;

        if (event.getClickedInventory() == null || !(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory topInventory = event.getView().getTopInventory();
        Inventory bottomInventory = event.getView().getBottomInventory();
        Inventory clickedInventory = event.getClickedInventory();
        int clickedSlot = event.getSlot();
        ClickType clickType = event.getClick();

        // logger.info("Crafting Click: Slot=" + clickedSlot + " Type=" + clickType + " SrcInv=" + clickedInventory.getType());

        boolean updateNeededByThisLogic = false; 

        // == 1. Handle clicks on the RESULT SLOT (Slot 0) ==
        if (clickedInventory.equals(topInventory) && clickedSlot == 0) {
            event.setCancelled(true);
            ItemStack resultItem = topInventory.getItem(0);
            if (resultItem != null && !resultItem.getType().isAir()) {
                if (clickType.isShiftClick()) {
                    handleShiftClickResult(player, topInventory);
                } else if (clickType.isLeftClick() || clickType.isRightClick()){
                    handleNormalClickResult(event, player, topInventory);
                }
                updateNeededByThisLogic = true;
            }
        }
        // == 2. Handle SHIFT-CLICK FROM PLAYER INVENTORY into Grid ==
        else if (clickType.isShiftClick() && clickedInventory.equals(bottomInventory)) {
            event.setCancelled(true);
            handleShiftClickIntoGrid(event, player, topInventory);
            updateNeededByThisLogic = true;
        }
        // == 3. Handle SHIFT-CLICK FROM GRID into Player Inventory ==
        else if (clickType.isShiftClick() && clickedInventory.equals(topInventory) && clickedSlot >= 1 && clickedSlot <= 9) {
            event.setCancelled(true);
            handleShiftClickOutOfGrid(event, player, topInventory);
            updateNeededByThisLogic = true;
        }
        // == 4. Handle Normal Clicks WITHIN the GRID (Slots 1-9) or other relevant actions that change grid items ==
        else if (clickedInventory.equals(topInventory) && clickedSlot >= 1 && clickedSlot <= 9) {
            updateNeededByThisLogic = true;
        }
        // Other clicks might not need an immediate full update of the result slot logic
        // For example, clicking in the player's own inventory while the crafting GUI is open.

        // --- Final Update Trigger ---
        if (updateNeededByThisLogic) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                updateResultSlot(topInventory, player);
                Bukkit.getScheduler().runTaskLater(plugin, player::updateInventory, 0L); // Refresh client view
            }, 1L); // 1-tick delay to allow Bukkit to process the click's item changes
        }
    }


    private void handleShiftClickResult(Player player, Inventory topInventory) {
        // logger.info("Shift-craft attempt...");
        ItemStack resultTemplate = topInventory.getItem(0);
        if (resultTemplate == null || resultTemplate.getType().isAir()) return;

        int maxCrafts = calculateMaxCrafts(topInventory, player);
        // logger.info("Max crafts calculated: " + maxCrafts);
        if (maxCrafts <= 0) return;

        int totalCraftedAmount = 0;
        ItemStack firstCraftedItem = resultTemplate.clone();
        // firstCraftedItem.setAmount(1); // The result template is already for 1 craft usually

        for (int i = 0; i < maxCrafts; i++) {
            CustomRecipe customRecipe = recipeManager.findMatchingRecipe(topInventory);
            Recipe vanillaRecipe = (customRecipe == null) ? Bukkit.getCraftingRecipe(getMatrix(topInventory), player.getWorld()) : null;
            ItemStack currentResultCheck = topInventory.getItem(0); // Re-check current result

            if (currentResultCheck == null || currentResultCheck.getType().isAir() || !currentResultCheck.isSimilar(resultTemplate)) {
                // logger.warning("Shift-craft loop stopped early: Result changed or disappeared.");
                break; 
            }

            ItemStack craftedItem = attemptCraft(player, topInventory, customRecipe, vanillaRecipe);
            if (craftedItem != null) {
                totalCraftedAmount += craftedItem.getAmount(); 
            } else {
                // logger.warning("Shift-craft loop stopped early: attemptCraft failed.");
                break; 
            }
             // After a successful craft, the ingredients are consumed. The recipe might not match anymore for the next iteration
             // if ingredients are fully depleted. The updateResultSlot will be called later,
             // which should clear the result if no recipe matches.
        }
        
        if (totalCraftedAmount > 0) {
            firstCraftedItem.setAmount(totalCraftedAmount); // Set to total amount crafted
            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(firstCraftedItem);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
                player.sendMessage(ChatColor.YELLOW + "Inventory full, some items dropped!");
            }
        }
        // logger.info("Shift-craft finished. Total Amount: " + totalCraftedAmount);
    }

    private void handleNormalClickResult(InventoryClickEvent event, Player player, Inventory topInventory) {
        // logger.info("Normal craft attempt...");
        ItemStack cursorItem = event.getCursor();

        CustomRecipe customRecipe = recipeManager.findMatchingRecipe(topInventory);
        Recipe vanillaRecipe = (customRecipe == null) ? Bukkit.getCraftingRecipe(getMatrix(topInventory), player.getWorld()) : null;
        ItemStack craftedItem = attemptCraft(player, topInventory, customRecipe, vanillaRecipe);

        if (craftedItem != null) {
            if (cursorItem == null || cursorItem.getType() == Material.AIR) {
                event.getView().setCursor(craftedItem);
            } else if (cursorItem.isSimilar(craftedItem)) {
                int canAdd = cursorItem.getMaxStackSize() - cursorItem.getAmount();
                if (canAdd >= craftedItem.getAmount()) {
                    cursorItem.setAmount(cursorItem.getAmount() + craftedItem.getAmount());
                    event.getView().setCursor(cursorItem);
                } else { // Not enough space on cursor, but some space
                     if (canAdd > 0) {
                        cursorItem.setAmount(cursorItem.getMaxStackSize());
                        craftedItem.setAmount(craftedItem.getAmount() - canAdd);
                     }
                    player.getWorld().dropItemNaturally(player.getLocation(), craftedItem);
                    player.sendMessage(ChatColor.RED + "Couldn't fully stack on cursor, some dropped!");
                }
            } else { // Cursor has a different item
                player.getWorld().dropItemNaturally(player.getLocation(), craftedItem);
                player.sendMessage(ChatColor.RED + "Clear your cursor or use an empty slot!");
            }
        } else {
            // logger.warning("Normal craft failed during attemptCraft call.");
        }
    }

    private void handleShiftClickIntoGrid(InventoryClickEvent event, Player player, Inventory topInventory) {
        ItemStack sourceItem = event.getCurrentItem();
        if (sourceItem == null || sourceItem.getType() == Material.AIR) return;
        ItemStack remainingSource = sourceItem.clone();

        // Pass 1: Merge with existing similar stacks in the grid
        for (int slot = 1; slot <= 9; slot++) {
            if (remainingSource.getAmount() <= 0) break;
            ItemStack targetItem = topInventory.getItem(slot);
            if (targetItem != null && targetItem.isSimilar(remainingSource)) {
                int canAdd = targetItem.getMaxStackSize() - targetItem.getAmount();
                if (canAdd > 0) {
                    int moveAmount = Math.min(canAdd, remainingSource.getAmount());
                    targetItem.setAmount(targetItem.getAmount() + moveAmount);
                    remainingSource.setAmount(remainingSource.getAmount() - moveAmount);
                }
            }
        }
        // Pass 2: Place in empty slots in the grid
        if (remainingSource.getAmount() > 0) {
            for (int slot = 1; slot <= 9; slot++) {
                if (remainingSource.getAmount() <= 0) break;
                ItemStack targetItem = topInventory.getItem(slot);
                if (targetItem == null || targetItem.getType() == Material.AIR) {
                    topInventory.setItem(slot, remainingSource.clone()); // Place remaining part
                    remainingSource.setAmount(0); // All placed
                    break; 
                }
            }
        }
        event.setCurrentItem(remainingSource.getAmount() > 0 ? remainingSource : null);
    }

    private void handleShiftClickOutOfGrid(InventoryClickEvent event, Player player, Inventory topInventory) {
        ItemStack sourceItem = event.getCurrentItem();
        if (sourceItem == null || sourceItem.getType() == Material.AIR) return;
        ItemStack sourceClone = sourceItem.clone(); // Item to attempt to add to player inventory

        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(sourceClone);

        if (leftovers.isEmpty()) { // Successfully added all to player inventory
            topInventory.setItem(event.getSlot(), null); // Clear from grid
        } else { // Couldn't add all, update amount in grid
            int amountLeftInGrid = 0;
            for (ItemStack leftoverStack : leftovers.values()) {
                amountLeftInGrid += leftoverStack.getAmount();
            }
            sourceItem.setAmount(amountLeftInGrid); // Update original item in grid
            topInventory.setItem(event.getSlot(), sourceItem); // Set it back (might be redundant if sourceItem is a direct ref)
            player.sendMessage(ChatColor.YELLOW + "Inventory full!");
        }
    }

    private int calculateMaxCrafts(Inventory guiInventory, Player player) {
        ItemStack resultItem = guiInventory.getItem(0);
        if (resultItem == null || resultItem.getType().isAir()) return 0;
        int maxPossibleBasedOnResultStackSize = resultItem.getMaxStackSize(); // Max items per craft (usually 1 for result)
        int resultItemAmountPerCraft = resultItem.getAmount(); // How many items this recipe produces

        int minIngredientSets = 64; // Max possible stacks to craft

        CustomRecipe customRecipe = recipeManager.findMatchingRecipe(guiInventory);
        if (customRecipe != null && customRecipe.getResult().isSimilar(resultItem)) {
            // logger.info("Calculating max for custom recipe: " + customRecipe.getId());
            if (customRecipe.getType() == CustomRecipe.RecipeType.SHAPED) {
                boolean recipeStillValid = true;
                for (int slot = 1; slot <= 9; slot++) {
                    RequiredItem required = customRecipe.getRequirementForGridSlot(slot);
                    if (required != null) {
                        ItemStack itemInSlot = guiInventory.getItem(slot);
                        if (itemInSlot == null || !required.matches(itemInSlot) || itemInSlot.getAmount() < required.getAmount()) {
                            // logger.info("MaxCalc Custom: Slot " + slot + " mismatch or insufficient amount during calc.");
                            recipeStillValid = false; // This specific requirement not met, means 0 crafts
                            break;
                        }
                        // How many times can this ingredient be used for the recipe
                        int craftsFromThisSlot = itemInSlot.getAmount() / required.getAmount();
                        minIngredientSets = Math.min(minIngredientSets, craftsFromThisSlot);
                    }
                }
                if (!recipeStillValid) return 0; // If any required item is missing or not matching
            } else { minIngredientSets = 1; } // Shapeless or other types, assume 1 for now if not fully implemented for max calculation
        } else {
            ItemStack[] matrix = getMatrix(guiInventory);
            Recipe vanillaRecipe = Bukkit.getCraftingRecipe(matrix, player.getWorld());
            if (vanillaRecipe != null && vanillaRecipe.getResult().isSimilar(resultItem)) {
                // logger.info("Calculating max for vanilla recipe");
                for (int slot = 1; slot <= 9; slot++) {
                    ItemStack itemInSlot = guiInventory.getItem(slot);
                    // For vanilla, each ingredient used contributes 1 to the recipe
                    // So the limit is the smallest stack size in the crafting grid that is part of the recipe
                    // This is a simplification; true vanilla max craft needs to check recipe shape too.
                    // For a simple approach:
                    if (itemInSlot != null && itemInSlot.getType() != Material.AIR) {
                         // This part of vanilla max calculation is tricky without knowing which slots are *actually* used by the recipe.
                         // A common approach is to find the smallest stack of any non-air item in the matrix.
                         // However, Bukkit doesn't easily expose the actual ingredients for a matched vanilla recipe.
                         // So, we'll take the minimum amount from any used slot.
                        minIngredientSets = Math.min(minIngredientSets, itemInSlot.getAmount());
                    }
                }
            } else {
                // logger.warning("calculateMaxCrafts: No recipe found matching result slot!");
                return 0;
            }
        }
        // Max crafts is limited by how many times you can form ingredient sets
        // AND how many results you can fit in one stack (though player inv takes more).
        // We are calculating how many *times* the recipe can be crafted.
        return Math.max(0, minIngredientSets);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null && clickedBlock.getType() == Material.CRAFTING_TABLE) {
            event.setCancelled(true);
            openCustomCraftingGUI(event.getPlayer());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        Player player = (Player) event.getPlayer();
        Inventory topInventory = event.getInventory();
        for (int i = 1; i <= 9; i++) { // Iterate through crafting grid slots
            ItemStack item = topInventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
                if (!leftovers.isEmpty()) {
                    leftovers.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
                }
            }
        }
        topInventory.clear(); // Clear the crafting grid and result for next use or if server handles it
    }

    public void openCustomCraftingGUI(Player player) {
        Inventory customWorkbench = Bukkit.createInventory(null, InventoryType.WORKBENCH, GUI_TITLE);
        player.openInventory(customWorkbench);
        // updateResultSlot(customWorkbench, player); // Result slot should be updated on next tick/click
    }

    private void updateResultSlot(Inventory guiInventory, Player player) {
        if (guiInventory.getType() != InventoryType.WORKBENCH || player == null || !player.isOnline()) return;

        CustomRecipe customRecipe = recipeManager.findMatchingRecipe(guiInventory);
        if (customRecipe != null) {
            guiInventory.setItem(0, customRecipe.getResult());
            return;
        }

        // If no custom recipe, check for vanilla.
        // (This part can be tricky as Bukkit.getCraftingRecipe expects a perfect matrix,
        // and custom GUIs might not always represent that for vanilla recipes if also supported)
        // For a purely custom crafter, you might skip vanilla recipe checking here.
        ItemStack[] matrix = getMatrix(guiInventory);
        Recipe vanillaRecipe = Bukkit.getCraftingRecipe(matrix, player.getWorld());
        guiInventory.setItem(0, (vanillaRecipe != null) ? vanillaRecipe.getResult() : null);
    }

    private ItemStack attemptCraft(Player player, Inventory guiInventory, CustomRecipe customRecipe, Recipe vanillaRecipe) {
        ItemStack resultItemFromSlot = guiInventory.getItem(0); // The item currently displayed in the result slot
        if (resultItemFromSlot == null || resultItemFromSlot.getType().isAir()) return null;

        if (customRecipe != null && customRecipe.getResult().isSimilar(resultItemFromSlot)) {
            // logger.info("[AttemptCraft] Trying custom recipe: " + customRecipe.getId());
            // Re-check match before consumption, as inventory might have changed due to bukkit event processing delays
            if (customRecipe.matches(guiInventory)) { 
                // logger.info("[AttemptCraft] -> Confirmed match for: " + customRecipe.getId());
                if (customRecipe.consumeIngredients(guiInventory)) { 
                    // logger.info("[AttemptCraft] -> Consumed ingredients successfully for: " + customRecipe.getId());
                    return customRecipe.getResult().clone(); // Return a clone
                } else { 
                    // logger.warning("[AttemptCraft] -> Failed to consume ingredients for: " + customRecipe.getId()); 
                    return null; 
                }
            } else { 
                // logger.warning("[AttemptCraft] -> Custom recipe " + customRecipe.getId() + " stopped matching before consumption!"); 
                return null; 
            }
        }

        if (vanillaRecipe != null && vanillaRecipe.getResult().isSimilar(resultItemFromSlot)) {
            // logger.info("[AttemptCraft] Trying vanilla recipe for: " + vanillaRecipe.getResult().getType());
            ItemStack[] matrix = getMatrix(guiInventory);
            // Bukkit doesn't provide a direct "consume ingredients for this vanilla recipe" method.
            // We have to manually decrement. This is a basic implementation.
            // It assumes each non-air item in the matrix is an ingredient and reduces its amount by 1.
            // This might not be accurate for all vanilla recipes (e.g. tools that don't get consumed, items with containers).
            boolean consumed = true;
            for (int i = 1; i <= 9; i++) {
                ItemStack ingredient = guiInventory.getItem(i);
                if (ingredient != null && !ingredient.getType().isAir()) {
                    // This is a very naive consumption for vanilla.
                    // A proper vanilla consumption would need to know the recipe's actual ingredient list.
                    // For now, just decrementing any item in the grid by 1.
                    ingredient.setAmount(ingredient.getAmount() - 1);
                    guiInventory.setItem(i, ingredient.getAmount() > 0 ? ingredient : null);
                }
            }
            if (consumed) {
                 return vanillaRecipe.getResult().clone(); // Return a clone
            } else {
                // logger.warning("[AttemptCraft] Failed to 'consume' for vanilla recipe (logic might be flawed).");
                return null;
            }
        }
        // logger.warning("[AttemptCraft] Failed - No valid recipe matched result slot after checks.");
        return null;
    }

    private ItemStack[] getMatrix(Inventory inventory) {
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            matrix[i] = inventory.getItem(i + 1); // Crafting grid slots are 1-9
        }
        return matrix;
    }
}
"@

# crafting/RecipeManager.java
$recipeManagerContent = @"
package io.github.x1f4r.mmocraft.crafting; // Updated package

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.crafting.models.CustomRecipe; // Updated import
// import io.github.x1f4r.mmocraft.crafting.models.RequiredItem; // Keep if used directly, commented if not
import io.github.x1f4r.mmocraft.items.ItemManager; // Updated import
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
// import org.bukkit.inventory.Inventory; // Keep if used directly, commented if not
// import org.bukkit.inventory.ItemStack; // Keep if used directly, commented if not

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class RecipeManager {

    private final MMOCraft plugin;
    private final List<CustomRecipe> customRecipes = new ArrayList<>();
    private final Map<String, Tag<Material>> customMaterialTags = new HashMap<>();

    public RecipeManager(MMOCraft plugin) {
        this.plugin = plugin;
        defineCustomTags();
        loadRecipes();
    }

    private void defineCustomTags() {
        // Example: Logs Tag (already present in user's code)
        Set<Material> logMaterials = EnumSet.noneOf(Material.class);
        for (Material mat : Material.values()) {
            if (mat.name().endsWith("_LOG") || mat.name().endsWith("_STEM") || mat.name().endsWith("_WOOD") || mat.name().endsWith("_HYPHAE")) {
                 if (!mat.name().contains("STRIPPED_") && !mat.name().contains("POTTED_")) { // Exclude stripped for now unless specified
                    logMaterials.add(mat);
                }
            }
        }
        // Add specific ones if the pattern isn't perfect
        logMaterials.add(Material.CRIMSON_STEM);
        logMaterials.add(Material.WARPED_STEM);
        // remove planks if they were added by _WOOD
        logMaterials.removeIf(m -> m.name().endsWith("_PLANKS"));


        customMaterialTags.put("LOGS", new Tag<Material>() {
            @Override public boolean isTagged(Material item) { return logMaterials.contains(item); }
            @Override public Set<Material> getValues() { return Collections.unmodifiableSet(logMaterials); }
            @Override public NamespacedKey getKey() { return new NamespacedKey(plugin, "logs_custom_tag"); } // Ensure unique key
            @Override public String toString() { return "CustomTag[LOGS]"; }
        });

        // Add more custom tags here if needed
        // e.g., customMaterialTags.put("CUSTOM_ORE_DUSTS", new Tag<Material>() { ... });
    }

    public void loadRecipes() {
        customRecipes.clear();
        ItemManager itemManager = plugin.getItemManager();
        if (itemManager == null) {
            plugin.getLogger().severe("ItemManager is null during RecipeManager load! Cannot load recipes.");
            return;
        }

        File recipesFile = new File(plugin.getDataFolder(), "recipes.yml");
        if (!recipesFile.exists()) {
            plugin.getLogger().info("recipes.yml not found, saving default.");
            plugin.saveResource("recipes.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(recipesFile);
        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");

        if (recipesSection == null) {
            plugin.getLogger().warning("Could not find 'recipes' section in recipes.yml!");
            return;
        }

        for (String recipeId : recipesSection.getKeys(false)) {
            ConfigurationSection recipeConfig = recipesSection.getConfigurationSection(recipeId);
            if (recipeConfig == null) continue;

            try {
                CustomRecipe recipe = CustomRecipe.loadFromConfig(plugin, itemManager, recipeId, recipeConfig, customMaterialTags);
                if (recipe != null) {
                    customRecipes.add(recipe);
                    plugin.getLogger().info("Loaded custom recipe: " + recipeId);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load custom recipe: " + recipeId, e);
            }
        }
        plugin.getLogger().info("Loaded " + customRecipes.size() + " custom recipes.");
    }

    public CustomRecipe findMatchingRecipe(org.bukkit.inventory.Inventory inventory) { // Explicitly qualify Inventory
        for (CustomRecipe recipe : customRecipes) {
            if (recipe.matches(inventory)) {
                return recipe;
            }
        }
        return null;
    }
     public List<CustomRecipe> getAllRecipes() {
        return Collections.unmodifiableList(customRecipes);
    }
}
"@

# crafting/models/CustomRecipe.java
$customRecipeModelContent = @"
package io.github.x1f4r.mmocraft.crafting.models; // Updated package

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.items.ItemManager; // Updated import
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Level; // Keep if Level is used
import java.util.logging.Logger;

public class CustomRecipe {

    private final String id;
    private final String resultItemId;
    private final int resultAmount;
    private final RecipeType type;
    private String[] shape; // Max 3x3
    private Map<Character, RequiredItem> shapedIngredients;
    // private List<RequiredItem> shapelessIngredients; // For future shapeless implementation

    private transient ItemManager itemManager; // Marked transient if GSON or similar was used, but here it's manually set
    private static final Logger logger = MMOCraft.getPlugin(MMOCraft.class).getLogger();


    private CustomRecipe(String id, String resultItemId, int resultAmount, RecipeType type, ItemManager manager) {
        this.id = id;
        this.resultItemId = resultItemId;
        this.resultAmount = resultAmount;
        this.type = type;
        this.itemManager = manager; // Set itemManager during construction
    }

    public static CustomRecipe loadFromConfig(MMOCraft plugin, ItemManager itemManager, String id, ConfigurationSection config, Map<String, Tag<Material>> customTags) {
        ConfigurationSection resultSection = config.getConfigurationSection("result");
        if (resultSection == null) {
            plugin.getLogger().warning("Recipe '" + id + "' is missing result section!");
            return null;
        }
        String resultId = resultSection.getString("item_id");
        if (resultId == null || resultId.isEmpty()) {
            plugin.getLogger().warning("Recipe '" + id + "' result section is missing 'item_id'!");
            return null;
        }
        if (itemManager.getItem(resultId) == null) { // Check if item exists
            plugin.getLogger().warning("Item ID '" + resultId + "' referenced by recipe '" + id + "' not found in ItemManager!");
            return null;
        }

        int resultAmount = resultSection.getInt("amount", 1);
        String typeStr = config.getString("type", "SHAPED").toUpperCase();
        RecipeType recipeType;
        try {
            recipeType = RecipeType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid recipe type '" + typeStr + "' for recipe '" + id + "'. Defaulting to SHAPED.");
            recipeType = RecipeType.SHAPED;
        }

        CustomRecipe recipe = new CustomRecipe(id, resultId, resultAmount, recipeType, itemManager);

        if (recipeType == RecipeType.SHAPED) {
            List<String> shapeList = config.getStringList("shape");
            // Validate shape: 1 to 3 rows, each row 1 to 3 chars
            if (shapeList.isEmpty() || shapeList.size() > 3) {
                plugin.getLogger().warning("Invalid shape for SHAPED recipe '" + id + "'. Must be 1-3 rows.");
                return null;
            }
            for (String row : shapeList) {
                if (row.length() > 3) {
                    plugin.getLogger().warning("Invalid shape row '" + row + "' in recipe '" + id + "'. Max 3 columns.");
                    return null;
                }
            }
            // Normalize shape to 3 chars per row for easier processing, padding with spaces
            recipe.shape = new String[shapeList.size()];
            for(int i = 0; i < shapeList.size(); i++) {
                recipe.shape[i] = String.format("%-3s", shapeList.get(i)); // Pad with spaces to 3 chars
            }


            ConfigurationSection ingredientsSection = config.getConfigurationSection("ingredients");
            if (ingredientsSection == null) {
                plugin.getLogger().warning("Missing ingredients section for SHAPED recipe '" + id + "'.");
                return null;
            }
            recipe.shapedIngredients = new HashMap<>();
            for (String key : ingredientsSection.getKeys(false)) {
                if (key.length() != 1) {
                    plugin.getLogger().warning("Invalid ingredient key '" + key + "' in recipe '" + id + "'. Must be a single character.");
                    continue; 
                }
                char ingredientChar = key.charAt(0);
                RequiredItem requiredItem = RequiredItem.loadFromConfig(plugin, ingredientsSection.getConfigurationSection(key), customTags);
                if (requiredItem != null) {
                    recipe.shapedIngredients.put(ingredientChar, requiredItem);
                } else {
                    plugin.getLogger().warning("Invalid ingredient definition for key '" + key + "' in recipe '" + id + "'. This ingredient will be skipped.");
                    return null; // Fail recipe load if an ingredient is bad
                }
            }
            // Validate that all chars in shape are defined in ingredients
            for (String row : recipe.shape) {
                for (char c : row.toCharArray()) {
                    if (c != ' ' && !recipe.shapedIngredients.containsKey(c)) {
                        plugin.getLogger().warning("Shape for recipe '" + id + "' contains char '" + c + "' not defined in ingredients.");
                        return null;
                    }
                }
            }
        } else if (recipeType == RecipeType.SHAPELESS) {
            // TODO: Implement shapeless recipe loading
            plugin.getLogger().warning("Shapeless recipe loading not yet fully implemented for recipe '" + id + "'.");
            // Example: recipe.shapelessIngredients = new ArrayList<>();
            // ConfigurationSection ingredientsList = config.getConfigurationSection("ingredients"); // or getList
            // load shapeless ingredients into recipe.shapelessIngredients
            return null; // Not implemented yet
        }
        return recipe;
    }

    public boolean matches(Inventory inventory) {
        // logger.finer("[RecipeCheck] Checking recipe '" + this.id + "'...");
        if (inventory.getType() != InventoryType.WORKBENCH) {
            // logger.finer("[RecipeCheck] -> Fail: Inventory is not WORKBENCH type for " + this.id);
            return false;
        }

        if (type == RecipeType.SHAPED) {
            // logger.finer("[RecipeCheck] -> Type: SHAPED for " + this.id);
            for (int r = 0; r < 3; r++) { // Iterate through 3x3 grid conceptually
                for (int c = 0; c < 3; c++) {
                    int slotIndex = 1 + r * 3 + c; // Crafting grid slot 1-9
                    ItemStack itemInSlot = inventory.getItem(slotIndex);
                    
                    char shapeChar = ' '; // Expected char from shape
                    if (r < shape.length && c < shape[r].length()) {
                        shapeChar = shape[r].charAt(c);
                    }

                    RequiredItem required = (shapeChar != ' ') ? shapedIngredients.get(shapeChar) : null;

                    // logger.finer("  [RecipeCheck] Slot " + slotIndex + " (Shape '" + shapeChar + "'): Item=" + ((itemInSlot != null) ? itemInSlot.getType() : "EMPTY"));

                    if (required == null) { // Expect empty slot in shape
                        if (itemInSlot != null && itemInSlot.getType() != Material.AIR) {
                            // logger.finer("  [RecipeCheck] -> Fail: Expected empty slot " + slotIndex +", found " + itemInSlot.getType() + " for " + this.id);
                            return false; // Found item where shape expects empty
                        }
                    } else { // Expect specific item
                        if (!required.matches(itemInSlot)) { // matches() logs details
                            // logger.finer("  [RecipeCheck] -> Fail: Slot " + slotIndex + " requirement not met for " + this.id);
                            return false;
                        }
                    }
                }
            }
            // logger.finer("[RecipeCheck] -> Success: Recipe '" + this.id + "' matches grid.");
            return true;

        } else if (type == RecipeType.SHAPELESS) {
            // TODO: Implement shapeless matching logic
            // logger.finer("[RecipeCheck] -> Type: SHAPELESS (Matching not implemented yet for " + this.id + ")");
            return false;
        }
        // logger.warning("[RecipeCheck] -> Fail: Unknown recipe type for " + this.id);
        return false;
    }


    public boolean consumeIngredients(Inventory inventory) {
        // logger.finer("[Consume] Attempting to consume for recipe " + id);
        if (inventory.getType() != InventoryType.WORKBENCH) return false;

        if (type == RecipeType.SHAPED) {
            // Create a map of slot -> amount to consume to avoid issues with same item in multiple slots
            Map<Integer, Integer> consumptionMap = new HashMap<>();

            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int slotIndex = 1 + r * 3 + c;
                     char shapeChar = ' ';
                    if (r < shape.length && c < shape[r].length()) { // Check bounds for shape array
                        shapeChar = shape[r].charAt(c);
                    }

                    if (shapeChar != ' ' && shapedIngredients.containsKey(shapeChar)) {
                        RequiredItem required = shapedIngredients.get(shapeChar);
                        ItemStack itemInSlot = inventory.getItem(slotIndex);

                        // Pre-check again before consumption (should be redundant if matches() was just called and true)
                        if (itemInSlot == null || !required.matches(itemInSlot) || itemInSlot.getAmount() < required.getAmount()) {
                            logger.severe("[Consume] Ingredient validation failed unexpectedly for slot " + slotIndex + " recipe " + id + ". Item: " + itemInSlot);
                            return false; // Should not happen if matches() was true
                        }
                        consumptionMap.put(slotIndex, consumptionMap.getOrDefault(slotIndex, 0) + required.getAmount());
                    }
                }
            }
            
            // Now perform actual consumption
            for (Map.Entry<Integer, Integer> entry : consumptionMap.entrySet()) {
                int slotIndex = entry.getKey();
                int amountToConsume = entry.getValue();
                ItemStack itemInSlot = inventory.getItem(slotIndex);

                if (itemInSlot != null && itemInSlot.getAmount() >= amountToConsume) {
                    itemInSlot.setAmount(itemInSlot.getAmount() - amountToConsume);
                    inventory.setItem(slotIndex, itemInSlot.getAmount() > 0 ? itemInSlot : null); // Set to null if depleted
                } else {
                     // This case should ideally be caught by the pre-check or matches()
                    logger.severe("[Consume] Item in slot " + slotIndex + " had insufficient amount ("+ (itemInSlot != null ? itemInSlot.getAmount() : "null") +") or became null during consumption for recipe " + id + ". Required: " + amountToConsume);
                    // Consider rolling back previous consumptions if this happens, though complex.
                    return false; 
                }
            }
            // logger.finer("[Consume] Consumed ingredients successfully for SHAPED recipe " + id);
            return true;

        } else if (type == RecipeType.SHAPELESS) {
            // TODO: Implement shapeless consumption logic
            // logger.finer("[Consume] Shapeless consumption not implemented for " + id);
            return false;
        }
        return false;
    }


    // Getters
    public String getId() { return id; }
    public RecipeType getType() { return type; }

    public ItemStack getResult() {
        if (itemManager == null) { // Should have been set by constructor or loadFromConfig
            logger.severe("ItemManager is null in getResult for recipe '" + this.id + "'. Cannot create result item.");
            return new ItemStack(Material.BARRIER); // Return an error item
        }
        ItemStack resultTemplate = itemManager.getItem(this.resultItemId);
        if (resultTemplate == null) {
            logger.severe("Item ID '" + this.resultItemId + "' (result for recipe '" + this.id + "') not found in ItemManager!");
            return new ItemStack(Material.BARRIER); // Return an error item
        }
        ItemStack finalResult = resultTemplate.clone(); // Clone to avoid modifying template
        finalResult.setAmount(this.resultAmount);
        return finalResult;
    }
    
    public RequiredItem getRequirementForGridSlot(int slotIndex) {
        if (type != RecipeType.SHAPED || slotIndex < 1 || slotIndex > 9 || shapedIngredients == null || shape == null) return null;
        // Convert 1-9 slotIndex to 0-2 row/col
        int row = (slotIndex - 1) / 3;
        int col = (slotIndex - 1) % 3;

        if (row >= shape.length || col >= shape[row].length()) return null; // Outside the defined shape pattern
        
        char ingredientChar = shape[row].charAt(col);
        if (ingredientChar == ' ') return null; // Space means no item required here

        return shapedIngredients.get(ingredientChar);
    }


    public enum RecipeType { SHAPED, SHAPELESS }
}
"@

# crafting/models/RequiredItem.java
$requiredItemModelContent = @"
package io.github.x1f4r.mmocraft.crafting.models; // Updated package

import io.github.x1f4r.mmocraft.MMOCraft;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
// import java.util.Map; // Keep if used, commented if not
import java.util.logging.Logger; // Keep if used

public class RequiredItem {

    private final IngredientType type;
    private final Material material; // Used if type is MATERIAL
    private final Tag<Material> tag; // Used if type is TAG
    private final int amount;
    // private final String customItemId; // For future use: if an ingredient must be a specific custom item

    private static final Logger logger = MMOCraft.getPlugin(MMOCraft.class).getLogger();

    private RequiredItem(IngredientType type, Material material, Tag<Material> tag, int amount) {
        this.type = type;
        this.material = material;
        this.tag = tag;
        this.amount = amount;
    }

    public static RequiredItem loadFromConfig(MMOCraft plugin, ConfigurationSection config, Map<String, Tag<Material>> customMaterialTags) {
        if (config == null) {
            logger.warning("RequiredItem config section is null.");
            return null;
        }

        String typeStr = config.getString("type", "MATERIAL").toUpperCase();
        String valueStr = config.getString("value");
        int amount = config.getInt("amount", 1);

        if (valueStr == null || valueStr.isEmpty()) {
            logger.warning("Missing 'value' in required item config: " + config.getCurrentPath());
            return null;
        }
        if (amount < 1) {
            logger.warning("Invalid 'amount' (" + amount + ") in required item config, must be at least 1: " + config.getCurrentPath());
            return null;
        }

        IngredientType ingredientType;
        Material material = null;
        Tag<Material> tag = null;

        try {
            ingredientType = IngredientType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid ingredient type '" + typeStr + "' in config: " + config.getCurrentPath() + ". Defaulting to MATERIAL.");
            ingredientType = IngredientType.MATERIAL;
        }

        switch (ingredientType) {
            case MATERIAL:
                material = Material.matchMaterial(valueStr.toUpperCase());
                if (material == null) {
                    logger.warning("Invalid material '" + valueStr + "' for required item in config: " + config.getCurrentPath());
                    return null;
                }
                break;
            case TAG:
                tag = customMaterialTags.get(valueStr.toUpperCase()); // Use the passed custom tags
                if (tag == null) {
                    // Check Bukkit tags as a fallback if you want, but usually custom tags are specific
                    // tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft(valueStr.toLowerCase()), Material.class);
                    // if (tag == null) {
                        logger.warning("Unknown material tag '" + valueStr + "' for required item in config: " + config.getCurrentPath() + ". Ensure it's defined in RecipeManager or is a valid Bukkit tag if supported.");
                        return null;
                    // }
                }
                break;
            // case CUSTOM_ITEM: // Future
            //     customItemId = valueStr;
            //     break;
            default:
                logger.warning("Unsupported ingredient type '" + ingredientType + "' after parsing for config: " + config.getCurrentPath());
                return null;
        }
        return new RequiredItem(ingredientType, material, tag, amount);
    }

    public boolean matches(ItemStack item) {
        // String requiredInfo = (this.type == IngredientType.MATERIAL) ? (this.material != null ? this.material.name() : "NULL_MATERIAL") : (this.tag != null ? "Tag:" + this.tag.getKey().getKey() : "NULL_TAG");
        // logger.finer("    [ReqCheck] Item: " + (item != null ? item.getType() : "NULL_ITEM") + " vs Req: " + requiredInfo + " Amount: " + this.amount);

        if (item == null || item.getType() == Material.AIR) {
            // logger.finer("    [ReqCheck] -> Fail: Item slot is empty or AIR.");
            return false; // Requires something, but slot is empty
        }

        boolean typeMatch = false;
        switch (this.type) {
            case MATERIAL:
                typeMatch = (item.getType() == this.material);
                break;
            case TAG:
                typeMatch = (this.tag != null && this.tag.isTagged(item.getType()));
                break;
            // case CUSTOM_ITEM: // Future
            //     if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING)) {
            //         String itemIdVal = item.getItemMeta().getPersistentDataContainer().get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);
            //         typeMatch = this.customItemId.equalsIgnoreCase(itemIdVal);
            //     }
            //     break;
            default:
                // logger.finer("    [ReqCheck] -> Fail: Unknown requirement type during match.");
                return false;
        }

        if (!typeMatch) {
            // logger.finer("    [ReqCheck] -> Fail: Type mismatch (Required: " + requiredInfo + ", Found: " + item.getType() + ")");
            return false;
        }

        if (item.getAmount() < this.amount) {
            // logger.finer("    [ReqCheck] -> Fail: Amount mismatch for " + item.getType() + " (Required: " + this.amount + ", Found: " + item.getAmount() + ")");
            return false;
        }

        // TODO: Add NBT/meta check later if ingredients need specific custom item properties beyond type/tag
        // logger.finer("    [ReqCheck] -> Success: Item " + item.getType() + " matches requirement.");
        return true;
    }

    // Getters
    public int getAmount() { return amount; }
    public IngredientType getType() { return type; }
    public Material getMaterial() { return material; } // Can be null if type is TAG
    public Tag<Material> getTag() { return tag; }       // Can be null if type is MATERIAL

    public enum IngredientType { MATERIAL, TAG /*, CUSTOM_ITEM */ } // CUSTOM_ITEM for future
}
"@

# items/ItemManager.java
$itemManagerContent = @"
package io.github.x1f4r.mmocraft.items; // Updated package

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.utils.NBTKeys; // Updated import
import org.bukkit.ChatColor;
import org.bukkit.Material;
// import org.bukkit.NamespacedKey; // No longer needed directly here if using NBTKeys
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class ItemManager {

    private final MMOCraft plugin;
    private final Map<String, ItemStack> customItems = new HashMap<>();

    public ItemManager(MMOCraft plugin) {
        this.plugin = plugin;
        loadItems();
    }

    public void loadItems() {
        customItems.clear();
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            plugin.getLogger().info("items.yml not found, saving default.");
            plugin.saveResource("items.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(itemsFile);
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) {
            plugin.getLogger().warning("Could not find 'items' section in items.yml!");
            return;
        }

        for (String itemId : itemsSection.getKeys(false)) {
            ConfigurationSection itemConfig = itemsSection.getConfigurationSection(itemId);
            if (itemConfig != null) {
                ItemStack parsedItem = parseItem(itemId, itemConfig);
                if (parsedItem != null) {
                    customItems.put(itemId.toLowerCase(), parsedItem);
                    plugin.getLogger().info("Loaded custom item: " + itemId);
                }
            }
        }
        plugin.getLogger().info("Finished loading " + customItems.size() + " custom items.");
    }

    public ItemStack getItem(String itemId) {
        ItemStack template = customItems.get(itemId.toLowerCase());
        return (template != null) ? template.clone() : null; // Always return a clone
    }

    private ItemStack parseItem(String itemId, ConfigurationSection itemConfig) {
        String matName = itemConfig.getString("material");
        if (matName == null) {
            plugin.getLogger().warning("Missing 'material' for item '" + itemId + "' in items.yml.");
            return null;
        }
        Material material = Material.matchMaterial(matName.toUpperCase());
        if (material == null) {
            plugin.getLogger().warning("Invalid material name '" + matName + "' for item '" + itemId + "'.");
            return null;
        }

        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) { // Should be rare, but good to check
            plugin.getLogger().warning("Could not get ItemMeta for material '" + matName + "' (item " + itemId + "). Item will be basic.");
            // Still try to tag it if possible
            try {
                 meta = item.getItemMeta(); // Try again, unlikely to help but for safety
                 if (meta != null && NBTKeys.ITEM_ID_KEY != null) {
                    meta.getPersistentDataContainer().set(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING, itemId.toLowerCase());
                    item.setItemMeta(meta);
                 }
            } catch (Exception e) { /* ignore */ }
            return item; // Return the basic item
        }

        // Name
        String name = itemConfig.getString("name");
        if (name != null) { meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name)); }

        // Lore
        List<String> lore = itemConfig.getStringList("lore");
        if (!lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>();
            lore.forEach(line -> coloredLore.add(ChatColor.translateAlternateColorCodes('&', line)));
            meta.setLore(coloredLore);
        }

        // Unbreakable
        boolean unbreakable = itemConfig.getBoolean("unbreakable", false);
        meta.setUnbreakable(unbreakable);

        // Enchantments
        ConfigurationSection enchantsSection = itemConfig.getConfigurationSection("enchants");
        if (enchantsSection != null) {
            for (String enchantKey : enchantsSection.getKeys(false)) {
                Enchantment enchantment = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(enchantKey.toLowerCase())); // Prefer NamespacedKey
                if (enchantment == null) enchantment = Enchantment.getByName(enchantKey.toUpperCase()); // Fallback for older names
                if (enchantment != null) {
                    int level = enchantsSection.getInt(enchantKey, 1);
                    meta.addEnchant(enchantment, level, true); // Ignore level restrictions
                } else { plugin.getLogger().warning("Invalid enchantment identifier '" + enchantKey + "' for item '" + itemId + "'."); }
            }
        }

        // Attributes (Vanilla Minecraft Attributes)
        ConfigurationSection attributesSection = itemConfig.getConfigurationSection("attributes");
        if (attributesSection != null) {
            for (String attributeKey : attributesSection.getKeys(false)) {
                try {
                    Attribute attribute = Attribute.valueOf(attributeKey.toUpperCase());
                    String valueStr = attributesSection.getString(attributeKey);
                    if (valueStr != null) {
                        String[] parts = valueStr.split(":"); // amount:operation[:slot]
                        if (parts.length < 2) throw new IllegalArgumentException("Attribute format must be amount:operation[:slot]");
                        
                        double attrAmount = Double.parseDouble(parts[0]);
                        AttributeModifier.Operation operation = AttributeModifier.Operation.valueOf(parts[1].toUpperCase());
                        EquipmentSlot slot = null;
                        if (parts.length > 2) {
                            try { slot = EquipmentSlot.valueOf(parts[2].toUpperCase()); }
                            catch (IllegalArgumentException e) { plugin.getLogger().warning("Invalid equipment slot '" + parts[2] + "' for attribute '" + attributeKey + "' on item '" + itemId + "'. Modifier will apply to all slots if applicable or main hand.");}
                        }
                        
                        // Unique name for modifier; crucial for stacking/removal
                        String modifierName = "mmocraft." + itemId + "." + attributeKey.toLowerCase(); 
                        AttributeModifier modifier = new AttributeModifier(UUID.randomUUID(), modifierName, attrAmount, operation, slot);
                        meta.addAttributeModifier(attribute, modifier);
                    }
                } catch (Exception e) { 
                    plugin.getLogger().log(Level.WARNING, "Failed to parse attribute '" + attributeKey + "' for item '" + itemId + "': " + e.getMessage()); 
                }
            }
        }

        // Item Flags
        List<String> flags = itemConfig.getStringList("item_flags");
        if (!flags.isEmpty()) {
            for (String flagName : flags) {
                try { meta.addItemFlags(ItemFlag.valueOf(flagName.toUpperCase())); }
                catch (IllegalArgumentException e) { plugin.getLogger().warning("Invalid ItemFlag '" + flagName + "' for item '" + itemId + "'."); }
            }
        }

        // --- Custom Stats (Stored in PersistentDataContainer) ---
        ConfigurationSection customStatsSection = itemConfig.getConfigurationSection("custom_stats");
        if (customStatsSection != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (customStatsSection.contains("STRENGTH")) {
                pdc.set(NBTKeys.STRENGTH_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("STRENGTH"));
            }
            if (customStatsSection.contains("CRIT_CHANCE")) {
                pdc.set(NBTKeys.CRIT_CHANCE_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("CRIT_CHANCE"));
            }
            if (customStatsSection.contains("CRIT_DAMAGE")) {
                pdc.set(NBTKeys.CRIT_DAMAGE_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("CRIT_DAMAGE"));
            }
            // MANA_KEY can be used for mana_cost on weapons or max_mana on armor
            if (customStatsSection.contains("MANA")) { // Generic 'MANA' key in YML
                pdc.set(NBTKeys.MANA_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("MANA"));
            } else if (customStatsSection.contains("MANA_COST")) { // More specific for abilities
                 pdc.set(NBTKeys.MANA_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("MANA_COST"));
            } else if (customStatsSection.contains("MAX_MANA")) { // More specific for armor
                 pdc.set(NBTKeys.MANA_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("MAX_MANA"));
            }

            if (customStatsSection.contains("SPEED")) {
                pdc.set(NBTKeys.SPEED_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("SPEED"));
            }
        }

        // Set Persistent Data Tag for ITEM_ID (Crucial for identifying custom items)
        // This should ideally be one of the last things set to meta, after all other properties.
        if (NBTKeys.ITEM_ID_KEY != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer(); // Get it again to be safe
            pdc.set(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING, itemId.toLowerCase());
        } else {
            plugin.getLogger().severe("ITEM_ID_KEY is null! Cannot tag item: " + itemId);
        }

        item.setItemMeta(meta);
        return item;
    }
}
"@

# items/PlayerAbilityListener.java
$playerAbilityListenerContent = @"
package io.github.x1f4r.mmocraft.items; // Updated package

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.player.PlayerStats; // Updated import
import io.github.x1f4r.mmocraft.utils.NBTKeys;   // Updated import
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
// import org.bukkit.util.Vector; // Keep if used by ability

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerAbilityListener implements Listener {

    private final MMOCraft plugin;
    private final Map<UUID, Long> aotdCooldown = new HashMap<>();
    private final long AOTD_COOLDOWN_MS = 5000; // 5 seconds

    public PlayerAbilityListener(MMOCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand == null || itemInHand.getType() == Material.AIR || !itemInHand.hasItemMeta()) {
            return;
        }
        ItemMeta meta = itemInHand.getItemMeta();
        if (meta == null) return; // Should be caught by hasItemMeta, but defensive

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING)) {
            return;
        }
        String itemId = pdc.get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);

        // Check for right-click actions
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if ("aspect_of_the_dragons".equalsIgnoreCase(itemId)) {
                event.setCancelled(true); 
                handleAspectOfTheDragonsAbility(player, itemInHand, pdc);
            }
            // Add other item ability checks here:
            // else if ("some_other_item_id".equalsIgnoreCase(itemId)) {
            //    handleSomeOtherAbility(player, itemInHand, pdc);
            // }
        }
    }

    private void handleAspectOfTheDragonsAbility(Player player, ItemStack item, PersistentDataContainer pdc) {
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (aotdCooldown.containsKey(playerUUID)) {
            if (currentTime - aotdCooldown.get(playerUUID) < AOTD_COOLDOWN_MS) {
                long timeLeft = (AOTD_COOLDOWN_MS - (currentTime - aotdCooldown.get(playerUUID))) / 1000;
                player.sendMessage(ChatColor.RED + "Dragon's Fury is on cooldown for " + (timeLeft + 1) + "s!");
                return;
            }
        }

        // Mana Cost Check
        PlayerStats stats = plugin.getPlayerStatsManager().getStats(player);
        int manaCost = pdc.getOrDefault(NBTKeys.MANA_KEY, PersistentDataType.INTEGER, 0); // MANA_KEY stores ability cost for AOTD

        if (!stats.consumeMana(manaCost)) {
            player.sendMessage(ChatColor.RED + "Not enough mana! (" + stats.getCurrentMana() + "/" + manaCost + ")");
            return;
        }
        // player.sendMessage(ChatColor.AQUA + "Mana: " + stats.getCurrentMana() + "/" + stats.getMaxMana()); // Feedback

        // --- Implement Dragon's Fury Ability ---
        player.sendMessage(ChatColor.GOLD + "You unleash " + ChatColor.BOLD + "Dragon's Fury!");
        
        // Example: AOE damage (simple version)
        player.getWorld().getNearbyEntities(player.getLocation().add(player.getLocation().getDirection().multiply(2)), 5, 5, 5, 
            entity -> entity instanceof LivingEntity && entity != player && !(entity instanceof org.bukkit.entity.ArmorStand))
            .forEach(entity -> {
                ((LivingEntity) entity).damage(20.0 + stats.getStrength() * 0.5, player); // Damage + 50% of strength
                 entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_LARGE, entity.getLocation().add(0,1,0), 1);
        });

        // Example: Launch a projectile (visual effect)
        // Fireball fireball = player.launchProjectile(Fireball.class);
        // fireball.setYield(0); // No block damage
        // fireball.setIsIncendiary(false);
        // fireball.setShooter(player);
        // fireball.setDirection(player.getLocation().getDirection().multiply(1.5));
        // fireball.setCustomName("Dragon Breath");


        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        player.getWorld().spawnParticle(org.bukkit.Particle.FLAME, player.getEyeLocation().add(player.getLocation().getDirection()), 30, 0.5, 0.5, 0.5, 0.1);
        // --- End of Ability Implementation ---

        aotdCooldown.put(playerUUID, currentTime); 
    }
}
"@

# items/ArmorSetListener.java
$armorSetListenerContent = @"
package io.github.x1f4r.mmocraft.items; // Updated package

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.utils.NBTKeys; // Updated import
import org.bukkit.Material;
// import org.bukkit.NamespacedKey; // No longer needed directly
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class ArmorSetListener implements Listener {

    private final MMOCraft plugin;
    private final Map<UUID, Long> sneakCooldown = new HashMap<>();
    private final long COOLDOWN_MILLIS = 5000; // 5 seconds
    private static final Logger logger = MMOCraft.getPlugin(MMOCraft.class).getLogger();

    // Define your custom item IDs as constants
    private static final String TREE_HELMET_ID = "tree_helmet";
    private static final String TREE_CHESTPLATE_ID = "tree_chestplate";
    private static final String TREE_LEGGINGS_ID = "tree_leggings";
    private static final String TREE_BOOTS_ID = "tree_boots";

    public ArmorSetListener(MMOCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return; // Only trigger when starting to sneak

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Cooldown check
        long currentTime = System.currentTimeMillis();
        long lastSneakTime = sneakCooldown.getOrDefault(playerUUID, 0L);
        if (currentTime - lastSneakTime < COOLDOWN_MILLIS) {
            // player.sendMessage(ChatColor.RED + "Tree set ability on cooldown!"); // Optional feedback
            return;
        }

        if (!isWearingFullTreeSet(player)) {
            return;
        }

        logger.info(player.getName() + " triggered Tree Armor sneak ability!");
        sneakCooldown.put(playerUUID, currentTime); // Update cooldown time

        // Ability: Drop an apple
        ItemStack apple = new ItemStack(Material.APPLE, 1);
        player.getWorld().dropItemNaturally(player.getLocation(), apple);
        player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 0.5f, 1.0f); // Example sound
        // player.sendMessage(ChatColor.GREEN + "You feel the forest's generosity!"); // Example message
    }

    private boolean isWearingFullTreeSet(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack helmet = inventory.getHelmet();
        ItemStack chestplate = inventory.getChestplate();
        ItemStack leggings = inventory.getLeggings();
        ItemStack boots = inventory.getBoots();

        if (!isTreeArmorPiece(helmet, TREE_HELMET_ID)) return false;
        if (!isTreeArmorPiece(chestplate, TREE_CHESTPLATE_ID)) return false;
        if (!isTreeArmorPiece(leggings, TREE_LEGGINGS_ID)) return false;
        if (!isTreeArmorPiece(boots, TREE_BOOTS_ID)) return false;

        return true;
    }

    private boolean isTreeArmorPiece(ItemStack item, String expectedId) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (NBTKeys.ITEM_ID_KEY != null && pdc.has(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING)) {
            String itemId = pdc.get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);
            return expectedId.equalsIgnoreCase(itemId);
        }
        return false;
    }
}
"@

# mobs/ElderDragonAI.java
$elderDragonAIContent = @"
package io.github.x1f4r.mmocraft.mobs; // Updated package

import io.github.x1f4r.mmocraft.MMOCraft;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class ElderDragonAI extends BukkitRunnable {

    private final MMOCraft plugin;
    private final UUID dragonUniqueId;
    private EnderDragon dragon; // Instance of the dragon
    private final Random random = new Random();
    private int ticksLived = 0;

    // Ability cooldowns (in ticks)
    private int fireballVolleyCooldown = 0;
    private final int FIREBALL_VOLLEY_COOLDOWN_MAX = 20 * 10; // 10 seconds

    private int lightningStrikeCooldown = 0;
    private final int LIGHTNING_STRIKE_COOLDOWN_MAX = 20 * 18; // 18 seconds

    private int chargePlayerCooldown = 0;
    private final int CHARGE_PLAYER_COOLDOWN_MAX = 20 * 25; // 25 seconds


    public ElderDragonAI(MMOCraft plugin, EnderDragon dragon) {
        this.plugin = plugin;
        this.dragon = dragon;
        this.dragonUniqueId = dragon.getUniqueId();
    }

    @Override
    public void run() {
        // Re-fetch the dragon instance if it's null or invalid
        if (dragon == null || !dragon.isValid() || dragon.isDead()) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(dragonUniqueId);
            if (entity instanceof EnderDragon && entity.isValid() && !entity.isDead()) {
                dragon = (EnderDragon) entity;
            } else {
                this.cancel(); // Dragon is gone or dead, stop AI
                plugin.getLogger().info("Elder Dragon AI for " + dragonUniqueId + " stopped (dragon gone or dead).");
                return;
            }
        }

        ticksLived++;

        // --- Custom Targeting ---
        // Vanilla Ender Dragon has complex phases. We're adding abilities on top.
        // If you want to override phases, it's much more complex.
        // Example: dragon.setPhase(EnderDragon.Phase.CHARGE_PLAYER);

        List<Player> nearbyPlayers = dragon.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distanceSquared(dragon.getLocation()) < 100 * 100 && // 100 blocks range
                              p.getGameMode() == org.bukkit.GameMode.SURVIVAL &&
                              !p.isDead())
                .collect(Collectors.toList());

        if (nearbyPlayers.isEmpty() && dragon.getPhase() != EnderDragon.Phase.DYING) {
             // If no players nearby, maybe make it circle its home or a predefined point.
             // Or reset/despawn after a timeout if that's desired.
            if (dragon.getPhase() == EnderDragon.Phase.CIRCLING && dragon.getTarget() == null) {
                // dragon.setPhase(EnderDragon.Phase.LEAVE_PORTAL); // Or some other less aggressive phase
            }
            return; 
        }


        // Decrement Cooldowns
        if (fireballVolleyCooldown > 0) fireballVolleyCooldown--;
        if (lightningStrikeCooldown > 0) lightningStrikeCooldown--;
        if (chargePlayerCooldown > 0) chargePlayerCooldown--;


        // --- Custom Abilities ---
        // Only execute abilities if players are nearby and dragon is in a combat phase
        if (!nearbyPlayers.isEmpty() && 
            (dragon.getPhase() == EnderDragon.Phase.CIRCLING || 
             dragon.getPhase() == EnderDragon.Phase.CHARGE_PLAYER ||
             dragon.getPhase() == EnderDragon.Phase.STRAFING_PLAYER || // Spigot specific? Check API
             dragon.getPhase() == EnderDragon.Phase.FLY_TO_PORTAL_APPROACH)) { // Example phases

            // Ability 1: Fireball Volley
            if (fireballVolleyCooldown <= 0 && random.nextInt(100) < 25) { // 25% chance
                performFireballVolley(nearbyPlayers);
                fireballVolleyCooldown = FIREBALL_VOLLEY_COOLDOWN_MAX + random.nextInt(20 * 3); // Add some variance
            }

            // Ability 2: Lightning Strike
            if (lightningStrikeCooldown <= 0 && random.nextInt(100) < 15) { // 15% chance
                performLightningStrike(nearbyPlayers);
                lightningStrikeCooldown = LIGHTNING_STRIKE_COOLDOWN_MAX + random.nextInt(20 * 5);
            }
            
            // Ability 3: Charge Player (more aggressive targeting)
            // Note: Vanilla EnderDragon already has a charge phase. This could augment it.
            if (chargePlayerCooldown <= 0 && random.nextInt(100) < 10 && dragon.getPhase() != EnderDragon.Phase.CHARGE_PLAYER) { // 10% chance
                Player target = nearbyPlayers.get(random.nextInt(nearbyPlayers.size()));
                // dragon.setPhase(EnderDragon.Phase.CHARGE_PLAYER); // This might trigger vanilla charge
                // dragon.setTarget(target); // This might not directly work as expected for EnderDragon pathfinding
                Bukkit.broadcastMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + dragon.getCustomName() + " focuses its rage on " + target.getName() + "!");
                dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 2.0f, 0.7f);
                // For a custom "charge", you might apply a strong velocity towards the player
                // This is very basic and might look jerky:
                // Vector direction = target.getLocation().toVector().subtract(dragon.getLocation().toVector()).normalize();
                // dragon.setVelocity(direction.multiply(1.5)); // Adjust speed
                chargePlayerCooldown = CHARGE_PLAYER_COOLDOWN_MAX + random.nextInt(20 * 7);
            }
        }
    }

    private void performFireballVolley(List<Player> targets) {
        if (targets.isEmpty() || dragon == null || !dragon.isValid()) return;
        Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + dragon.getCustomName() + " unleashes a Fireball Volley!");
        dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_GHAST_SHOOT, 2.0f, 0.5f);

        for (int i = 0; i < 3 + random.nextInt(3); i++) { // 3-5 fireballs
            Player target = targets.get(random.nextInt(targets.size()));
            Location dragonEyeLoc = dragon.getEyeLocation();
            Vector direction = target.getEyeLocation().toVector().subtract(dragonEyeLoc.toVector()).normalize();

            Fireball fireball = dragon.launchProjectile(Fireball.class, direction.multiply(1.2)); // Adjust speed
            fireball.setShooter(dragon);
            fireball.setYield(1.5F + random.nextFloat()); // Smaller explosions than ghast
            fireball.setIsIncendiary(false); // Optional: set true for fire
            
             // Schedule a task to make fireball more visible or add trail
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (!fireball.isValid() || fireball.isDead() || ticks++ > 100) { // Max 5 seconds
                        this.cancel();
                        return;
                    }
                    fireball.getWorld().spawnParticle(Particle.FLAME, fireball.getLocation(), 3, 0.1, 0.1, 0.1, 0.01);
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }
    }

    private void performLightningStrike(List<Player> targets) {
        if (targets.isEmpty() || dragon == null || !dragon.isValid()) return;
        Player target = targets.get(random.nextInt(targets.size()));

        Bukkit.broadcastMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + dragon.getCustomName() + " calls down lightning upon " + target.getName() + "!");
        dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.5f, 1.0f);

        Location targetLoc = target.getLocation();
        // Strike around the player for AOE feel
        for (int i = 0; i < 1 + random.nextInt(3); i++) { // 1-3 bolts
            Location strikeLoc = targetLoc.clone().add(random.nextInt(10) - 5, 0, random.nextInt(10) - 5); // Strike within 5 block radius
            // Ensure strike location is safe (e.g., highest block) to avoid striking underground if player is in cave
            strikeLoc = strikeLoc.getWorld().getHighestBlockAt(strikeLoc).getLocation().add(0,1,0); 
            
            dragon.getWorld().strikeLightning(strikeLoc); // This does damage
            // dragon.getWorld().strikeLightningEffect(strikeLoc); // Effect only, no damage
            dragon.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, strikeLoc, 2, 0.5, 0.5, 0.5, 0);
        }
    }

    public void start() {
        // Run task more frequently for smoother AI checks, but less frequent for heavy abilities
        this.runTaskTimer(plugin, 20L, 20L); // Run every second (20 ticks)
        plugin.getLogger().info("Elder Dragon AI for " + dragonUniqueId + " started.");
    }
}
"@

# mobs/MobDropListener.java
$mobDropListenerContent = @"
package io.github.x1f4r.mmocraft.mobs; // Updated package

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.items.ItemManager; // Updated import
import io.github.x1f4r.mmocraft.utils.NBTKeys;   // Updated import
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

// import java.util.Random; // Keep if used for chance-based drops later
import java.util.logging.Logger;

public class MobDropListener implements Listener {

    private final MMOCraft plugin;
    private final ItemManager itemManager;
    // private final Random random = new Random(); // Keep for future use
    private static final Logger logger = MMOCraft.getPlugin(MMOCraft.class).getLogger();


    public MobDropListener(MMOCraft plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager(); // Get ItemManager from plugin
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return; // Only interested in Ender Dragons for now
        }
        EnderDragon dragon = (EnderDragon) event.getEntity();

        String mobType = null;
        if (NBTKeys.MOB_TYPE_KEY != null) { // Use NBTKeys
            mobType = dragon.getPersistentDataContainer().get(NBTKeys.MOB_TYPE_KEY, PersistentDataType.STRING);
        } else {
            logger.severe("MOB_TYPE_KEY from NBTKeys was null! Cannot check mob type for drops.");
            return; 
        }


        if (mobType != null && mobType.equals("elder_dragon")) {
            logger.info("Custom Elder Dragon killed! Processing custom drops...");

            event.getDrops().clear(); // Clear default Ender Dragon drops
            event.setDroppedExp(500); // Example: Set custom XP drop

            // --- Drop Aspect of the Dragons - 100% Chance ---
            ItemStack aotd = itemManager.getItem("aspect_of_the_dragons"); 
            if (aotd != null) {
                event.getDrops().add(aotd);
                logger.info("Dropped Aspect of the Dragons for Elder Dragon kill.");
            } else {
                logger.warning("Attempted to drop AOTD, but item 'aspect_of_the_dragons' is not loaded/defined in ItemManager!");
            }
            // -----------------------------

            // Add other custom drops for the Elder Dragon here...
            // Example: ItemStack dragonScale = itemManager.getItem("dragon_scale");
            // if (dragonScale != null) {
            //    int amount = random.nextInt(3) + 1; // 1-3 scales
            //    dragonScale.setAmount(amount);
            //    event.getDrops().add(dragonScale);
            // }

        } else {
            // logger.info("Vanilla Ender Dragon killed. No custom drops added by MMOCraft specific logic.");
            // Vanilla drops will proceed as normal if not cleared.
        }
    }
}
"@

# utils/NBTKeys.java
$nbtKeysContent = @"
package io.github.x1f4r.mmocraft.utils;

import io.github.x1f4r.mmocraft.MMOCraft;
import org.bukkit.NamespacedKey;

public final class NBTKeys {

    private static MMOCraft plugin;

    // Item Identification
    public static NamespacedKey ITEM_ID_KEY;

    // Mob Identification
    public static NamespacedKey MOB_TYPE_KEY;

    // Custom Stats
    public static NamespacedKey STRENGTH_KEY;
    public static NamespacedKey CRIT_CHANCE_KEY;
    public static NamespacedKey CRIT_DAMAGE_KEY;
    public static NamespacedKey MANA_KEY;       // Can represent max_mana on armor or mana_cost on items
    public static NamespacedKey SPEED_KEY;

    public static void init(MMOCraft pluginInstance) {
        if (NBTKeys.plugin != null) { // Ensure it's initialized only once
            return;
        }
        NBTKeys.plugin = pluginInstance;

        // It's good practice to prefix plugin-specific keys
        ITEM_ID_KEY = new NamespacedKey(plugin, "mmo_item_id");
        MOB_TYPE_KEY = new NamespacedKey(plugin, "mmo_mob_type");

        STRENGTH_KEY = new NamespacedKey(plugin, "mmo_strength");
        CRIT_CHANCE_KEY = new NamespacedKey(plugin, "mmo_crit_chance");
        CRIT_DAMAGE_KEY = new NamespacedKey(plugin, "mmo_crit_damage");
        MANA_KEY = new NamespacedKey(plugin, "mmo_mana_stat"); // General mana key
        SPEED_KEY = new NamespacedKey(plugin, "mmo_speed");
    }

    private NBTKeys() {
        // Prevent instantiation
        throw new IllegalStateException("Utility class. Use NBTKeys.init(plugin) to initialize.");
    }
}
"@

# player/PlayerStats.java
$playerStatsContent = @"
package io.github.x1f4r.mmocraft.player;

public class PlayerStats {

    // Combat Stats
    private int strength;
    private int critChance; // Base 0-100%
    private int critDamage; // Additional % damage on top of base crit (e.g., 50 means +50% damage)

    // Resource Stats
    private int currentMana;
    private int maxMana;

    // Utility Stats
    private int speed; // Percentage modifier

    public PlayerStats(int strength, int critChance, int critDamage, int currentMana, int maxMana, int speed) {
        this.strength = strength;
        this.critChance = Math.max(0, Math.min(100, critChance)); // Clamp
        this.critDamage = critDamage;
        this.maxMana = Math.max(1, maxMana); // Max mana should be at least 1
        this.currentMana = Math.max(0, Math.min(this.maxMana, currentMana)); // Clamp
        this.speed = speed;
    }

    // Default stats (e.g., for a new player or base values)
    public static PlayerStats base() {
        return new PlayerStats(0, 5, 50, 100, 100, 0); // Example: 5% base crit chance, 50% base crit damage, 100 mana
    }

    // Getters
    public int getStrength() { return strength; }
    public int getCritChance() { return critChance; }
    public int getCritDamage() { return critDamage; }
    public int getCurrentMana() { return currentMana; }
    public int getMaxMana() { return maxMana; }
    public int getSpeed() { return speed; }

    // Setters (or methods to modify stats)
    public void setStrength(int strength) { this.strength = strength; }
    public void setCritChance(int critChance) { this.critChance = Math.max(0, Math.min(100, critChance)); }
    public void setCritDamage(int critDamage) { this.critDamage = critDamage; }
    public void setCurrentMana(int currentMana) { this.currentMana = Math.max(0, Math.min(this.maxMana, currentMana));}
    public void setMaxMana(int maxMana) { 
        this.maxMana = Math.max(1, maxMana);
        // Ensure current mana doesn't exceed new max mana
        if (this.currentMana > this.maxMana) {
            this.currentMana = this.maxMana;
        }
    }
    public void setSpeed(int speed) { this.speed = speed; }

    public void addMana(int amount) {
        setCurrentMana(this.currentMana + amount);
    }

    public boolean consumeMana(int amount) {
        if (amount <= 0) return true; // Consuming 0 or negative mana is always successful
        if (this.currentMana >= amount) {
            setCurrentMana(this.currentMana - amount);
            return true;
        }
        return false;
    }
}
"@

# player/PlayerStatsManager.java
$playerStatsManagerContent = @"
package io.github.x1f4r.mmocraft.player;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerStatsManager {

    private final MMOCraft plugin;
    private final Map<UUID, PlayerStats> playerStatsCache = new HashMap<>();
    private final Map<UUID, AttributeModifier> speedModifiers = new HashMap<>();
    private static final String SPEED_MODIFIER_UUID_NAMESPACE = "mmocraft_speed_modifier_v2"; // Use unique name


    public PlayerStatsManager(MMOCraft plugin) {
        this.plugin = plugin;
        startManaRegenTask();
        startStatRefreshTask(); 
    }

    public PlayerStats getStats(Player player) {
        // Ensure player is valid and online before computing/returning stats
        if (player == null || !player.isOnline()) {
            // Return a default/dummy stat object or handle appropriately
            // For cache, we usually compute if absent when player is valid.
            // If accessed for an offline player, this might be an issue.
            // For now, this assumes player is online when getStats is called.
            return playerStatsCache.computeIfAbsent(player.getUniqueId(), uuid -> {
                PlayerStats newStats = PlayerStats.base();
                // updateAndApplyStats(player, newStats); // Calculate initial stats from gear
                return newStats;
            });
        }
        return playerStatsCache.computeIfAbsent(player.getUniqueId(), uuid -> PlayerStats.base());
    }
    
    // Internal method to update a given stats object
    private void updateStatsFromEquipment(Player player, PlayerStats stats) {
        PlayerInventory inventory = player.getInventory();

        // Reset to base before recalculating from gear
        // This ensures stats are not infinitely cumulative on each call without proper reset logic
        PlayerStats baseDefaults = PlayerStats.base();
        stats.setStrength(baseDefaults.getStrength());
        stats.setCritChance(baseDefaults.getCritChance());
        stats.setCritDamage(baseDefaults.getCritDamage());
        stats.setMaxMana(baseDefaults.getMaxMana()); 
        stats.setSpeed(baseDefaults.getSpeed());    

        // Iterate over armor and main hand item
        ItemStack[] armor = inventory.getArmorContents();
        for (ItemStack item : armor) {
            accumulateStatsFromItem(item, stats);
        }
        accumulateStatsFromItem(inventory.getItemInMainHand(), stats);
        // accumulateStatsFromItem(inventory.getItemInOffHand(), stats); // If offhand items can have stats

        // Ensure current mana is not over new max mana (already handled by setMaxMana and setCurrentMana)
        stats.setMaxMana(stats.getMaxMana()); // Re-call to ensure clamp if maxMana changed
        stats.setCurrentMana(stats.getCurrentMana()); // Re-call to ensure clamp
    }


    public void updateAndApplyAllEffects(Player player) {
        PlayerStats stats = getStats(player); 
        updateStatsFromEquipment(player, stats); // Recalculate stats from gear

        applySpeedModifier(player, stats.getSpeed());
        // Apply other persistent effects here if any (e.g., health boosts via attributes)
        // plugin.getLogger().log(Level.FINER, "Refreshed stats for " + player.getName() + 
        //      ": STR=" + stats.getStrength() + ", CRIT%=" + stats.getCritChance() + 
        //      ", CRITDMG=" + stats.getCritDamage() + ", MANA=" + stats.getCurrentMana() + "/" + stats.getMaxMana() +
        //      ", SPD%=" + stats.getSpeed());
    }

    private void accumulateStatsFromItem(ItemStack item, PlayerStats stats) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) { // PDC check is implicit with hasItemMeta typically
                PersistentDataContainer pdc = meta.getPersistentDataContainer();

                stats.setStrength(stats.getStrength() + pdc.getOrDefault(NBTKeys.STRENGTH_KEY, PersistentDataType.INTEGER, 0));
                stats.setCritChance(stats.getCritChance() + pdc.getOrDefault(NBTKeys.CRIT_CHANCE_KEY, PersistentDataType.INTEGER, 0));
                stats.setCritDamage(stats.getCritDamage() + pdc.getOrDefault(NBTKeys.CRIT_DAMAGE_KEY, PersistentDataType.INTEGER, 0));
                stats.setMaxMana(stats.getMaxMana() + pdc.getOrDefault(NBTKeys.MANA_KEY, PersistentDataType.INTEGER, 0)); 
                stats.setSpeed(stats.getSpeed() + pdc.getOrDefault(NBTKeys.SPEED_KEY, PersistentDataType.INTEGER, 0));
            }
        }
    }

    private void applySpeedModifier(Player player, int speedStatPercentage) {
        AttributeInstance speedAttribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttribute == null) return;

        UUID modifierUUID = UUID.nameUUIDFromBytes((SPEED_MODIFIER_UUID_NAMESPACE + player.getName()).getBytes());

        // Remove any existing modifier with the same UUID (from this plugin)
        AttributeModifier oldModifier = null;
        for(AttributeModifier modifier : speedAttribute.getModifiers()){
            if(modifier.getUniqueId().equals(modifierUUID)){
                oldModifier = modifier;
                break;
            }
        }
        if(oldModifier != null){
            speedAttribute.removeModifier(oldModifier);
        }
        speedModifiers.remove(player.getUniqueId()); // Clean up map too


        if (speedStatPercentage != 0) {
            // Convert percentage stat to modifier value.
            // For MULTIPLY_SCALAR_1, if speedStat is 10 (10%), modifier amount should be 0.1
            // This means final speed = BaseSpeed * (1 + modifierAmount)
            double modifierAmount = (double) speedStatPercentage / 100.0;

            AttributeModifier newModifier = new AttributeModifier(
                    modifierUUID,
                    "mmocraft_speed_boost", // Name of the modifier
                    modifierAmount,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1 
            );
            
            // try { // Adding modifier can sometimes fail if not handled correctly
                speedAttribute.addModifier(newModifier);
                speedModifiers.put(player.getUniqueId(), newModifier); // Track current modifier
            // } catch (IllegalArgumentException e) {
            //    plugin.getLogger().log(Level.WARNING, "Could not apply speed modifier for " + player.getName() + ": " + e.getMessage());
            // }
        }
    }


    public void handlePlayerJoin(Player player) {
        // Initialize stats object for the player
        PlayerStats stats = PlayerStats.base();
        playerStatsCache.put(player.getUniqueId(), stats);
        // Calculate and apply stats from any equipment they might have on join
        updateAndApplyAllEffects(player); 
    }

    public void handlePlayerQuit(Player player) {
        // Clean up speed modifier
        AttributeInstance speedAttribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttribute != null) {
            AttributeModifier currentModifier = speedModifiers.remove(player.getUniqueId());
            if (currentModifier != null) {
                 // Check if modifier is still present before removing
                boolean wasPresent = false;
                for(AttributeModifier mod : speedAttribute.getModifiers()){
                    if(mod.getUniqueId().equals(currentModifier.getUniqueId())){
                        wasPresent = true;
                        break;
                    }
                }
                if(wasPresent){
                    speedAttribute.removeModifier(currentModifier);
                }
            }
        }
        playerStatsCache.remove(player.getUniqueId()); // Clean up cache
    }


    private void startManaRegenTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID playerUUID : playerStatsCache.keySet()) { // Iterate over cached players
                    Player player = Bukkit.getPlayer(playerUUID);
                    if (player != null && player.isOnline()) { // Ensure player is still online
                        PlayerStats stats = getStats(player); // Get their current stats object
                        if (stats.getCurrentMana() < stats.getMaxMana()) {
                            int manaToRegen = (int) Math.max(1, stats.getMaxMana() * 0.025); // Regen 2.5% of max mana, min 1
                            stats.addMana(manaToRegen);
                            // player.sendActionBar(ChatColor.AQUA + "Mana: " + stats.getCurrentMana() + "/" + stats.getMaxMana()); 
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 20L * 1); // Initial delay 5s, then every 1 second
    }

    private void startStatRefreshTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) { // Iterate over online players directly
                    updateAndApplyAllEffects(player);
                }
            }
        // Refresh stats more frequently to catch equipment changes, item swaps etc.
        // The PlayerEquipmentListener will also call for updates on specific events.
        }.runTaskTimer(plugin, 40L, 20L * 1); // Refresh stats every 1 second
    }

    // Call this method on specific events like inventory changes, item held changes.
    public void scheduleStatsUpdate(Player player) {
        // Adding a small delay can prevent issues with inventory updates not being fully processed.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) { // Check if player is still online
                 updateAndApplyAllEffects(player);
            }
        }, 2L); // 2-tick delay
    }
}
"@

# player/listeners/PlayerDamageListener.java
$playerDamageListenerContent = @"
package io.github.x1f4r.mmocraft.player.listeners;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.player.PlayerStats;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Random;

public class PlayerDamageListener implements Listener {

    // private final MMOCraft plugin; // Not strictly needed if only using StatsManager
    private final PlayerStatsManager statsManager;
    private final Random random = new Random();

    public PlayerDamageListener(MMOCraft plugin) {
        // this.plugin = plugin;
        this.statsManager = plugin.getPlayerStatsManager();
    }

    @EventHandler(priority = EventPriority.NORMAL) // NORMAL allows other plugins to modify damage before/after
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return; 
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return; 
        }

        Player damager = (Player) event.getDamager();
        LivingEntity target = (LivingEntity) event.getEntity();
        PlayerStats damagerStats = statsManager.getStats(damager); // Get player's calculated stats

        double currentDamage = event.getDamage();

        // 1. Apply Custom Strength
        // This strength is an additive bonus on top of Minecraft's default damage calculations.
        currentDamage += damagerStats.getStrength();

        // 2. Apply Custom Critical Hit
        double critChancePercentage = damagerStats.getCritChance();
        if (critChancePercentage > 0 && random.nextDouble() * 100.0 < critChancePercentage) {
            // It's a crit!
            // Default Minecraft crit is 1.5x. Our critDamage is an *additional* percentage.
            // If base crit is 1.5x (this is implicit in vanilla if conditions met), 
            // and player has +50% crit damage stat, this could mean:
            // Option A: Total = BaseDamage * 1.5 * (1 + CustomCritDamage/100.0)
            // Option B: Total = BaseDamage * (1.5 + CustomCritDamage/100.0)
            // Option C: If vanilla crit didn't happen, our crit is BaseDamage * (1 + CustomCritDamage/100.0)
            // Let's go with a simple model: our crit damage stat directly boosts the damage by that percentage.
            // And we assume our crit is independent of vanilla's jump-crit.
            
            double critDamageMultiplier = 1.0 + (damagerStats.getCritDamage() / 100.0); // e.g., 50 stat -> 1.5x multiplier
            currentDamage *= critDamageMultiplier;

            // Visual/Audio Feedback for Crit
            damager.playSound(damager.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
            if (target.isValid()) { // Ensure target is still valid for particle effect
                target.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC, target.getEyeLocation(), 15, 0.5, 0.5, 0.5, 0.1);
            }
            // damager.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "CRITICAL HIT! " + ChatColor.GRAY + String.format("%.1f", currentDamage));
        }

        event.setDamage(Math.max(0.1, currentDamage)); // Ensure damage is at least a small amount, not negative
    }
}
"@

# player/listeners/PlayerEquipmentListener.java
$playerEquipmentListenerContent = @"
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
"@


# --- Write/Overwrite Content for All Java Files ---
Write-Host "Writing/Overwriting refactored Java file contents..."
$fileContents = @{
    "$baseSrcPath\MMOCraft.java"                                       = $mmocraftContent
    "$baseSrcPath\commands\CustomCraftCommand.java"                    = $customCraftCommandContent
    "$baseSrcPath\commands\SummonElderDragonCommand.java"              = $summonElderDragonCommandContent
    "$baseSrcPath\crafting\CraftingGUIListener.java"                   = $craftingGUIListenerContent
    "$baseSrcPath\crafting\RecipeManager.java"                         = $recipeManagerContent
    "$baseSrcPath\crafting\models\CustomRecipe.java"                   = $customRecipeModelContent
    "$baseSrcPath\crafting\models\RequiredItem.java"                   = $requiredItemModelContent
    "$baseSrcPath\items\ItemManager.java"                              = $itemManagerContent
    "$baseSrcPath\items\PlayerAbilityListener.java"                    = $playerAbilityListenerContent
    "$baseSrcPath\items\ArmorSetListener.java"                         = $armorSetListenerContent
    "$baseSrcPath\mobs\ElderDragonAI.java"                             = $elderDragonAIContent
    "$baseSrcPath\mobs\MobDropListener.java"                           = $mobDropListenerContent
    "$baseSrcPath\utils\NBTKeys.java"                                  = $nbtKeysContent
    "$baseSrcPath\player\PlayerStats.java"                             = $playerStatsContent
    "$baseSrcPath\player\PlayerStatsManager.java"                      = $playerStatsManagerContent
    "$baseSrcPath\player\listeners\PlayerDamageListener.java"          = $playerDamageListenerContent
    "$baseSrcPath\player\listeners\PlayerEquipmentListener.java"       = $playerEquipmentListenerContent
}

foreach ($entry in $fileContents.GetEnumerator()) {
    $filePath = $entry.Key
    $fileContent = $entry.Value
    
    # Ensure target directory exists before writing
    $dirPath = Split-Path $filePath
    if (-not (Test-Path $dirPath)) {
        New-Item -ItemType Directory -Path $dirPath -Force | Out-Null
    }
    
    try {
        Set-Content -Path $filePath -Value $fileContent -Encoding UTF8 -Force
        Write-Host "  Updated/Created: $filePath"
    } catch {
        Write-Error "Failed to write content to $filePath. Error: $($_.Exception.Message)"
    }
}

# --- Clean Up Old Directories ---
# Only remove if they are empty and exist
Write-Host "Cleaning up old directories (if empty)..."
$oldDirsToRemove = @(
    "$baseSrcPath\listeners",
    "$baseSrcPath\managers",
    "$baseSrcPath\models",
    "$baseSrcPath\ai" # if you had one before
)
foreach ($dir in $oldDirsToRemove) {
    if (Test-Path $dir) {
        # Check if directory is empty
        if ((Get-ChildItem -Path $dir -ErrorAction SilentlyContinue).Count -eq 0) {
            Remove-Item -Path $dir -Recurse -Force
            Write-Host "  Removed empty directory: $dir"
        } else {
            Write-Warning "  Directory not empty, did not remove: $dir. Please check manually."
        }
    }
}

$ErrorActionPreference = "Continue" # Reset to default
Write-Host "Refactoring script finished." -ForegroundColor Green
Write-Host "Please thoroughly test your plugin. Rebuild your project with Gradle."
Write-Host "You may need to refresh your project in your IDE."