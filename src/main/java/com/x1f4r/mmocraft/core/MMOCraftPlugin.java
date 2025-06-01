package com.x1f4r.mmocraft.core;

import com.x1f4r.mmocraft.combat.listeners.PlayerCombatListener;
import com.x1f4r.mmocraft.combat.service.BasicDamageCalculationService;
import com.x1f4r.mmocraft.combat.service.DamageCalculationService;
import com.x1f4r.mmocraft.combat.service.DefaultMobStatProvider;
import com.x1f4r.mmocraft.combat.service.MobStatProvider;
import com.x1f4r.mmocraft.command.BasicCommandRegistryService;
import com.x1f4r.mmocraft.command.CommandRegistryService;
import com.x1f4r.mmocraft.command.commands.CustomCraftCommand;
import com.x1f4r.mmocraft.command.commands.ExecuteSkillCommand;
import com.x1f4r.mmocraft.command.commands.MMOCraftInfoCommand;
import com.x1f4r.mmocraft.command.commands.admin.MMOCAdminRootCommand;
import com.x1f4r.mmocraft.config.BasicConfigService;
import com.x1f4r.mmocraft.config.ConfigService;
import com.x1f4r.mmocraft.crafting.service.BasicRecipeRegistryService;
import com.x1f4r.mmocraft.crafting.service.RecipeRegistryService;
import com.x1f4r.mmocraft.crafting.ui.CraftingUIManager;
import com.x1f4r.mmocraft.eventbus.BasicEventBusService;
import com.x1f4r.mmocraft.eventbus.EventBusService;
import com.x1f4r.mmocraft.eventbus.events.PluginReloadedEvent;
import com.x1f4r.mmocraft.item.equipment.listeners.PlayerEquipmentListener;
import com.x1f4r.mmocraft.item.equipment.service.PlayerEquipmentManager;
import com.x1f4r.mmocraft.item.impl.SimpleSword;
import com.x1f4r.mmocraft.item.impl.TrainingArmor;
import com.x1f4r.mmocraft.item.service.BasicCustomItemRegistry;
import com.x1f4r.mmocraft.item.service.CustomItemRegistry;
import com.x1f4r.mmocraft.loot.listeners.MobDeathLootListener;
import com.x1f4r.mmocraft.loot.model.LootTable;
import com.x1f4r.mmocraft.loot.model.LootTableEntry;
import com.x1f4r.mmocraft.loot.service.BasicLootService;
import com.x1f4r.mmocraft.loot.service.LootService;
import com.x1f4r.mmocraft.persistence.PersistenceService;
import com.x1f4r.mmocraft.persistence.SqlitePersistenceService;
import com.x1f4r.mmocraft.playerdata.BasicPlayerDataService;
import com.x1f4r.mmocraft.playerdata.PlayerDataService;
import com.x1f4r.mmocraft.playerdata.listeners.PlayerJoinQuitListener;
import com.x1f4r.mmocraft.skill.impl.MinorHealSkill;
import com.x1f4r.mmocraft.skill.impl.StrongStrikeSkill;
import com.x1f4r.mmocraft.skill.service.BasicSkillRegistryService;
import com.x1f4r.mmocraft.skill.service.SkillRegistryService;
import com.x1f4r.mmocraft.statuseffect.manager.BasicStatusEffectManager;
import com.x1f4r.mmocraft.statuseffect.manager.StatusEffectManager;
import com.x1f4r.mmocraft.util.LoggingUtil;
import com.x1f4r.mmocraft.world.spawning.service.BasicCustomSpawningService;
import com.x1f4r.mmocraft.world.spawning.service.CustomSpawningService;
import com.x1f4r.mmocraft.world.zone.listeners.PlayerZoneTrackerListener;
import com.x1f4r.mmocraft.world.zone.model.Zone;
import com.x1f4r.mmocraft.world.zone.service.BasicZoneManager;
import com.x1f4r.mmocraft.world.zone.service.ZoneManager;
import com.x1f4r.mmocraft.world.resourcegathering.listeners.ResourceNodeInteractionListener;
import com.x1f4r.mmocraft.world.resourcegathering.model.ResourceNodeType;
import com.x1f4r.mmocraft.world.resourcegathering.service.ActiveNodeManager;
import com.x1f4r.mmocraft.world.resourcegathering.service.BasicResourceNodeRegistryService;
import com.x1f4r.mmocraft.world.resourcegathering.service.ResourceNodeRegistryService;


