package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.ai.AIBehavior;
import io.github.x1f4r.mmocraft.ai.AIController;
import io.github.x1f4r.mmocraft.ai.behaviors.*; // Import your example behaviors
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import io.github.x1f4r.mmocraft.entities.CustomMobType;
import io.github.x1f4r.mmocraft.entities.EntityStats;
import io.github.x1f4r.mmocraft.entities.listeners.CustomMobLifecycleListener;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent; // For SpawnReason
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CustomMobService implements Service {
    private MMOCore core;
    private LoggingService logging;
    private ConfigService configService;
    private ItemService itemService;
    private EntityStatsService entityStatsService;
    // NBTService keys are static

    private final Map<String, CustomMobType> customMobRegistry = new ConcurrentHashMap<>();
    private final Map<String, AIBehavior> aiBehaviorRegistry = new ConcurrentHashMap<>();
    private final Map<UUID, AIController> activeAIControllers = new ConcurrentHashMap<>();

    private long defaultAIControllerTickRate;
    private final Random random = new Random();

    public CustomMobService(MMOCore core) {
        this.core = core;
    }

    @Override
    public void initialize(MMOCore core) {
        this.logging = core.getService(LoggingService.class);
        this.configService = core.getService(ConfigService.class);
        this.itemService = core.getService(ItemService.class);
        this.entityStatsService = core.getService(EntityStatsService.class);
        // NBTService is available via static access

        this.defaultAIControllerTickRate = configService.getMainConfig().getLong("custom_mobs.ai_controller_tick_rate", 5L);
        if (this.defaultAIControllerTickRate <= 0) this.defaultAIControllerTickRate = 5L;

        FileConfiguration customMobsCfg = configService.getConfig(ConfigService.CUSTOM_MOBS_CONFIG_FILENAME);
        if (customMobsCfg.getKeys(false).isEmpty() && customMobsCfg.getConfigurationSection("custom_mobs") == null) {
            logging.warn("'" + ConfigService.CUSTOM_MOBS_CONFIG_FILENAME + "' is empty or missing 'custom_mobs' section. No custom mobs defined.");
        } else {
            loadCustomMobTypes(customMobsCfg);
        }
        configService.subscribeToReload(ConfigService.CUSTOM_MOBS_CONFIG_FILENAME, this::loadCustomMobTypes);

        registerDefaultAIBehaviors();

        // EntityLifecycleListener now needs CustomMobService
        // If EntityLifecycleListener was registered by EntityStatsService, it needs to be updated or replaced.
        // For now, let CustomMobService register its own more specific listener.
        core.registerListener(new CustomMobLifecycleListener(this, entityStatsService, core));
        logging.info(getServiceName() + " initialized. Loaded " + customMobRegistry.size() + " custom mob types. Registered " + aiBehaviorRegistry.size() + " AI behaviors.");
    }

    private void registerDefaultAIBehaviors() {
        // behaviorId, followSpeedMultiplier, attackRange, detectionRange, attackCooldownTicks
        registerAIBehavior(new BasicMeleeBehavior("basic_melee", 1.1, 2.0, 16.0, 20));
        registerAIBehavior(new BasicMeleeBehavior("basic_melee_crypt_ghoul", 1.25, 1.8, 20.0, 15)); // Faster, slightly shorter range

        // behaviorId, speedMultiplier, maxStrollRadius, averageStrollIntervalTicks
        registerAIBehavior(new RandomStrollBehavior("random_stroll_normal", 1.0, 10, 120)); // Strolls every ~6s
        registerAIBehavior(new RandomStrollBehavior("random_stroll_fast", 1.2, 12, 80));   // Strolls quicker

        // behaviorId, maxLookRange
        registerAIBehavior(new LookAtTargetBehavior("look_at_player_target", 24.0));
        // Add more behaviors as they are developed
    }

    @Override
    public void shutdown() {
        new ArrayList<>(activeAIControllers.values()).forEach(AIController::stop); // Stop all active controllers
        activeAIControllers.clear();
        customMobRegistry.clear();
        aiBehaviorRegistry.clear();
        logging.info(getServiceName() + " shutdown. Custom mob systems cleared.");
    }

    private void loadCustomMobTypes(FileConfiguration config) {
        customMobRegistry.clear();
        ConfigurationSection mobsRootSection = config.getConfigurationSection("custom_mobs");
        if (mobsRootSection == null) {
            logging.info("No 'custom_mobs' section found in " + ConfigService.CUSTOM_MOBS_CONFIG_FILENAME + ". No custom mobs loaded.");
            return;
        }

        for (String typeId : mobsRootSection.getKeys(false)) {
            ConfigurationSection mobConfig = mobsRootSection.getConfigurationSection(typeId);
            if (mobConfig == null) continue;

            try {
                EntityType baseEntityType = EntityType.valueOf(mobConfig.getString("base_entity_type", "ZOMBIE").toUpperCase());
                String displayName = mobConfig.getString("display_name", "&f" + typeId);

                ConfigurationSection statsConfig = mobConfig.getConfigurationSection("stats");
                EntityStats stats;
                if (statsConfig != null) {
                    stats = new EntityStats(
                            statsConfig.getDouble("maxHealth", 20.0), statsConfig.getInt("defense", 0),
                            statsConfig.getInt("strength", 5), statsConfig.getInt("critChance", 0),
                            statsConfig.getInt("critDamage", 0), statsConfig.getInt("speedPercent", 0),
                            statsConfig.getInt("maxMana", 0)
                    );
                } else {
                    stats = EntityStats.createDefault(null); // Create a default based on no entity
                    logging.warn("CustomMobType '" + typeId + "' missing 'stats' section. Using generic default stats.");
                }

                List<String> aiBehaviorIds = mobConfig.getStringList("ai_behavior_ids");
                String customLootTableId = mobConfig.getString("custom_loot_table_id");

                Map<String, String> equipment = new HashMap<>();
                ConfigurationSection equipConfig = mobConfig.getConfigurationSection("equipment");
                if (equipConfig != null) {
                    for (String slotKey : equipConfig.getKeys(false)) {
                        equipment.put(slotKey.toUpperCase(), equipConfig.getString(slotKey));
                    }
                }

                Map<String, Object> genericNbtData = new HashMap<>();
                ConfigurationSection nbtConfigData = mobConfig.getConfigurationSection("generic_nbt_data");
                if (nbtConfigData != null) {
                    for (String key : nbtConfigData.getKeys(false)) {
                        genericNbtData.put(key, nbtConfigData.get(key));
                    }
                }

                ConfigurationSection spawnRulesConfig = mobConfig.getConfigurationSection("spawn_rules");
                boolean replaceVanilla = false; double replaceChance = 0.0; int spawnWeight = 10;
                Set<String> spawnBiomes = new HashSet<>(); Integer minLight = null, maxLight = null;
                Integer minY = null, maxY = null; String requiredWorld = null;

                if (spawnRulesConfig != null) {
                    replaceVanilla = spawnRulesConfig.getBoolean("replace_vanilla", false);
                    replaceChance = spawnRulesConfig.getDouble("replace_chance", 0.1);
                    spawnWeight = spawnRulesConfig.getInt("weight", 10);
                    spawnBiomes.addAll(spawnRulesConfig.getStringList("biomes").stream().map(String::toUpperCase).collect(Collectors.toSet()));
                    if (spawnRulesConfig.contains("min_spawn_light_level")) minLight = spawnRulesConfig.getInt("min_spawn_light_level");
                    if (spawnRulesConfig.contains("max_spawn_light_level")) maxLight = spawnRulesConfig.getInt("max_spawn_light_level");
                    if (spawnRulesConfig.contains("min_spawn_y")) minY = spawnRulesConfig.getInt("min_spawn_y");
                    if (spawnRulesConfig.contains("max_spawn_y")) maxY = spawnRulesConfig.getInt("max_spawn_y");
                    requiredWorld = spawnRulesConfig.getString("required_world");
                }

                CustomMobType type = new CustomMobType(typeId.toLowerCase(), baseEntityType, displayName, stats,
                        aiBehaviorIds, customLootTableId, equipment,
                        replaceVanilla, replaceChance, spawnWeight, spawnBiomes,
                        minLight, maxLight, minY, maxY, requiredWorld, genericNbtData);
                registerCustomMobType(type);

            } catch (IllegalArgumentException e) {
                logging.warn("Invalid configuration for custom mob '" + typeId + "'. Error: " + e.getMessage() + ". Skipping.");
            } catch (Exception e) {
                logging.severe("Unexpected error parsing custom mob type: '" + typeId + "'", e);
            }
        }
        logging.info("Reloaded " + customMobRegistry.size() + " custom mob types from config.");
    }

    public void registerCustomMobType(@NotNull CustomMobType mobType) {
        customMobRegistry.put(mobType.typeId().toLowerCase(), mobType);
        if (logging.isDebugMode()) logging.debug("Registered custom mob type: " + mobType.typeId());
    }

    @Nullable
    public CustomMobType getCustomMobType(@Nullable String typeId) {
        if (typeId == null) return null;
        return customMobRegistry.get(typeId.toLowerCase());
    }

    public void registerAIBehavior(@NotNull AIBehavior behavior) {
        aiBehaviorRegistry.put(behavior.getBehaviorId().toLowerCase(), behavior);
        if (logging.isDebugMode()) logging.debug("Registered AI behavior: " + behavior.getBehaviorId());
    }

    @Nullable
    public AIBehavior getAIBehavior(@Nullable String behaviorId) {
        if (behaviorId == null) return null;
        return aiBehaviorRegistry.get(behaviorId.toLowerCase());
    }

    @Nullable
    public LivingEntity spawnCustomMob(@NotNull String typeId, @NotNull Location location, @Nullable Consumer<LivingEntity> postSpawnModifier) {
        CustomMobType mobType = getCustomMobType(typeId);
        if (mobType == null) {
            logging.warn("Attempted to spawn unknown custom mob type ID: '" + typeId + "' at " + location);
            return null;
        }

        World world = location.getWorld();
        if (world == null) {
            logging.warn("Cannot spawn custom mob '" + typeId + "', location has no world.");
            return null;
        }

        LivingEntity entity = (LivingEntity) world.spawnEntity(location, mobType.baseEntityType());
        if (!entity.isValid()) { // Check if spawn was successful
            logging.severe("Failed to spawn base entity " + mobType.baseEntityType() + " for custom mob " + typeId);
            return null;
        }

        // 1. Apply CustomMobTypeID NBT Tag FIRST
        NBTService.set(entity.getPersistentDataContainer(), NBTService.CUSTOM_MOB_TYPE_ID_KEY, PersistentDataType.STRING, mobType.typeId());

        // 2. Apply Stats (EntityStatsService will use the NBT tag)
        entityStatsService.applyStatsToEntity(entity); // This also calls VisualFeedbackService to update health bar

        // 3. Apply Display Name
        entity.customName(LegacyComponentSerializer.legacyAmpersand().deserialize(mobType.displayName()));
        entity.setCustomNameVisible(true);

        // 4. Apply Equipment
        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null) {
            mobType.equipment().forEach((slotKey, itemIdOrVanilla) -> {
                try {
                    EquipmentSlot slot = EquipmentSlot.valueOf(slotKey.toUpperCase());
                    ItemStack itemToEquip = null;
                    if (itemIdOrVanilla.toUpperCase().startsWith("VANILLA:")) {
                        Material mat = Material.matchMaterial(itemIdOrVanilla.substring("VANILLA:".length()));
                        if (mat != null) itemToEquip = new ItemStack(mat);
                        else logging.warn("Invalid VANILLA material '" + itemIdOrVanilla + "' for equipment on mob " + typeId);
                    } else {
                        itemToEquip = itemService.createItemStack(itemIdOrVanilla, 1);
                        if (itemToEquip.getType() == Material.BARRIER) { // ItemService returns barrier on error
                            logging.warn("Failed to create custom item '" + itemIdOrVanilla + "' for equipment on mob " + typeId);
                            itemToEquip = null;
                        }
                    }
                    if (itemToEquip != null && !itemToEquip.getType().isAir()) {
                        equipment.setItem(slot, itemToEquip);
                        equipment.setDropChance(slot, 0.0f); // Custom mobs typically don't drop their starter gear unless in loot table
                    }
                } catch (IllegalArgumentException e) {
                    logging.warn("Invalid equipment slot key '" + slotKey + "' for custom mob " + typeId);
                }
            });
        }

        // 5. Apply Generic NBT (Requires careful implementation, often NMS or specific Bukkit API)
        mobType.genericNbtData().forEach((key, value) -> {
            // Example for common boolean flags:
            if ("Silent".equalsIgnoreCase(key) && value instanceof Boolean val) entity.setSilent(val);
            if ("IsBaby".equalsIgnoreCase(key) && value instanceof Boolean val && entity instanceof Ageable ageable) {
                if (val) ageable.setBaby(); else ageable.setAdult();
            }
            // More complex NBT requires a robust NBT manipulation library or NMS.
        });

        // 6. Attach AIController (if using custom runnable AI system)
        // Purpur Goal system would involve adding PathfinderGoals here.
        if (!mobType.aiBehaviorIds().isEmpty() && !activeAIControllers.containsKey(entity.getUniqueId())) {
            // If using Purpur Goals, you would clear default goals and add custom ones here.
            // Example for custom AIController:
            AIController controller = new AIController(entity, mobType, core, this.defaultAIControllerTickRate);
            activeAIControllers.put(entity.getUniqueId(), controller);
            controller.start();
        }

        if (postSpawnModifier != null) {
            postSpawnModifier.accept(entity);
        }

        if (logging.isInfoEnabled()) logging.info("Spawned custom mob: " + mobType.typeId() + " (" + entity.getType() + " - " + entity.getUniqueId() + ") at " + location.toVector());
        return entity;
    }

    public void handleEntityDespawnOrDeath(LivingEntity entity) {
        if (entity == null) return;
        UUID uuid = entity.getUniqueId();
        AIController controller = activeAIControllers.remove(uuid);
        if (controller != null) {
            controller.stop();
            if (logging.isDebugMode()) logging.debug("Stopped and removed AIController for despawned/dead entity: " + uuid);
        }
        // EntityStatsService.clearStatsFromEntity() is called by its own listener (CustomMobLifecycleListener calls it)
    }

    @Nullable
    public LivingEntity attemptReplaceVanillaSpawn(@NotNull LivingEntity vanillaEntity, @NotNull CreatureSpawnEvent.SpawnReason reason) {
        // Don't replace plugin-spawned entities, spawns from eggs, or already custom mobs.
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM || reason == CreatureSpawnEvent.SpawnReason.COMMAND ||
                reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG || reason == CreatureSpawnEvent.SpawnReason.EGG ||
                NBTService.has(vanillaEntity.getPersistentDataContainer(), NBTService.CUSTOM_MOB_TYPE_ID_KEY, PersistentDataType.STRING)) {
            return null;
        }

        List<CustomMobType> candidates = new ArrayList<>();
        for (CustomMobType type : customMobRegistry.values()) {
            if (type.replaceVanillaSpawns() && type.baseEntityType() == vanillaEntity.getType()) {
                if (!type.spawnBiomes().isEmpty()) {
                    Biome currentBiome = vanillaEntity.getLocation().getBlock().getBiome();
                    if (!type.spawnBiomes().contains(currentBiome.name().toUpperCase())) continue;
                }
                byte lightLevel = vanillaEntity.getLocation().getBlock().getLightLevel();
                if ((type.minSpawnLightLevel() != null && lightLevel < type.minSpawnLightLevel()) ||
                        (type.maxSpawnLightLevel() != null && lightLevel > type.maxSpawnLightLevel())) continue;

                int yLevel = vanillaEntity.getLocation().getBlockY();
                if ((type.minSpawnY() != null && yLevel < type.minSpawnY()) ||
                        (type.maxSpawnY() != null && yLevel > type.maxSpawnY())) continue;

                if (type.requiredWorld() != null && !type.requiredWorld().equalsIgnoreCase(vanillaEntity.getWorld().getName())) continue;

                if (random.nextDouble() < type.replaceChance()){
                    candidates.add(type);
                }
            }
        }

        if (candidates.isEmpty()) return null;

        CustomMobType chosenType = selectWeightedRandom(candidates);
        if (chosenType != null) {
            if (logging.isDebugMode()) logging.debug("Attempting to replace vanilla " + vanillaEntity.getType() + " with custom " + chosenType.typeId() + " at " + vanillaEntity.getLocation().toVector());
            // The actual spawning happens after the event is cancelled by the listener
            // The listener will call spawnCustomMob. Here we just return the type to spawn.
            // For now, let's make CustomMobService do the spawn if a type is chosen.
            // The listener will then cancel the original event.
            return spawnCustomMob(chosenType.typeId(), vanillaEntity.getLocation(), null);
        }
        return null;
    }

    private CustomMobType selectWeightedRandom(List<CustomMobType> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        int totalWeight = candidates.stream().mapToInt(CustomMobType::spawnWeight).sum();
        if (totalWeight <= 0) { // Fallback if no positive weights, pick uniformly
            return candidates.get(random.nextInt(candidates.size()));
        }
        int randomNumber = random.nextInt(totalWeight);
        int cumulativeWeight = 0;
        for (CustomMobType candidate : candidates) {
            cumulativeWeight += candidate.spawnWeight();
            if (randomNumber < cumulativeWeight) {
                return candidate;
            }
        }
        // Should be unreachable if totalWeight > 0 and candidates is not empty.
        // Return last as a fallback.
        return candidates.get(candidates.size() - 1);
    }
}