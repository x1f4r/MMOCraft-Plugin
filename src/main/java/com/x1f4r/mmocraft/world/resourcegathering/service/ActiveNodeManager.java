package com.x1f4r.mmocraft.world.resourcegathering.service;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.item.service.CustomItemRegistry; // Not directly used in this impl, but good for context
import com.x1f4r.mmocraft.loot.service.LootService;       // Not directly used in this impl, but good for context
import com.x1f4r.mmocraft.util.LoggingUtil;
import com.x1f4r.mmocraft.world.resourcegathering.model.ActiveResourceNode;
import com.x1f4r.mmocraft.world.resourcegathering.model.ResourceNodeType;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack; // If we drop items directly, not via LootService here

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveNodeManager {

    private final MMOCraftPlugin plugin;
    private final LoggingUtil logger;
    private final ResourceNodeRegistryService nodeRegistryService;
    // private final LootService lootService; // Needed for dropping items if not handled by listener
    // private final CustomItemRegistry customItemRegistry; // Same as above

    private final Map<Location, ActiveResourceNode> activeNodes = new ConcurrentHashMap<>();
    private final Material depletedMaterial = Material.BEDROCK; // Or COBBLESTONE, STONE etc.

    public ActiveNodeManager(MMOCraftPlugin plugin, LoggingUtil logger,
                             ResourceNodeRegistryService nodeRegistryService,
                             LootService lootService, CustomItemRegistry customItemRegistry) {
        this.plugin = plugin;
        this.logger = logger;
        this.nodeRegistryService = nodeRegistryService;
        // this.lootService = lootService;
        // this.customItemRegistry = customItemRegistry;
    }

    public void placeNewNode(Location location, String nodeTypeId) {
        if (location == null || location.getWorld() == null) {
            logger.warning("Attempted to place new node at null location or world.");
            return;
        }
        Optional<ResourceNodeType> nodeTypeOpt = nodeRegistryService.getNodeType(nodeTypeId);
        if (nodeTypeOpt.isEmpty()) {
            logger.warning("Attempted to place new node with unknown typeId: " + nodeTypeId);
            return;
        }
        ResourceNodeType nodeType = nodeTypeOpt.get();

        // Ensure location is block-snapped for consistent map keys
        Location blockLocation = location.getBlock().getLocation();

        if (activeNodes.containsKey(blockLocation)) {
            logger.warning("Node already exists at " + blockLocation + ". Cannot place new node: " + nodeTypeId);
            return;
        }

        ActiveResourceNode newNode = new ActiveResourceNode(blockLocation, nodeTypeId);
        activeNodes.put(blockLocation, newNode);

        // Set the block in the world
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Block block = blockLocation.getBlock();
            block.setType(nodeType.getDisplayMaterial());
            // TODO: Set custom block data if needed (e.g. for custom textures via resource pack)
            logger.info("Placed new resource node '" + nodeTypeId + "' at " + blockLocationToString(blockLocation));
        });
    }

    public Optional<ActiveResourceNode> getActiveNode(Location location) {
        if (location == null) return Optional.empty();
        return Optional.ofNullable(activeNodes.get(location.getBlock().getLocation()));
    }

    public void depleteNode(ActiveResourceNode node) {
        if (node == null || node.isDepleted()) {
            return;
        }

        Optional<ResourceNodeType> nodeTypeOpt = nodeRegistryService.getNodeType(node.getNodeTypeId());
        if (nodeTypeOpt.isEmpty()) {
            logger.severe("Cannot deplete node: Unknown NodeType ID '" + node.getNodeTypeId() + "' for node at " + blockLocationToString(node.getLocation()));
            return;
        }
        ResourceNodeType nodeType = nodeTypeOpt.get();

        node.setDepleted(true);
        node.setRespawnAtMillis(System.currentTimeMillis() + (nodeType.getRespawnTimeSeconds() * 1000L));

        // Change block in world to depleted state
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Block block = node.getLocation().getBlock();
            block.setType(depletedMaterial);
            logger.debug("Depleted node '" + node.getNodeTypeId() + "' at " + blockLocationToString(node.getLocation()) + ". Respawning in " + nodeType.getRespawnTimeSeconds() + "s.");
        });
    }

    public void respawnNode(ActiveResourceNode node) {
        if (node == null || !node.isDepleted()) {
            return;
        }

        Optional<ResourceNodeType> nodeTypeOpt = nodeRegistryService.getNodeType(node.getNodeTypeId());
        if (nodeTypeOpt.isEmpty()) {
            logger.severe("Cannot respawn node: Unknown NodeType ID '" + node.getNodeTypeId() + "' for node at " + blockLocationToString(node.getLocation()));
            // Potentially remove it from activeNodes if type is gone? Or leave it as depleted forever.
            return;
        }
        ResourceNodeType nodeType = nodeTypeOpt.get();

        node.setDepleted(false);
        node.setRespawnAtMillis(0);

        // Change block back to display material
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Block block = node.getLocation().getBlock();
            block.setType(nodeType.getDisplayMaterial());
            logger.debug("Respawned node '" + node.getNodeTypeId() + "' at " + blockLocationToString(node.getLocation()));
        });
    }

    public void tickNodes() {
        long currentTimeMillis = System.currentTimeMillis();
        for (ActiveResourceNode node : activeNodes.values()) {
            if (node.isDepleted() && node.getRespawnAtMillis() <= currentTimeMillis) {
                respawnNode(node);
            }
        }
    }

    /**
     * Removes a node from tracking and attempts to set its block to air.
     * Useful for admin commands or if a node type is removed.
     * @param location The location of the node to remove.
     * @return true if a node was found and removed, false otherwise.
     */
    public boolean removeNode(Location location) {
        Location blockLocation = location.getBlock().getLocation();
        ActiveResourceNode node = activeNodes.remove(blockLocation);
        if (node != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                blockLocation.getBlock().setType(Material.AIR);
                logger.info("Removed resource node '" + node.getNodeTypeId() + "' from " + blockLocationToString(blockLocation));
            });
            return true;
        }
        return false;
    }

    public Map<Location, ActiveResourceNode> getAllActiveNodesView() {
        return Collections.unmodifiableMap(activeNodes);
    }

    private String blockLocationToString(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return String.format("%s@(%d,%d,%d)", loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public void shutdown() {
        // If this manager had its own scheduled tasks, cancel them here.
        // For now, tickNodes is called by an external scheduler in MMOCraftPlugin.
        logger.info("ActiveNodeManager shutting down. Persisting node states is not yet implemented.");
        // Potentially save all node states to disk here if persistence is desired.
    }
}