import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Collections; // Though not directly used for .of, good if other Collections methods are used

public final class MMOCraftPlugin extends JavaPlugin {

    private ConfigService configService;
    private EventBusService eventBusService;
    private PersistenceService persistenceService;
    private CommandRegistryService commandRegistryService;
    private PlayerDataService playerDataService;
    private DamageCalculationService damageCalculationService;
    private SkillRegistryService skillRegistryService;
    private StatusEffectManager statusEffectManager;
    private MobStatProvider mobStatProvider;
    private CustomItemRegistry customItemRegistry;
    private PlayerEquipmentManager playerEquipmentManager;
    private LootService lootService;
    private RecipeRegistryService recipeRegistryService;
    private CraftingUIManager craftingUIManager;
    private CustomSpawningService customSpawningService;
    private ZoneManager zoneManager;
    private ResourceNodeRegistryService resourceNodeRegistryService; // Resource Gathering
    private ActiveNodeManager activeNodeManager;                     // Resource Gathering
    private LoggingUtil loggingUtil;
    private BukkitTask statusEffectTickTask;
    private BukkitTask customSpawningTask;
    private BukkitTask resourceNodeTickTask;                         // Resource Gathering

    @Override
    public void onEnable() {
        LoggingUtil preliminaryLogger = new LoggingUtil(this);
        configService = new BasicConfigService(this, preliminaryLogger);
        loggingUtil = new LoggingUtil(this, configService);
        preliminaryLogger.info("Preliminary logger transitioning to final logger.");
        loggingUtil.info("Final LoggingUtil initialized.");

        eventBusService = new BasicEventBusService(loggingUtil);
        customItemRegistry = new BasicCustomItemRegistry(this, loggingUtil);
        registerCustomItems();
        loggingUtil.info("CustomItemRegistry initialized and items registered.");

        commandRegistryService = new BasicCommandRegistryService(this);

        try {
            persistenceService = new SqlitePersistenceService(this);
            persistenceService.initDatabase();
        } catch (Exception e) {
            LoggingUtil loggerToUse = (loggingUtil != null) ? loggingUtil : preliminaryLogger;
            loggerToUse.severe("Failed to initialize PersistenceService or database. Plugin may not function correctly.", e);
        }

        playerDataService = new BasicPlayerDataService(this, persistenceService, loggingUtil, eventBusService);
        playerDataService.initDatabaseSchema();

        mobStatProvider = new DefaultMobStatProvider();
        loggingUtil.info("MobStatProvider initialized.");

        damageCalculationService = new BasicDamageCalculationService(playerDataService, loggingUtil, mobStatProvider);
        loggingUtil.info("DamageCalculationService initialized.");

        playerEquipmentManager = new PlayerEquipmentManager(this, playerDataService, customItemRegistry, loggingUtil);
        loggingUtil.info("PlayerEquipmentManager initialized.");

        skillRegistryService = new BasicSkillRegistryService(loggingUtil);
        registerSkills();
        loggingUtil.info("SkillRegistryService initialized and skills registered.");

        statusEffectManager = new BasicStatusEffectManager(this, loggingUtil, playerDataService);
        loggingUtil.info("StatusEffectManager initialized.");

        lootService = new BasicLootService(this, loggingUtil);
        registerDefaultLootTables();
        loggingUtil.info("LootService initialized and default loot tables registered.");

        recipeRegistryService = new BasicRecipeRegistryService(this, loggingUtil, customItemRegistry);
        loggingUtil.info("RecipeRegistryService initialized.");

        craftingUIManager = new CraftingUIManager(this, recipeRegistryService, playerDataService, customItemRegistry, loggingUtil);
        loggingUtil.info("CraftingUIManager initialized.");

        customSpawningService = new BasicCustomSpawningService(this, loggingUtil, mobStatProvider, lootService, customItemRegistry);
        // TODO: Register custom spawn rules here for CustomSpawningService
        loggingUtil.info("CustomSpawningService initialized.");

        zoneManager = new BasicZoneManager(this, loggingUtil, eventBusService); // Corrected
        registerDefaultZones();
        loggingUtil.info("ZoneManager initialized and default zones registered.");

        resourceNodeRegistryService = new BasicResourceNodeRegistryService(loggingUtil); // Resource Gathering
        registerResourceNodeTypes(); // Resource Gathering
        activeNodeManager = new ActiveNodeManager(this, loggingUtil, resourceNodeRegistryService, lootService, customItemRegistry); // Resource Gathering
        placeInitialResourceNodes(); // Resource Gathering
        loggingUtil.info("ResourceNodeRegistryService and ActiveNodeManager initialized.");


        // Register Listeners (after all services they depend on are initialized)
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(playerDataService, loggingUtil), this);
        getServer().getPluginManager().registerEvents(new PlayerZoneTrackerListener(zoneManager, loggingUtil, eventBusService), this); // Corrected
        getServer().getPluginManager().registerEvents(new PlayerCombatListener(damageCalculationService, playerDataService, loggingUtil, mobStatProvider), this);
        getServer().getPluginManager().registerEvents(new PlayerEquipmentListener(this, playerEquipmentManager, loggingUtil), this);
        getServer().getPluginManager().registerEvents(new ResourceNodeInteractionListener(this, activeNodeManager, resourceNodeRegistryService, lootService, customItemRegistry, playerDataService, loggingUtil), this); // Resource Gathering
        getServer().getPluginManager().registerEvents(new MobDeathLootListener(lootService, customItemRegistry, this, loggingUtil), this);
        // CraftingUIManager registers its own listeners.

