package com.x1f4r.mmocraft.world.spawning.service;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.combat.service.MobStatProvider; // For applying stats
import com.x1f4r.mmocraft.item.service.CustomItemRegistry; // For equipment
import com.x1f4r.mmocraft.loot.service.LootService; // For assigning loot tables (metadata)
import com.x1f4r.mmocraft.util.LoggingUtil;
import com.x1f4r.mmocraft.util.StringUtil;
import com.x1f4r.mmocraft.world.spawning.model.CustomSpawnRule;
import com.x1f4r.mmocraft.world.spawning.model.MobSpawnDefinition;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance; // Added this line
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue; // For custom mob data

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class BasicCustomSpawningService implements CustomSpawningService {

    private final MMOCraftPlugin plugin;
    private final LoggingUtil logger;
    private final MobStatProvider mobStatProvider;
    private final LootService lootService; // To link mob to loot table via metadata
    private final CustomItemRegistry customItemRegistry; // To create equipment

    private final List<CustomSpawnRule> spawnRules = new CopyOnWriteArrayList<>();

    public static final String METADATA_KEY_CUSTOM_MOB_ID = "MMOCRAFT_CUSTOM_MOB_ID";
    public static final String METADATA_KEY_LOOT_TABLE_ID = "MMOCRAFT_LOOT_TABLE_ID";


    public BasicCustomSpawningService(MMOCraftPlugin plugin, LoggingUtil logger, MobStatProvider mobStatProvider,
                                      LootService lootService, CustomItemRegistry customItemRegistry) {
        this.plugin = plugin;
        this.logger = logger;
        this.mobStatProvider = mobStatProvider;
        this.lootService = lootService;
        this.customItemRegistry = customItemRegistry;
        logger.debug("BasicCustomSpawningService initialized.");
    }

    @Override
    public void registerRule(CustomSpawnRule rule) {
        if (rule == null || rule.getRuleId() == null || rule.getRuleId().trim().isEmpty()) {
            logger.warning("Attempted to register null rule or rule with invalid ID.");
            return;
        }
        // Check for duplicate ruleId to prevent issues, or decide if replacement is allowed
        if (spawnRules.stream().anyMatch(r -> r.getRuleId().equals(rule.getRuleId()))) {
            logger.warning("Spawn rule with ID '" + rule.getRuleId() + "' already exists. Skipping registration.");
            return;
        }
        spawnRules.add(rule);
        logger.info("Registered custom spawn rule: " + rule.getRuleId() + " for mob type " + rule.getMobSpawnDefinition().getEntityType());
    }

    @Override
    public boolean unregisterRule(String ruleId) {
        if (ruleId == null) return false;
        boolean removed = spawnRules.removeIf(rule -> rule.getRuleId().equals(ruleId));
        if (removed) {
            logger.info("Unregistered custom spawn rule: " + ruleId);
        }
        return removed;
    }

    @Override
    public List<CustomSpawnRule> getAllRules() {
        return Collections.unmodifiableList(spawnRules);
    }

    @Override
    public void attemptSpawns() {
        long currentTick = Bukkit.getServer().getWorlds().get(0).getFullTime(); // Use a consistent world time
        logger.finest("Attempting custom spawns at tick: " + currentTick);

        for (World world : Bukkit.getServer().getWorlds()) {
            // Iterate players to find potential spawn areas around them (more targeted than iterating all chunks)
            for (Player player : world.getPlayers()) {
                if (player.isDead() || !player.isValid()) continue;

                Location playerLoc = player.getLocation();
                // Define a radius around player to attempt spawns, e.g., 32-128 blocks
                int spawnAttemptRadius = 64; // Example
                int attemptsPerPlayer = 3; // How many random spots to try around a player

                for (int i = 0; i < attemptsPerPlayer; i++) {
                    // Get a random location within a hollow sphere (e.g., 24-64 blocks away)
                    double angle = Math.random() * Math.PI * 2;
                    double distance = 24 + (Math.random() * (spawnAttemptRadius - 24));
                    int x = (int) (playerLoc.getX() + Math.cos(angle) * distance);
                    int z = (int) (playerLoc.getZ() + Math.sin(angle) * distance);

                    // Try to find a safe Y level (surface)
                    Location potentialSpawnLoc = new Location(world, x, 0, z); // Y will be adjusted
                    potentialSpawnLoc.setY(world.getHighestBlockYAt(x, z) + 1);

                    // Check if chunk is loaded (important!)
                    if (!world.isChunkLoaded(potentialSpawnLoc.getBlockX() >> 4, potentialSpawnLoc.getBlockZ() >> 4)) {
                        continue;
                    }

                    processRulesForLocation(potentialSpawnLoc, world, player, currentTick);
                }
            }
        }
        // Placeholder: Actual spawning logic is complex and involves iterating chunks,
        // finding valid spawn locations (surface, caves, specific blocks), checking light levels, etc.
        // logger.finer("Custom spawning tick completed. This is currently a placeholder.");
    }

    private void processRulesForLocation(Location loc, World world, Player nearestPlayer, long currentTick) {
        for (CustomSpawnRule rule : spawnRules) {
            if (!rule.isReadyToAttemptSpawn(currentTick)) {
                // logger.finest("Rule " + rule.getRuleId() + " not ready for attempt (interval).");
                continue;
            }

            if (!rule.conditionsMet(loc, world, nearestPlayer)) {
                // logger.finest("Rule " + rule.getRuleId() + " conditions not met at " + loc.toVector());
                continue;
            }

            // Check maxNearbyEntities
            long nearbyCount = world.getNearbyEntities(loc, rule.getSpawnRadiusCheck(), rule.getSpawnRadiusCheck(), rule.getSpawnRadiusCheck(),
                entity -> entity.getType() == rule.getMobSpawnDefinition().getEntityType() && !entity.isDead()
            ).size();

            if (nearbyCount >= rule.getMaxNearbyEntities()) {
                // logger.finest("Rule " + rule.getRuleId() + ": too many nearby entities (" + nearbyCount + "/" + rule.getMaxNearbyEntities() + ")");
                continue;
            }

            if (rule.rollForSpawn()) {
                spawnMob(loc, rule.getMobSpawnDefinition());
                rule.setLastSpawnAttemptTickGlobal(currentTick); // Update last attempt time for this rule
                logger.fine("Successfully spawned " + rule.getMobSpawnDefinition().getDefinitionId() + " via rule " + rule.getRuleId() + " at " + loc.toVector());
                // Potentially break here if only one mob should spawn per chosen spot, or continue for more rules.
                break;
            }
        }
    }

    private void spawnMob(Location location, MobSpawnDefinition definition) {
        World world = location.getWorld();
        if (world == null) return;

        Entity spawnedEntity = world.spawnEntity(location, definition.getEntityType());
        if (!(spawnedEntity instanceof LivingEntity livingEntity)) {
            logger.warning("Spawned entity for " + definition.getDefinitionId() + " is not a LivingEntity. Removing.");
            spawnedEntity.remove();
            return;
        }

        // Apply custom name
        definition.getDisplayName().ifPresent(name -> {
            livingEntity.setCustomName(StringUtil.colorize(name));
            livingEntity.setCustomNameVisible(true);
        });

        // Apply base stats from MobStatProvider (Bukkit attributes)
        double baseHealth = mobStatProvider.getBaseHealth(definition.getEntityType()); // Using EntityType for base stats
        AttributeInstance healthAttr = livingEntity.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(baseHealth);
            livingEntity.setHealth(baseHealth); // Set current health to max
        }

        double baseAttack = mobStatProvider.getBaseAttackDamage(definition.getEntityType());
        AttributeInstance attackAttr = livingEntity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.setBaseValue(baseAttack);
        }

        // Note: Bukkit has no generic "DEFENSE" attribute. Defense is handled via armor or custom calculations.
        // We can store our custom defense value as metadata if needed for our damage calculation.

        // Apply equipment
        EntityEquipment equipment = livingEntity.getEquipment();
        if (equipment != null && definition.getEquipment() != null) {
            definition.getEquipment().forEach((slot, itemStack) -> {
                if (itemStack != null && !itemStack.getType().isAir()) {
                    equipment.setItem(slot, itemStack.clone()); // Clone to be safe
                    // Ensure drop chances are set to 0 if desired for mob equipment
                    equipment.setDropChance(slot, 0.0f);
                }
            });
        }

        // Store MMOCraft specific metadata
        livingEntity.setMetadata(METADATA_KEY_CUSTOM_MOB_ID, new FixedMetadataValue(plugin, definition.getDefinitionId()));
        definition.getLootTableId().ifPresent(lootId -> {
            livingEntity.setMetadata(METADATA_KEY_LOOT_TABLE_ID, new FixedMetadataValue(plugin, lootId));
        });
        // Could also store mobStatKey if it's different from EntityType.name()

        logger.fine("Custom mob '" + definition.getDefinitionId() + "' spawned at " + location.toVector());
    }

    @Override
    public void shutdown() {
        logger.info("BasicCustomSpawningService shutting down. (No specific cleanup actions implemented yet)");
        // If there were persistent tasks or caches related to spawning locations that need cleanup, do it here.
    }
}
