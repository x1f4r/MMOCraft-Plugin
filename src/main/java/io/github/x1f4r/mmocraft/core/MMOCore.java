package io.github.x1f4r.mmocraft.core;

// Managers
import io.github.x1f4r.mmocraft.items.ItemManager;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import io.github.x1f4r.mmocraft.stats.EntityStatsManager;
import io.github.x1f4r.mmocraft.display.DamageAndHealthDisplayManager;
import io.github.x1f4r.mmocraft.crafting.RecipeManager;
import io.github.x1f4r.mmocraft.commands.CommandRegistry;
import io.github.x1f4r.mmocraft.entities.EntityManager;

// Listeners - Grouped by function
import io.github.x1f4r.mmocraft.listeners.PlayerJoinQuitListener;
import io.github.x1f4r.mmocraft.listeners.PlayerEquipmentListener;
import io.github.x1f4r.mmocraft.listeners.EntitySpawnListener;
import io.github.x1f4r.mmocraft.listeners.PlayerToolListener;
import io.github.x1f4r.mmocraft.listeners.BowListener;
import io.github.x1f4r.mmocraft.listeners.PlayerSaturationListener; // <<< UPDATED IMPORT PATH
import io.github.x1f4r.mmocraft.items.PlayerAbilityListener;
import io.github.x1f4r.mmocraft.items.ArmorSetListener;
import io.github.x1f4r.mmocraft.crafting.CraftingGUIListener;
import io.github.x1f4r.mmocraft.combat.PlayerDamageListener;
import io.github.x1f4r.mmocraft.entities.MobDropListener;
import io.github.x1f4r.mmocraft.listeners.CompactorGUIListener;
import io.github.x1f4r.mmocraft.listeners.CompactorSystemListener;


import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MMOCore {

    private final MMOPlugin plugin;
    private final Logger log;

    // Managers
    private ItemManager itemManager;
    private PlayerStatsManager playerStatsManager;
    private EntityStatsManager entityStatsManager;
    private RecipeManager recipeManager;
    private CommandRegistry commandRegistry;
    private DamageAndHealthDisplayManager damageAndHealthDisplayManager;
    private CraftingGUIListener craftingGUIListener;
    private EntityManager entityManager;


    public MMOCore(MMOPlugin plugin) {
        this.plugin = plugin;
        this.log = MMOPlugin.getMMOLogger();
    }

    public boolean enableServices() {
        log.info("Initializing MMOCore services...");

        plugin.saveDefaultConfig();
        plugin.saveResource("items.yml", false);
        plugin.saveResource("mobs.yml", false);
        plugin.saveResource("recipes.yml", false);

        try {
            // Initialize Managers
            itemManager = new ItemManager(this);
            playerStatsManager = new PlayerStatsManager(this);
            entityStatsManager = new EntityStatsManager(this);
            damageAndHealthDisplayManager = new DamageAndHealthDisplayManager(this);
            recipeManager = new RecipeManager(this);
            entityManager = new EntityManager(this); // Initialize EntityManager
            craftingGUIListener = new CraftingGUIListener(this);
            commandRegistry = new CommandRegistry(this); // Initialize CommandRegistry

            // --- Call Initialization Methods ---
            itemManager.loadItems();
            playerStatsManager.initialize();
            entityStatsManager.initialize();
            damageAndHealthDisplayManager.initialize();
            recipeManager.initialize();
            entityManager.initialize(); // Call EntityManager init if needed


        } catch (Exception e) {
            log.log(Level.SEVERE, "Critical error initializing a core manager: " + e.getMessage(), e);
            return false;
        }

        registerListeners();
        commandRegistry.registerCommands(); // Register commands after managers are ready

        log.info("MMOCore services initialized, listeners and commands registered.");
        return true;
    }

    private void registerListeners() {
        PluginManager pm = plugin.getServer().getPluginManager();
        log.info("Registering Listeners...");

        // Core & Phase 2a
        pm.registerEvents(new PlayerJoinQuitListener(this), plugin);
        pm.registerEvents(new PlayerEquipmentListener(this), plugin);
        pm.registerEvents(new EntitySpawnListener(this), plugin);
        pm.registerEvents(damageAndHealthDisplayManager, plugin);

        // Phase 2b
        pm.registerEvents(craftingGUIListener, plugin);
        pm.registerEvents(new PlayerDamageListener(this), plugin);
        pm.registerEvents(new PlayerAbilityListener(this), plugin);
        pm.registerEvents(new ArmorSetListener(this), plugin);

        // Phase 2c
        pm.registerEvents(new MobDropListener(this), plugin); // Handles drops and AI cleanup call
        pm.registerEvents(new PlayerToolListener(this), plugin);
        pm.registerEvents(new BowListener(this), plugin);
        pm.registerEvents(new PlayerSaturationListener(), plugin); // <<< USES CORRECT CLASS NOW

        // Personal Compactor Listener
        pm.registerEvents(new CompactorGUIListener(this), plugin);
        pm.registerEvents(new CompactorSystemListener(this), plugin);

        log.info("Listeners registered.");
    }


    public void disableServices() {
        log.info("Disabling MMOCore services...");
        if (entityManager != null) {
            entityManager.shutdown(); // Stop AI tasks
        }
        if (damageAndHealthDisplayManager != null) {
            damageAndHealthDisplayManager.cleanupOnDisable();
        }
        // Other cleanup (e.g., saving data)
        Bukkit.getScheduler().cancelTasks(plugin);
        log.info("MMOCore services disabled.");
    }

    // --- Getters for Managers ---
    public MMOPlugin getPlugin() { return plugin; }
    public ItemManager getItemManager() {
        if (itemManager == null) throw new IllegalStateException("ItemManager not initialized!");
        return itemManager;
    }
    public PlayerStatsManager getPlayerStatsManager() {
        if (playerStatsManager == null) throw new IllegalStateException("PlayerStatsManager not initialized!");
        return playerStatsManager;
    }
    public EntityStatsManager getEntityStatsManager() {
        if (entityStatsManager == null) throw new IllegalStateException("EntityStatsManager not initialized!");
        return entityStatsManager;
    }
    public DamageAndHealthDisplayManager getDamageAndHealthDisplayManager() {
        if (damageAndHealthDisplayManager == null) throw new IllegalStateException("DamageAndHealthDisplayManager not initialized!");
        return damageAndHealthDisplayManager;
    }
    public RecipeManager getRecipeManager() {
        if (recipeManager == null) throw new IllegalStateException("RecipeManager not initialized!");
        return recipeManager;
    }
    public CraftingGUIListener getCraftingGUIListener() {
        if (craftingGUIListener == null) throw new IllegalStateException("CraftingGUIListener not initialized!");
        return craftingGUIListener;
    }
    public EntityManager getEntityManager() { // Added getter
        if (entityManager == null) throw new IllegalStateException("EntityManager not initialized!");
        return entityManager;
    }
    // No getter needed for CommandRegistry usually, it just does registration on enable.
}