        // Register Commands (after services they depend on)
        commandRegistryService.registerCommand("mmoc", new MMOCraftInfoCommand(this, "mmoc", "mmocraft.command.info", "Base command for MMOCraft.", loggingUtil));
        commandRegistryService.registerCommand("useskill", new ExecuteSkillCommand(this));
        commandRegistryService.registerCommand("mmocadm", new MMOCAdminRootCommand(this));
        commandRegistryService.registerCommand("customcraft", new CustomCraftCommand(this));

        // Start Schedulers
        long statusEffectTickInterval = 20L; // 1 second
        statusEffectTickTask = getServer().getScheduler().runTaskTimer(this, () -> {
            if (statusEffectManager != null) {
                statusEffectManager.tickAllActiveEffects();
            }
        }, statusEffectTickInterval, statusEffectTickInterval);
        loggingUtil.info("StatusEffect tick scheduler started (every " + statusEffectTickInterval + " server ticks).");

        long spawningInterval = 200L; // 10 seconds (20 ticks * 10 seconds)
        customSpawningTask = getServer().getScheduler().runTaskTimer(this, () -> { // Added
            if (customSpawningService != null) {
                customSpawningService.attemptSpawns();
            }
        }, spawningInterval, spawningInterval);
        loggingUtil.info("CustomSpawningService task scheduler started (every " + spawningInterval + " server ticks).");

        long resourceNodeTickInterval = 100L; // 5 seconds (20 ticks * 5)
        resourceNodeTickTask = getServer().getScheduler().runTaskTimer(this, () -> { // Resource Gathering
            if (activeNodeManager != null) {
                activeNodeManager.tickNodes();
            }
        }, resourceNodeTickInterval, resourceNodeTickInterval);
        loggingUtil.info("ActiveNodeManager task scheduler started (every " + resourceNodeTickInterval + " server ticks).");


        if (eventBusService != null) {
            eventBusService.register(PluginReloadedEvent.class, event -> {
                loggingUtil.info("PluginReloadedEvent handled: Configuration has been reloaded. Event name: " + event.getEventName());
            });
        } else {
            loggingUtil.warning("EventBusService was not initialized. Cannot register PluginReloadedEvent handler.");
        }

