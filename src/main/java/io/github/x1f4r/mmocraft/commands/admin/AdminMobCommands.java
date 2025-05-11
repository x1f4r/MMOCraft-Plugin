package io.github.x1f4r.mmocraft.commands.admin;

import io.github.x1f4r.mmocraft.commands.AbstractMMOCommand;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.entities.CustomMobType;
import io.github.x1f4r.mmocraft.services.CustomMobService;
import io.github.x1f4r.mmocraft.services.NBTService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class AdminMobCommands extends AbstractMMOCommand {

    public AdminMobCommands(MMOCore core, String commandLabelForUsage) {
        super(core, commandLabelForUsage, null);

        CustomMobService customMobService = core.getService(CustomMobService.class);

        // Subcommand: spawn <mobTypeId> [amount] [x y z world|playerName]
        addSubCommand("spawn", "mmocraft.admin.mob.spawn", 1, "<mobTypeID> [amount] [x y z world | @p | playerName]",
                (sender, args) -> {
                    String mobTypeId = args[0];
                    int amount = (args.length > 1) ? parseInt(args[1], "amount") : 1;
                    if (amount < 1) amount = 1;
                    if (amount > 50) amount = 50; // Safety cap

                    Location spawnLocation = null;
                    World world = null;

                    if (args.length > 2) {
                        if (args.length >= 5 && !args[2].startsWith("@")) { // x y z [world]
                            double x = parseDouble(args[2], "x_coord");
                            double y = parseDouble(args[3], "y_coord");
                            double z = parseDouble(args[4], "z_coord");
                            world = (args.length > 5) ? Bukkit.getWorld(args[5]) : (sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().get(0));
                            if (world == null) throw new CommandArgumentException("Invalid world name or no world specified from console.");
                            spawnLocation = new Location(world, x, y, z);
                        } else { // Player name or @p
                            Player targetForLocation = null;
                            if (args[2].equalsIgnoreCase("@p") && sender instanceof Player p) {
                                targetForLocation = p;
                            } else if (!args[2].startsWith("@")) {
                                targetForLocation = parsePlayer(sender, args[2], "targetPlayer");
                            }
                            if (targetForLocation != null) {
                                spawnLocation = targetForLocation.getLocation();
                            } else if (sender instanceof Player p) {
                                spawnLocation = p.getLocation(); // Default to sender's location if player
                            } else {
                                throw new CommandArgumentException("Must specify coordinates or a player for spawn location from console.");
                            }
                        }
                    } else if (sender instanceof Player p) {
                        spawnLocation = p.getLocation();
                    } else {
                        throw new CommandArgumentException("Must specify coordinates or a player for spawn location from console.");
                    }

                    int spawnedCount = 0;
                    for (int i = 0; i < amount; i++) {
                        LivingEntity spawned = customMobService.spawnCustomMob(mobTypeId, spawnLocation, null);
                        if (spawned != null) spawnedCount++;
                        else {
                            sender.sendMessage(Component.text("Failed to spawn custom mob with ID '" + mobTypeId + "'. Is it defined in custom_mobs.yml?", NamedTextColor.RED));
                            break; // Stop if one fails
                        }
                    }
                    if (spawnedCount > 0) {
                        sender.sendMessage(Component.text("Spawned " + spawnedCount + "x " + mobTypeId + " at " +
                                String.format("%.1f, %.1f, %.1f", spawnLocation.getX(), spawnLocation.getY(), spawnLocation.getZ()) +
                                " in " + spawnLocation.getWorld().getName(), NamedTextColor.GREEN));
                    }
                },
                (sender, args) -> { // Tab completer for "spawn"
                    if (args.length == 1) return tabCompleteFromList(new ArrayList<>(customMobService.getAllCustomMobTypeIds()), args[0]);
                    if (args.length == 2) return List.of("1", "5", "10"); // Amount
                    if (args.length == 3) { // x, @p, or playername
                        List<String> suggestions = new ArrayList<>(tabCompletePlayerNames(args[2]));
                        if (sender instanceof Player p) {
                            suggestions.add(String.valueOf(p.getLocation().getBlockX()));
                            suggestions.add("@p");
                        }
                        return tabCompleteFromList(suggestions, args[2]);
                    }
                    if (args.length == 4 && sender instanceof Player p) return List.of(String.valueOf(p.getLocation().getBlockY())); // y
                    if (args.length == 5 && sender instanceof Player p) return List.of(String.valueOf(p.getLocation().getBlockZ())); // z
                    if (args.length == 6) return Bukkit.getWorlds().stream().map(World::getName).filter(name -> name.toLowerCase().startsWith(args[5].toLowerCase())).toList(); // world
                    return List.of();
                });

        // Subcommand: list
        addSubCommand("list", "mmocraft.admin.mob.list", 0, "",
                (sender, args) -> {
                    Set<String> mobTypeIds = customMobService.getAllCustomMobTypeIds();
                    if (mobTypeIds.isEmpty()) {
                        sender.sendMessage(Component.text("No custom mob types are currently defined.", NamedTextColor.YELLOW));
                        return;
                    }
                    sender.sendMessage(Component.text("Available Custom Mob Type IDs (" + mobTypeIds.size() + "):", NamedTextColor.GOLD));
                    String joinedIds = String.join(", ", new ArrayList<>(mobTypeIds).stream().sorted().toList());
                    sender.sendMessage(Component.text(joinedIds, NamedTextColor.YELLOW));
                },
                null
        );

        // Subcommand: killall <mobTypeId|ALL_MMO> [radius] [worldName]
        addSubCommand("killall", "mmocraft.admin.mob.killall", 1, "<mobTypeID|ALL_MMO> [radius] [world]",
                (sender, args) -> {
                    String targetType = args[0].toLowerCase();
                    double radius = -1; // -1 means global in specified world
                    World world = null;

                    if (args.length > 1) {
                        try {
                            radius = parseDouble(args[1], "radius");
                            if (args.length > 2) world = Bukkit.getWorld(args[2]);
                        } catch (CommandArgumentException e) { // If arg 2 is not a number, it might be a world name
                            world = Bukkit.getWorld(args[1]);
                            if (world == null) throw new CommandArgumentException("Invalid radius or world name: " + args[1]);
                            // If radius was intended, it would have parsed. No radius arg if this path.
                        }
                    }

                    Location center = null;
                    if (radius > 0 && sender instanceof Player p) {
                        center = p.getLocation();
                        if (world == null) world = p.getWorld(); // Default to sender's world if radius specified by player
                    } else if (radius > 0) {
                        throw new CommandArgumentException("Radius for killall can only be used when command is run by a player (for center location).");
                    }

                    if (world == null && sender instanceof Player p) world = p.getWorld();
                    if (world == null && Bukkit.getWorlds().size() == 1) world = Bukkit.getWorlds().get(0); // Default to main world if only one
                    if (world == null && args.length <= (radius > 0 ? 2 : 1)) { // No world specified and multiple exist
                        throw new CommandArgumentException("Multiple worlds exist. Please specify a world name for global killall or run as player for radius.");
                    }


                    int killedCount = 0;
                    List<World> worldsToSearch = (world != null) ? List.of(world) : Bukkit.getWorlds();

                    for (World w : worldsToSearch) {
                        for (LivingEntity entity : w.getLivingEntities()) {
                            if (entity instanceof Player) continue; // Don't kill players

                            boolean shouldKill = false;
                            if (targetType.equals("all_mmo")) {
                                if (NBTService.has(entity.getPersistentDataContainer(), NBTService.CUSTOM_MOB_TYPE_ID_KEY, PersistentDataType.STRING)) {
                                    shouldKill = true;
                                }
                            } else {
                                String mobIdNBT = NBTService.get(entity.getPersistentDataContainer(), NBTService.CUSTOM_MOB_TYPE_ID_KEY, PersistentDataType.STRING, null);
                                if (targetType.equals(mobIdNBT)) {
                                    shouldKill = true;
                                }
                            }

                            if (shouldKill) {
                                if (radius > 0 && center != null && entity.getWorld().equals(center.getWorld())) {
                                    if (entity.getLocation().distanceSquared(center) <= radius * radius) {
                                        entity.remove(); // Or entity.setHealth(0) to trigger death events
                                        killedCount++;
                                    }
                                } else if (radius <= 0) { // Global in this world (or all worlds if world was null)
                                    entity.remove();
                                    killedCount++;
                                }
                            }
                        }
                    }
                    sender.sendMessage(Component.text("Removed " + killedCount + " matching MMOCraft mobs.", NamedTextColor.GREEN));
                },
                (sender, args) -> {
                    if (args.length == 1) {
                        List<String> suggestions = new ArrayList<>(customMobService.getAllCustomMobTypeIds());
                        suggestions.add("ALL_MMO");
                        return tabCompleteFromList(suggestions, args[0]);
                    }
                    if (args.length == 2) return List.of("5", "10", "20", "50"); // Radius or world names
                    if (args.length == 3 && args[1].matches("\\d+(\\.\\d+)?")) { // If arg 2 was a radius number
                        return Bukkit.getWorlds().stream().map(World::getName).filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase())).toList();
                    }
                    return List.of();
                });

    }

    @Override
    protected boolean handleBaseCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        sendHelpMessage(sender, label);
        return true;
    }
}