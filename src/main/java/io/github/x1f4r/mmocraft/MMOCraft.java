package io.github.x1f4r.mmocraft;

import io.github.x1f4r.mmocraft.commands.*;
import io.github.x1f4r.mmocraft.crafting.CraftingGUIListener;
import io.github.x1f4r.mmocraft.crafting.RecipeManager;
import io.github.x1f4r.mmocraft.items.ArmorSetListener;
import io.github.x1f4r.mmocraft.items.ItemManager;
import io.github.x1f4r.mmocraft.items.PlayerAbilityListener;
import io.github.x1f4r.mmocraft.listeners.BowListener;
import io.github.x1f4r.mmocraft.listeners.EntitySpawnListener;
import io.github.x1f4r.mmocraft.listeners.PlayerToolListener;
import io.github.x1f4r.mmocraft.player.listeners.PlayerSaturationListener;
import io.github.x1f4r.mmocraft.mobs.MobDropListener;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import io.github.x1f4r.mmocraft.player.listeners.PlayerDamageListener;
import io.github.x1f4r.mmocraft.player.listeners.PlayerEquipmentListener;
import io.github.x1f4r.mmocraft.stats.EntityStatsManager;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class MMOCraft extends JavaPlugin {

    private CraftingGUIListener craftingGUIListener;
    private RecipeManager recipeManager;
    private ItemManager itemManager;
    private PlayerStatsManager playerStatsManager;
    private EntityStatsManager entityStatsManager;

    private final Map<UUID, BukkitTask> activeDragonAIs = new HashMap<>();

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
        this.playerStatsManager = new PlayerStatsManager(this);
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
        pm.registerEvents(new PlayerSaturationListener(), this);
        pm.registerEvents(new BowListener(this), this);
        pm.registerEvents(new PlayerToolListener(this), this);

        registerCommands();

        getLogger().info("MMOCraft Plugin has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : activeDragonAIs.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        activeDragonAIs.clear();
        getLogger().info("MMOCraft Plugin has been disabled! Active AI tasks cancelled.");
    }

    private void registerCommands() {
        // Existing commands
        PluginCommand customCraftCmd = getCommand("customcraft");
        if (customCraftCmd != null) customCraftCmd.setExecutor(new CustomCraftCommand(this.craftingGUIListener));
        else getLogger().warning("Command 'customcraft' not found in plugin.yml!");

        PluginCommand summonDragonCmd = getCommand("summonelderdragon");
        if (summonDragonCmd != null) summonDragonCmd.setExecutor(new SummonElderDragonCommand(this));
        else getLogger().warning("Command 'summonelderdragon' not found in plugin.yml!");

        PluginCommand giveItemCmd = getCommand("givecustomitem");
        if (giveItemCmd != null) {
            GiveCustomItemCommand giveExecutor = new GiveCustomItemCommand(this);
            giveItemCmd.setExecutor(giveExecutor);
            giveItemCmd.setTabCompleter(giveExecutor);
        } else getLogger().warning("Command 'givecustomitem' not found in plugin.yml!");

        PluginCommand statsCmd = getCommand("stats");
        if (statsCmd != null) statsCmd.setExecutor(new PlayerStatsCommand(this));
        else getLogger().warning("Command 'stats' not found in plugin.yml!");

        PluginCommand reloadMobsCmd = getCommand("reloadmobs");
        if (reloadMobsCmd != null) reloadMobsCmd.setExecutor(new ReloadMobsConfigCommand(this));
        else getLogger().warning("Command 'reloadmobs' not found in plugin.yml!");

        // New Admin Stats Command
        PluginCommand adminStatsCmd = getCommand("mmoadmin");
        if (adminStatsCmd != null) {
            AdminStatsCommand adminExecutor = new AdminStatsCommand(this);
            adminStatsCmd.setExecutor(adminExecutor);
            adminStatsCmd.setTabCompleter(adminExecutor);
        } else {
            getLogger().log(Level.WARNING, "Command 'mmoadmin' not found in plugin.yml! Please add it.");
        }
    }

    public Map<UUID, BukkitTask> getActiveDragonAIs() {
        return activeDragonAIs;
    }

    public RecipeManager getRecipeManager() { return recipeManager; }
    public ItemManager getItemManager() { return itemManager; }
    public PlayerStatsManager getPlayerStatsManager() { return playerStatsManager; }
    public EntityStatsManager getEntityStatsManager() { return entityStatsManager; }
    public CraftingGUIListener getCraftingGUIListener() { return craftingGUIListener; }
}
