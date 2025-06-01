package com.x1f4r.mmocraft.world.resourcegathering.listeners;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.item.service.CustomItemRegistry; // For potential custom tool interactions
import com.x1f4r.mmocraft.loot.service.LootService;
import com.x1f4r.mmocraft.playerdata.PlayerDataService; // For player stats/skills affecting gathering
import com.x1f4r.mmocraft.util.LoggingUtil;
import com.x1f4r.mmocraft.util.StringUtil;
import com.x1f4r.mmocraft.world.resourcegathering.model.ActiveResourceNode;
import com.x1f4r.mmocraft.world.resourcegathering.model.ResourceNodeType;
import com.x1f4r.mmocraft.world.resourcegathering.service.ActiveNodeManager;
import com.x1f4r.mmocraft.world.resourcegathering.service.ResourceNodeRegistryService;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public class ResourceNodeInteractionListener implements Listener {

    private final MMOCraftPlugin plugin;
    private final ActiveNodeManager activeNodeManager;
    private final ResourceNodeRegistryService nodeRegistryService;
    private final LootService lootService;
    private final CustomItemRegistry customItemRegistry; // Currently unused, for future expansion
    private final PlayerDataService playerDataService;   // Currently unused, for future expansion
    private final LoggingUtil logger;

    public ResourceNodeInteractionListener(MMOCraftPlugin plugin, ActiveNodeManager activeNodeManager,
                                           ResourceNodeRegistryService nodeRegistryService, LootService lootService,
                                           CustomItemRegistry customItemRegistry, PlayerDataService playerDataService,
                                           LoggingUtil logger) {
        this.plugin = plugin;
        this.activeNodeManager = activeNodeManager;
        this.nodeRegistryService = nodeRegistryService;
        this.lootService = lootService;
        this.customItemRegistry = customItemRegistry;
        this.playerDataService = playerDataService;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location blockLocation = event.getBlock().getLocation();

        Optional<ActiveResourceNode> activeNodeOpt = activeNodeManager.getActiveNode(blockLocation);

        if (activeNodeOpt.isEmpty()) {
            // Not a custom resource node, let vanilla handling take over or other plugins.
            return;
        }

        // It's a custom resource node, so we take control.
        event.setCancelled(true); // Prevent vanilla block breaking behavior.
        // Set drop to 0 to ensure no vanilla drops if not cancelled fully or by other plugins.
        event.setDropItems(false);


        ActiveResourceNode activeNode = activeNodeOpt.get();

        if (activeNode.isDepleted()) {
            player.sendMessage(StringUtil.colorize("&cThis resource node is currently depleted."));
            return;
        }

        Optional<ResourceNodeType> nodeTypeOpt = nodeRegistryService.getNodeType(activeNode.getNodeTypeId());
        if (nodeTypeOpt.isEmpty()) {
            logger.severe("ActiveResourceNode at " + blockLocation + " has unknown ResourceNodeTypeId: " + activeNode.getNodeTypeId());
            player.sendMessage(StringUtil.colorize("&cError: Resource node type unknown. Please report to an admin."));
            return;
        }

        ResourceNodeType nodeType = nodeTypeOpt.get();

        // 1. Check Tool Requirements
        if (!nodeType.getRequiredToolTypes().isEmpty()) {
            ItemStack toolInHand = player.getInventory().getItemInMainHand();
            if (toolInHand == null || !nodeType.getRequiredToolTypes().contains(toolInHand.getType())) {
                // TODO: Make this message more descriptive (e.g., list required tools)
                player.sendMessage(StringUtil.colorize("&cYou do not have the required tool to gather from this node. (" + toolInHand.getType() + ")"));
                return;
            }
        }

        // TODO: 2. Implement Break Time / Interaction Logic
        // For now, assume instant break if tool is correct.
        // Future: This might involve initiating a "breaking" state, tracking progress,
        // and then calling depleteNode and loot distribution upon completion.
        // This could be managed by a new service or within ActiveNodeManager.

        // 3. Distribute Loot
        lootService.getLootTableById(nodeType.getLootTableId()).ifPresentOrElse(lootTable -> {
            // Generate loot and give it to the player or drop it at the node's location.
            // For simplicity, let's drop at player's location for now.
            lootTable.generateLoot(customItemRegistry, plugin).forEach(itemStack -> { // Corrected
                if (itemStack != null && itemStack.getAmount() > 0) {
                    player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
                }
            });
            player.sendMessage(StringUtil.colorize("&aYou successfully gathered resources from &e" + nodeType.getEffectiveName() + "&a!"));
            logger.debug("Player " + player.getName() + " gathered from node " + nodeType.getTypeId() + " at " + blockLocation);

            // 4. Deplete Node
            activeNodeManager.depleteNode(activeNode);

        }, () -> {
            logger.warning("LootTableId '" + nodeType.getLootTableId() + "' not found for ResourceNodeType '" + nodeType.getTypeId() + "'. No loot dropped.");
            player.sendMessage(StringUtil.colorize("&cNo loot configured for this node. Please report to an admin."));
            // Still deplete the node even if loot is missing, to prevent farming an empty node.
             activeNodeManager.depleteNode(activeNode);
        });
    }
}
