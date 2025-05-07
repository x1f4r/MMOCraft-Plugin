package io.github.x1f4r.mmocraft;

import io.github.x1f4r.mmocraft.commands.*;
import io.github.x1f4r.mmocraft.crafting.CraftingGUIListener;
import io.github.x1f4r.mmocraft.crafting.RecipeManager;
import io.github.x1f4r.mmocraft.items.ArmorSetListener;
import io.github.x1f4r.mmocraft.items.ItemManager;
import io.github.x1f4r.mmocraft.items.PlayerAbilityListener;
import io.github.x1f4r.mmocraft.listeners.EntitySpawnListener;
import io.github.x1f4r.mmocraft.listeners.PlayerSatiationListener;
import io.github.x1f4r.mmocraft.mobs.MobDropListener;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager; // Added import
import io.github.x1f4r.mmocraft.player.listeners.PlayerDamageListener;
import io.github.x1f4r.mmocraft.player.listeners.PlayerEquipmentListener;
import io.github.x1f4r.mmocraft.stats.EntityStatsManager;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class MMOCraft extends JavaPlugin {

    private CraftingGUIListener craftingGUIListener;
    private RecipeManager recipeManager;
    private ItemManager itemManager;
    private PlayerStatsManager playerStatsManager; // This line should now work
    private EntityStatsManager entityStatsManager;

    @Override
    public void onEnable() {
        getLogger().info("MMOCraft Plugin enabling...");
        NBTKeys.init(this);

        if (!getDataFolder().exists()) {
            if (!getDataFolder().mkdirs()) {
                getLogger().severe("Could not create plugin data folder! This may cause issues.");
            }
        }
        saveDefaultConfig();
        saveResource("mobs.yml", false);
        saveResource("items.yml", false);
        saveResource("recipes.yml", false);

        this.itemManager = new ItemManager(this);
        this.recipeManager = new RecipeManager(this);
        this.playerStatsManager = new PlayerStatsManager(this); // This line should now work
        this.entityStatsManager = new EntityStatsManager(this);

        this.craftingGUIListener = new CraftingGUIListener(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this.craftingGUIListener, this);
        pm.registerEvents(new MobDropListener(this), this);
        pm.registerEvents(new ArmorSetListener(this), this);
        pm.registerEvents(new PlayerAbilityListener(this), this);
        pm.registerEvents(new PlayerDamageListener(this), this);
        pm.registerEvents(new PlayerEquipmentListener(this), this);
        pm.registerEvents(new EntitySpawnListener(this), this);
        pm.registerEvents(new PlayerSatiationListener(), this);

        registerCommands();

        getLogger().info("MMOCraft Plugin has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("MMOCraft Plugin has been disabled!");
    }

    private void registerCommands() {
        PluginCommand customCraftPluginCommand = this.getCommand("customcraft");
        if (customCraftPluginCommand != null) {
            customCraftPluginCommand.setExecutor(new CustomCraftCommand(this.craftingGUIListener));
        } else { getLogger().log(Level.WARNING, "Command 'customcraft' not found in plugin.yml!");}

        PluginCommand summonElderDragonPluginCommand = this.getCommand("summonelderdragon");
        if (summonElderDragonPluginCommand != null) {
            summonElderDragonPluginCommand.setExecutor(new SummonElderDragonCommand(this));
        } else { getLogger().log(Level.WARNING, "Command 'summonelderdragon' not found in plugin.yml!");}

        PluginCommand giveCustomItemPluginCommand = this.getCommand("givecustomitem");
        if (giveCustomItemPluginCommand != null) {
            GiveCustomItemCommand giveCmdExecutor = new GiveCustomItemCommand(this);
            giveCustomItemPluginCommand.setExecutor(giveCmdExecutor);
            giveCustomItemPluginCommand.setTabCompleter(giveCmdExecutor);
        } else { getLogger().log(Level.WARNING, "Command 'givecustomitem' NOT FOUND in plugin.yml!");}

        PluginCommand playerStatsPluginCommand = this.getCommand("stats");
        if (playerStatsPluginCommand != null) {
            playerStatsPluginCommand.setExecutor(new PlayerStatsCommand(this));
        } else { getLogger().log(Level.WARNING, "Command 'stats' not found in plugin.yml!");}

        PluginCommand reloadMobsConfigCmd = this.getCommand("reloadmobs");
        if (reloadMobsConfigCmd != null) {
            reloadMobsConfigCmd.setExecutor(new ReloadMobsConfigCommand(this));
        } else { getLogger().log(Level.WARNING, "Command 'reloadmobs' not found in plugin.yml (Optional: for reloading mobs.yml).");}
    }

    public RecipeManager getRecipeManager() { return recipeManager; }
    public ItemManager getItemManager() { return itemManager; }
    public PlayerStatsManager getPlayerStatsManager() { return playerStatsManager; } // This line should now work
    public EntityStatsManager getEntityStatsManager() { return entityStatsManager; }
    public CraftingGUIListener getCraftingGUIListener() { return craftingGUIListener; }
}