        loggingUtil.info("MMOCraft core loaded and all services initialized!");
        loggingUtil.info("Default Max Health from config: " + configService.getInt("stats.max-health"));
        loggingUtil.debug("onEnable sequence completed. Debug logging is " + (configService.getBoolean("core.debug-logging") ? "enabled" : "disabled") + ".");
    }

    @Override
    public void onDisable() {
        loggingUtil.info("MMOCraft shutting down...");

        if (resourceNodeTickTask != null && !resourceNodeTickTask.isCancelled()) { // Resource Gathering
            resourceNodeTickTask.cancel();
            loggingUtil.info("ActiveNodeManager task scheduler cancelled.");
        }
        if (customSpawningTask != null && !customSpawningTask.isCancelled()) {
            customSpawningTask.cancel();
            loggingUtil.info("CustomSpawningService task scheduler cancelled.");
        }
        if (statusEffectTickTask != null && !statusEffectTickTask.isCancelled()) {
            statusEffectTickTask.cancel();
            loggingUtil.info("StatusEffect tick scheduler cancelled.");
        }

        if (playerDataService instanceof BasicPlayerDataService) {
            ((BasicPlayerDataService) playerDataService).shutdown();
        }
        if (statusEffectManager instanceof BasicStatusEffectManager) {
            ((BasicStatusEffectManager) statusEffectManager).shutdown();
        }
        if (customSpawningService instanceof BasicCustomSpawningService) {
            ((BasicCustomSpawningService) customSpawningService).shutdown();
        }
        if (activeNodeManager != null) { // Resource Gathering
            activeNodeManager.shutdown();
        }
        if (persistenceService != null) {
            try {
                persistenceService.close();
            } catch (SQLException e) {
                loggingUtil.severe("Error while closing database connection: " + e.getMessage(), e);
            }
        } else {
            loggingUtil.warning("PersistenceService was not initialized, nothing to close.");
        }
        loggingUtil.info("MMOCraft has been disabled.");
    }

    // --- Service Getters ---
    public ConfigService getConfigService() { return configService; }
    public EventBusService getEventBusService() { return eventBusService; }
    public PersistenceService getPersistenceService() { return persistenceService; }
    public CommandRegistryService getCommandRegistryService() { return commandRegistryService; }
    public PlayerDataService getPlayerDataService() { return playerDataService; }
    public DamageCalculationService getDamageCalculationService() { return damageCalculationService; }
    public SkillRegistryService getSkillRegistryService() { return skillRegistryService; }
    public StatusEffectManager getStatusEffectManager() { return statusEffectManager; }
    public MobStatProvider getMobStatProvider() { return mobStatProvider; }
    public CustomItemRegistry getCustomItemRegistry() { return customItemRegistry; }
    public PlayerEquipmentManager getPlayerEquipmentManager() { return playerEquipmentManager; }
    public LootService getLootService() { return lootService; }
    public RecipeRegistryService getRecipeRegistryService() { return recipeRegistryService; }
    public CraftingUIManager getCraftingUIManager() { return craftingUIManager; }
    public CustomSpawningService getCustomSpawningService() { return customSpawningService; }
    public ZoneManager getZoneManager() { return zoneManager; }
    public ResourceNodeRegistryService getResourceNodeRegistryService() { return resourceNodeRegistryService; } // Resource Gathering
    public ActiveNodeManager getActiveNodeManager() { return activeNodeManager; }                     // Resource Gathering
    public LoggingUtil getLoggingUtil() { return loggingUtil; }

    // --- Plugin Setup Methods ---
    private void registerResourceNodeTypes() { // Resource Gathering
        if (resourceNodeRegistryService == null || lootService == null) {
            loggingUtil.severe("ResourceNodeRegistryService or LootService not initialized. Cannot register node types.");
            return;
        }
        // Ensure a loot table for "stone_node_loot" exists or is created.
        // For testing, we can make a dummy one if it doesn't affect other tests.
        // Or assume it's defined elsewhere (e.g. via config or another system)
         if (lootService.getLootTableById("stone_node_loot").isEmpty()) {
            LootTable stoneLoot = new LootTable("stone_node_loot", List.of(
                new LootTableEntry("COBBLESTONE", 1.0, 1, 1) // Using Material name as placeholder item ID
            ));
            lootService.registerLootTable(null, stoneLoot); // Registering without EntityType for general use
             loggingUtil.info("Registered placeholder 'stone_node_loot' table for resource node testing.");
        }
         if (lootService.getLootTableById("iron_ore_node_loot").isEmpty()) {
            LootTable ironLoot = new LootTable("iron_ore_node_loot", List.of(
                new LootTableEntry("RAW_IRON", 1.0, 1, 1)
            ));
            lootService.registerLootTable(null, ironLoot);
             loggingUtil.info("Registered placeholder 'iron_ore_node_loot' table for resource node testing.");
        }


        ResourceNodeType stoneNode = new ResourceNodeType(
                "stone_node", Material.STONE, 5.0,
                Set.of(Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE),
                "stone_node_loot", 60, "&7Stone Deposit"
        );
        resourceNodeRegistryService.registerNodeType(stoneNode);

        ResourceNodeType ironOreNode = new ResourceNodeType(
                "iron_ore_node", Material.IRON_ORE, 10.0,
                Set.of(Material.STONE_PICKAXE, Material.IRON_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE),
                "iron_ore_node_loot", 180, "&fIron Vein"
        );
        resourceNodeRegistryService.registerNodeType(ironOreNode);
        loggingUtil.info("Registered default resource node types.");
    }

    private void placeInitialResourceNodes() { // Resource Gathering
        if (activeNodeManager == null) {
            loggingUtil.severe("ActiveNodeManager not initialized. Cannot place initial resource nodes.");
            return;
        }
        World defaultWorld = Bukkit.getWorld("world");
        if (defaultWorld == null && !Bukkit.getWorlds().isEmpty()) {
            defaultWorld = Bukkit.getWorlds().get(0);
        }
        if (defaultWorld == null) {
            loggingUtil.severe("No worlds loaded. Cannot place initial resource nodes.");
            return;
        }

        // Example locations - ensure these are suitable for your test world
        activeNodeManager.placeNewNode(new Location(defaultWorld, 10, 60, 10), "stone_node");
        activeNodeManager.placeNewNode(new Location(defaultWorld, 12, 60, 10), "stone_node");
        activeNodeManager.placeNewNode(new Location(defaultWorld, 10, 60, 12), "iron_ore_node");
        loggingUtil.info("Placed initial resource nodes in world '" + defaultWorld.getName() + "'.");
    }

    private void registerDefaultZones() {
        if (zoneManager == null) {
            loggingUtil.severe("ZoneManager not initialized. Cannot register default zones.");
            return;
        }
        World defaultWorld = Bukkit.getWorld("world");
        if (defaultWorld == null) {
            if (!Bukkit.getWorlds().isEmpty()) {
                defaultWorld = Bukkit.getWorlds().get(0);
                loggingUtil.warning("'world' not found. Using first loaded world for default zone: " + defaultWorld.getName());
            } else {
                loggingUtil.severe("No worlds loaded. Cannot register 'SpawnSanctuary' default zone.");
                return;
            }
        }

        Zone spawnSanctuary = new Zone(
                "spawn_sanctuary", "Spawn Sanctuary", defaultWorld.getName(),
                -50, 0, -50, 50, 128, 50,
                Map.of("isSanctuary", true, "pvpAllowed", false)
        );
        zoneManager.registerZone(spawnSanctuary);
        loggingUtil.info("Registered default zone: Spawn Sanctuary in world '" + defaultWorld.getName() + "'");
    }

    private void registerSkills() {
        if (skillRegistryService == null) {
            loggingUtil.severe("SkillRegistryService not initialized. Cannot register skills.");
            return;
        }
        skillRegistryService.registerSkill(new StrongStrikeSkill(this));
        skillRegistryService.registerSkill(new MinorHealSkill(this));
    }

    private void registerCustomItems() {
        if (customItemRegistry == null) {
            loggingUtil.severe("CustomItemRegistry not initialized. Cannot register custom items.");
            return;
        }
        customItemRegistry.registerItem(new SimpleSword(this));
        customItemRegistry.registerItem(new TrainingArmor(this));
    }

    private void registerDefaultLootTables() {
        if (lootService == null || customItemRegistry == null) {
            loggingUtil.severe("LootService or CustomItemRegistry not initialized. Cannot register default loot tables.");
            return;
        }
        LootTable zombieLootTable = new LootTable("zombie_common_drops", List.of(
            new LootTableEntry("simple_sword", 0.05, 1, 1)
        ));
        lootService.registerLootTable(EntityType.ZOMBIE, zombieLootTable);
        loggingUtil.info("Default loot tables registered.");
    }

    public void reloadPluginConfig() {
        if (configService != null) {
            configService.reloadConfig();
            loggingUtil.info("MMOCraft configuration reloaded via reloadPluginConfig().");
            loggingUtil.info("Default Max Health after reload: " + configService.getInt("stats.max-health"));
            loggingUtil.debug("Debug status after reload: " + (configService.getBoolean("core.debug-logging") ? "enabled" : "disabled") + ".");

            if (eventBusService != null) {
                eventBusService.call(new PluginReloadedEvent());
            } else {
                loggingUtil.warning("EventBusService not available, cannot fire PluginReloadedEvent.");
            }
        } else {
            loggingUtil.severe("ConfigService not available. Cannot reload configuration.");
        }
    }
}
