package com.x1f4r.mmocraft.command.commands.admin;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.command.AbstractPluginCommand;
import com.x1f4r.mmocraft.util.LoggingUtil;
import com.x1f4r.mmocraft.util.StringUtil;
import com.x1f4r.mmocraft.world.resourcegathering.model.ResourceNodeType;
import com.x1f4r.mmocraft.world.resourcegathering.service.ActiveNodeManager;
import com.x1f4r.mmocraft.world.resourcegathering.service.ResourceNodeRegistryService;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ResourceAdminCommand extends AbstractPluginCommand {

    private final MMOCraftPlugin plugin;
    private final ActiveNodeManager activeNodeManager;
    private final ResourceNodeRegistryService nodeRegistryService;
    private final LoggingUtil logger;

    public ResourceAdminCommand(MMOCraftPlugin plugin, String commandName, String permission) {
        super(commandName, permission, "Manages resource node administration commands."); // Corrected
        this.plugin = plugin;
        this.activeNodeManager = plugin.getActiveNodeManager();
        this.nodeRegistryService = plugin.getResourceNodeRegistryService();
        this.logger = plugin.getLoggingUtil();

        // Define subcommands
        // /mmocadm resource place <nodeTypeId> [world x y z]
        // /mmocadm resource remove (looks at target block)
        // /mmocadm resource info (looks at target block)
    }

    @Override
    public boolean onCommand(CommandSender sender, String[] args) { // Renamed, made public
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "place":
                return handlePlaceCommand(sender, Arrays.copyOfRange(args, 1, args.length));
            case "remove":
                return handleRemoveCommand(sender, Arrays.copyOfRange(args, 1, args.length));
            case "info":
                return handleInfoCommand(sender, Arrays.copyOfRange(args, 1, args.length));
            default:
                sender.sendMessage(StringUtil.colorize("&cUnknown subcommand '" + subCommand + "'."));
                sendHelp(sender);
                return true;
        }
    }

    private boolean handlePlaceCommand(CommandSender sender, String[] args) {
        // <nodeTypeId> [world x y z]
        if (args.length < 1) {
            sender.sendMessage(StringUtil.colorize("&cUsage: /" + commandName + " place <nodeTypeId> [world x y z]"));
            return true;
        }

        String nodeTypeId = args[0];
        if (nodeRegistryService.getNodeType(nodeTypeId).isEmpty()) {
            sender.sendMessage(StringUtil.colorize("&cInvalid ResourceNodeType ID: '" + nodeTypeId + "'."));
            return true;
        }

        Location targetLocation;
        if (args.length >= 4) { // world x y z provided
            World world = Bukkit.getWorld(args[1]);
            if (world == null) {
                sender.sendMessage(StringUtil.colorize("&cWorld '" + args[1] + "' not found."));
                return true;
            }
            try {
                double x = Double.parseDouble(args[2]);
                double y = Double.parseDouble(args[3]);
                double z = Double.parseDouble(args[4]);
                targetLocation = new Location(world, x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage(StringUtil.colorize("&cInvalid coordinates."));
                return true;
            }
        } else if (sender instanceof Player) {
            Player player = (Player) sender;
            Block targetBlock = player.getTargetBlockExact(5); // Max distance 5 blocks
            if (targetBlock == null) {
                sender.sendMessage(StringUtil.colorize("&cYou are not looking at a block, or it's too far. Specify coordinates or look at a block."));
                return true;
            }
            targetLocation = targetBlock.getLocation();
        } else {
            sender.sendMessage(StringUtil.colorize("&cConsole must specify world and coordinates."));
            return true;
        }

        activeNodeManager.placeNewNode(targetLocation, nodeTypeId);
        sender.sendMessage(StringUtil.colorize("&aPlaced resource node '" + nodeTypeId + "' at " +
                targetLocation.getWorld().getName() + "(" + targetLocation.getBlockX() + ", " +
                targetLocation.getBlockY() + ", " + targetLocation.getBlockZ() + ")."));
        return true;
    }

    private boolean handleRemoveCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(StringUtil.colorize("&cThis command must be run by a player looking at a node."));
            return true;
        }
        Player player = (Player) sender;
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            sender.sendMessage(StringUtil.colorize("&cYou are not looking at a block, or it's too far."));
            return true;
        }

        Location loc = targetBlock.getLocation();
        Optional<com.x1f4r.mmocraft.world.resourcegathering.model.ActiveResourceNode> nodeOpt = activeNodeManager.getActiveNode(loc);

        if (nodeOpt.isEmpty()) {
            sender.sendMessage(StringUtil.colorize("&cNo active resource node found at your target block."));
            return true;
        }

        if (activeNodeManager.removeNode(loc)) {
            sender.sendMessage(StringUtil.colorize("&aSuccessfully removed resource node '" + nodeOpt.get().getNodeTypeId() + "' at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()));
        } else {
            // Should not happen if nodeOpt was present, but as a fallback
            sender.sendMessage(StringUtil.colorize("&cFailed to remove node. Check console for errors."));
        }
        return true;
    }

    private boolean handleInfoCommand(CommandSender sender, String[] args) {
         if (!(sender instanceof Player)) {
            sender.sendMessage(StringUtil.colorize("&cThis command must be run by a player looking at a node."));
            return true;
        }
        Player player = (Player) sender;
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            sender.sendMessage(StringUtil.colorize("&cYou are not looking at a block, or it's too far."));
            return true;
        }

        Location loc = targetBlock.getLocation();
        Optional<com.x1f4r.mmocraft.world.resourcegathering.model.ActiveResourceNode> nodeOpt = activeNodeManager.getActiveNode(loc);

        if (nodeOpt.isEmpty()) {
            sender.sendMessage(StringUtil.colorize("&cNo active resource node found at your target block."));
            return true;
        }

        com.x1f4r.mmocraft.world.resourcegathering.model.ActiveResourceNode activeNode = nodeOpt.get();
        Optional<ResourceNodeType> typeOpt = nodeRegistryService.getNodeType(activeNode.getNodeTypeId());

        sender.sendMessage(StringUtil.colorize("&6--- Resource Node Info ---"));
        sender.sendMessage(StringUtil.colorize("&eLocation: &f" + loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")"));
        sender.sendMessage(StringUtil.colorize("&eNode Type ID: &f" + activeNode.getNodeTypeId()));
        typeOpt.ifPresent(type -> sender.sendMessage(StringUtil.colorize("&eDisplay Name: &f" + StringUtil.colorize(type.getEffectiveName()))));
        sender.sendMessage(StringUtil.colorize("&eIs Depleted: &f" + activeNode.isDepleted()));
        if (activeNode.isDepleted()) {
            long remaining = (activeNode.getRespawnAtMillis() - System.currentTimeMillis()) / 1000;
            sender.sendMessage(StringUtil.colorize("&eRespawn In: &f" + Math.max(0, remaining) + " seconds"));
        }
        typeOpt.ifPresent(type -> {
            sender.sendMessage(StringUtil.colorize("&eBase Break Time: &f" + type.getBreakTimeSeconds() + "s"));
            sender.sendMessage(StringUtil.colorize("&eRespawn Time: &f" + type.getRespawnTimeSeconds() + "s"));
            sender.sendMessage(StringUtil.colorize("&eLoot Table ID: &f" + type.getLootTableId()));
            sender.sendMessage(StringUtil.colorize("&eRequired Tools: &f" + type.getRequiredToolTypes().stream().map(Enum::name).collect(Collectors.joining(", "))));
        });
        return true;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) { // Renamed, made public
        if (args.length == 1) {
            return List.of("place", "remove", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length > 1 && "place".equalsIgnoreCase(args[0])) {
            if (args.length == 2) { // Suggest nodeTypeId
                return nodeRegistryService.getAllNodeTypes().stream()
                        .map(ResourceNodeType::getTypeId)
                        .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 3) { // Suggest world names
                 return Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            // No suggestions for x y z for now
        }
        return Collections.emptyList();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(StringUtil.colorize("&6--- Resource Admin Commands ---"));
        sender.sendMessage(StringUtil.colorize("&e/" + commandName + " place <nodeTypeId> [world x y z] &7- Places a node. Uses target block if no coords."));
        sender.sendMessage(StringUtil.colorize("&e/" + commandName + " remove &7- Removes node at target block."));
        sender.sendMessage(StringUtil.colorize("&e/" + commandName + " info &7- Shows info about node at target block."));
    }
}
