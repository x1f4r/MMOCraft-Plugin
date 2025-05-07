package io.github.x1f4r.mmocraft;

import io.github.x1f4r.mmocraft.commands.CustomCraftCommand;
import io.github.x1f4r.mmocraft.commands.GiveCustomItemCommand;
import io.github.x1f4r.mmocraft.commands.PlayerStatsCommand;
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
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

// No need for Objects import if we use direct null checks for PluginCommand
import java.util.logging.Level;

public final class MMOCraft extends JavaPlugin {

    private CraftingGUIListener craftingGUIListener;
    private RecipeManager recipeManager;
    private ItemManager itemManager;
    private PlayerStatsManager playerStatsManager;

    @Override
    public void onEnable() {
        getLogger().info("MMOCraft Plugin enabling...");
        NBTKeys.init(this); // Initialize NBTKeys utility

        // Ensure data folder exists for configs
        if (!getDataFolder().exists()) {
            if (!getDataFolder().mkdirs()) {
                getLogger().severe("Could not create plugin data folder! This may cause issues.");
            }
        }

        // Initialize Managers
        this.itemManager = new ItemManager(this);
        this.recipeManager = new RecipeManager(this);
        this.playerStatsManager = new PlayerStatsManager(this);

        // Initialize and register Listeners
        this.craftingGUIListener = new CraftingGUIListener(this); // Also used by a command

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
        // CustomCraft Command
        PluginCommand customCraftPluginCommand = this.getCommand("customcraft");
        if (customCraftPluginCommand != null) {
            customCraftPluginCommand.setExecutor(new CustomCraftCommand(this.craftingGUIListener));
            // If CustomCraftCommand needs tab completion, set it: customCraftPluginCommand.setTabCompleter(...);
        } else {
            getLogger().warning("Command 'customcraft' not found in plugin.yml or failed to load! Please check plugin.yml.");
        }

        // SummonElderDragon Command
        PluginCommand summonElderDragonPluginCommand = this.getCommand("summonelderdragon");
        if (summonElderDragonPluginCommand != null) {
            summonElderDragonPluginCommand.setExecutor(new SummonElderDragonCommand(this));
        } else {
            getLogger().warning("Command 'summonelderdragon' not found in plugin.yml or failed to load! Please check plugin.yml.");
        }

        // GiveCustomItem Command
        PluginCommand giveCustomItemPluginCommand = this.getCommand("givecustomitem");
        if (giveCustomItemPluginCommand != null) {
            GiveCustomItemCommand giveCmdExecutor = new GiveCustomItemCommand(this);
            giveCustomItemPluginCommand.setExecutor(giveCmdExecutor);
            giveCustomItemPluginCommand.setTabCompleter(giveCmdExecutor); // Set the TabCompleter
        } else {
            getLogger().warning("Command 'givecustomitem' NOT FOUND in plugin.yml or failed to load! Please check plugin.yml carefully for spelling and structure.");
        }

        // PlayerStats Command
        PluginCommand playerStatsPluginCommand = this.getCommand("stats");
        if (playerStatsPluginCommand != null) {
            playerStatsPluginCommand.setExecutor(new PlayerStatsCommand(this));
            // PlayerStatsCommand does not currently implement TabCompleter
        } else {
            getLogger().warning("Command 'stats' not found in plugin.yml or failed to load! Please check plugin.yml.");
        }
    }

    // Getters for Managers
    public RecipeManager getRecipeManager() { return recipeManager; }
    public ItemManager getItemManager() { return itemManager; }
    public PlayerStatsManager getPlayerStatsManager() { return playerStatsManager; }
    // Getter for Crafting GUI Listener (still needed by CustomCraftCommand)
    public CraftingGUIListener getCraftingGUIListener() { return craftingGUIListener; }
}