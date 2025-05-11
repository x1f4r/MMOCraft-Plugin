package io.github.x1f4r.mmocraft.commands.admin;

import io.github.x1f4r.mmocraft.commands.AbstractMMOCommand;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.player.PlayerProfile;
import io.github.x1f4r.mmocraft.player.stats.CalculatedPlayerStats;
import io.github.x1f4r.mmocraft.services.PlayerDataService;
import io.github.x1f4r.mmocraft.services.PlayerResourceService;
import io.github.x1f4r.mmocraft.services.PlayerStatsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Field; // For stat name iteration (advanced, optional)
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AdminPlayerCommands extends AbstractMMOCommand {

    public AdminPlayerCommands(MMOCore core, String commandLabelForUsage) {
        super(core, commandLabelForUsage, null);

        List<String> statNames = getSettableStatNames(); // Get stat names for tab completion

        // Subcommand: stats set <playerName> <statName> <value>
        addSubCommand("stats_set", "mmocraft.admin.player.stats.set", 3, "<playerName> <statName> <value>",
                (sender, args) -> {
                    Player target = parsePlayer(sender, args[0], "playerName");
                    String statName = args[1].toLowerCase();
                    int value = parseInt(args[2], "value");

                    PlayerDataService pds = core.getService(PlayerDataService.class);
                    PlayerProfile profile = pds.getProfile(target); // Ensures profile is loaded

                    boolean success = updatePlayerBaseStat(profile, statName, value, false);
                    if (success) {
                        pds.savePlayerProfile(target, profile); // Save changes to PDC
                        core.getService(PlayerStatsService.class).scheduleStatsUpdate(target); // Recalculate and apply
                        sender.sendMessage(Component.text("Set base stat '" + statName + "' to " + value + " for " + target.getName(), NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text("Unknown base stat: '" + statName + "'. Valid: " + String.join(", ", statNames), NamedTextColor.RED));
                    }
                },
                (sender, args) -> {
                    if (args.length == 1) return tabCompletePlayerNames(args[0]);
                    if (args.length == 2) return tabCompleteFromList(statNames, args[1]);
                    if (args.length == 3 && statNames.contains(args[1].toLowerCase())) return List.of("0", "10", "50", "100"); // Example values
                    return List.of();
                });

        // Subcommand: stats add <playerName> <statName> <value>
        addSubCommand("stats_add", "mmocraft.admin.player.stats.add", 3, "<playerName> <statName> <value>",
                (sender, args) -> {
                    Player target = parsePlayer(sender, args[0], "playerName");
                    String statName = args[1].toLowerCase();
                    int valueToAdd = parseInt(args[2], "value");

                    PlayerDataService pds = core.getService(PlayerDataService.class);
                    PlayerProfile profile = pds.getProfile(target);

                    boolean success = updatePlayerBaseStat(profile, statName, valueToAdd, true); // true for additive
                    if (success) {
                        pds.savePlayerProfile(target, profile);
                        core.getService(PlayerStatsService.class).scheduleStatsUpdate(target);
                        sender.sendMessage(Component.text("Added " + valueToAdd + " to base stat '" + statName + "' for " + target.getName(), NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text("Unknown base stat: '" + statName + "'. Valid: " + String.join(", ", statNames), NamedTextColor.RED));
                    }
                },
                (sender, args) -> { // Same tab completion as stats_set
                    if (args.length == 1) return tabCompletePlayerNames(args[0]);
                    if (args.length == 2) return tabCompleteFromList(statNames, args[1]);
                    if (args.length == 3 && statNames.contains(args[1].toLowerCase())) return List.of("1", "5", "10", "-5");
                    return List.of();
                });

        // Subcommand: stats get <playerName> <statName|all_base|all_calculated>
        addSubCommand("stats_get", "mmocraft.admin.player.stats.get", 2, "<playerName> <statName|all_base|all_calculated>",
                (sender, args) -> {
                    Player target = parsePlayer(sender, args[0], "playerName");
                    String statQuery = args[1].toLowerCase();
                    PlayerDataService pds = core.getService(PlayerDataService.class);
                    PlayerStatsService pss = core.getService(PlayerStatsService.class);
                    PlayerProfile profile = pds.getProfile(target);
                    CalculatedPlayerStats calculated = pss.getCalculatedStats(target);

                    if (statQuery.equals("all_base")) {
                        sender.sendMessage(Component.text("Base Stats for " + target.getName() + ":", NamedTextColor.GOLD));
                        statNames.forEach(sn -> sender.sendMessage(Component.text("  " + sn + ": ", NamedTextColor.GRAY)
                                .append(Component.text(getPlayerBaseStatValue(profile, sn), NamedTextColor.YELLOW))));
                    } else if (statQuery.equals("all_calculated")) {
                        sender.sendMessage(Component.text("Calculated Stats for " + target.getName() + ":", NamedTextColor.GOLD));
                        // Iterate CalculatedPlayerStats fields via reflection or hardcode
                        getCalculatedStatValues(calculated).forEach((name, val) ->
                                sender.sendMessage(Component.text("  " + name + ": ", NamedTextColor.GRAY)
                                        .append(Component.text(val, NamedTextColor.AQUA)))
                        );
                    } else if (statNames.contains(statQuery)) {
                        sender.sendMessage(Component.text("Base " + statQuery + " for " + target.getName() + ": ", NamedTextColor.GRAY)
                                .append(Component.text(getPlayerBaseStatValue(profile, statQuery), NamedTextColor.YELLOW)));
                        // Also show calculated version
                        getCalculatedStatValues(calculated).entrySet().stream()
                                .filter(e -> e.getKey().toLowerCase().replace("_percent","").contains(statQuery.replace("base_","").replace("_percent","")))
                                .findFirst()
                                .ifPresent(e -> sender.sendMessage(Component.text("Calculated " + e.getKey() + ": ", NamedTextColor.GRAY)
                                        .append(Component.text(e.getValue(), NamedTextColor.AQUA))));

                    } else {
                        sender.sendMessage(Component.text("Unknown stat query: '" + statQuery + "'. Valid specific stats: " + String.join(", ", statNames) + " or use 'all_base' / 'all_calculated'.", NamedTextColor.RED));
                    }
                },
                (sender, args) -> {
                    if (args.length == 1) return tabCompletePlayerNames(args[0]);
                    if (args.length == 2) {
                        List<String> suggestions = new ArrayList<>(statNames);
                        suggestions.addAll(List.of("all_base", "all_calculated"));
                        return tabCompleteFromList(suggestions, args[1]);
                    }
                    return List.of();
                });

        // Subcommand: resource set <playerName> <health|mana> <value>
        addSubCommand("resource_set", "mmocraft.admin.player.resource.set", 3, "<playerName> <health|mana> <value>",
                (sender, args) -> {
                    Player target = parsePlayer(sender, args[0], "playerName");
                    String resourceType = args[1].toLowerCase();
                    int value = parseInt(args[2], "value");
                    PlayerResourceService prs = core.getService(PlayerResourceService.class);

                    if (resourceType.equals("health")) {
                        double maxHealth = target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                        target.setHealth(Math.max(0, Math.min(maxHealth, value)));
                        sender.sendMessage(Component.text("Set " + target.getName() + "'s health to " + target.getHealth(), NamedTextColor.GREEN));
                    } else if (resourceType.equals("mana")) {
                        prs.setCurrentMana(target, value);
                        sender.sendMessage(Component.text("Set " + target.getName() + "'s mana to " + prs.getCurrentMana(target), NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text("Unknown resource type: '" + resourceType + "'. Use 'health' or 'mana'.", NamedTextColor.RED));
                    }
                },
                (sender, args) -> {
                    if (args.length == 1) return tabCompletePlayerNames(args[0]);
                    if (args.length == 2) return tabCompleteFromList(List.of("health", "mana"), args[1]);
                    if (args.length == 3) return List.of("1", "10", "100"); // Example amounts
                    return List.of();
                });
    }

    private List<String> getSettableStatNames() {
        // Get field names from PlayerProfile that start with "base"
        return Arrays.stream(PlayerProfile.class.getDeclaredFields())
                .filter(f -> f.getName().startsWith("base"))
                .map(f -> f.getName().substring("base".length()).toLowerCase()) // e.g., Strength -> strength
                .sorted().toList();
    }

    private boolean updatePlayerBaseStat(PlayerProfile profile, String statName, int value, boolean additive) {
        // Using reflection here is one way, but direct switch/if-else is safer and clearer.
        String normalizedStatName = statName.toLowerCase().replace("_", "").replace("percent","");
        int currentValue;
        switch (normalizedStatName) {
            case "strength": currentValue = profile.getBaseStrength(); profile.setBaseStrength(additive ? currentValue + value : value); return true;
            case "defense": currentValue = profile.getBaseDefense(); profile.setBaseDefense(additive ? currentValue + value : value); return true;
            case "critchance": currentValue = profile.getBaseCritChance(); profile.setBaseCritChance(additive ? currentValue + value : value); return true;
            case "critdamage": currentValue = profile.getBaseCritDamage(); profile.setBaseCritDamage(additive ? currentValue + value : value); return true;
            case "maxhealth": currentValue = profile.getBaseMaxHealth(); profile.setBaseMaxHealth(additive ? currentValue + value : value); return true;
            case "maxmana": currentValue = profile.getBaseMaxMana(); profile.setBaseMaxMana(additive ? currentValue + value : value); return true;
            case "speed": currentValue = profile.getBaseSpeedPercent(); profile.setBaseSpeedPercent(additive ? currentValue + value : value); return true; // This sets baseSpeedPercent
            case "miningspeed": currentValue = profile.getBaseMiningSpeed(); profile.setBaseMiningSpeed(additive ? currentValue + value : value); return true;
            case "foragingspeed": currentValue = profile.getBaseForagingSpeed(); profile.setBaseForagingSpeed(additive ? currentValue + value : value); return true;
            case "fishingspeed": currentValue = profile.getBaseFishingSpeed(); profile.setBaseFishingSpeed(additive ? currentValue + value : value); return true;
            case "shootingspeed": currentValue = profile.getBaseShootingSpeed(); profile.setBaseShootingSpeed(additive ? currentValue + value : value); return true;
            default: return false;
        }
    }

    private String getPlayerBaseStatValue(PlayerProfile profile, String statName) {
        String normalizedStatName = statName.toLowerCase().replace("_", "").replace("percent","");
        return switch (normalizedStatName) {
            case "strength" -> String.valueOf(profile.getBaseStrength());
            case "defense" -> String.valueOf(profile.getBaseDefense());
            // ... other stats ...
            default -> "N/A";
        };
    }

    private Map<String, String> getCalculatedStatValues(CalculatedPlayerStats stats) {
        Map<String, String> values = new LinkedHashMap<>();
        // Iterate over record components using reflection (Java 16+)
        // This is more future-proof than hardcoding.
        Arrays.stream(stats.getClass().getRecordComponents()).forEach(comp -> {
            try {
                Method accessor = comp.getAccessor();
                Object value = accessor.invoke(stats);
                String name = comp.getName();
                // Simple formatting for display
                if (name.toLowerCase().contains("percent") || name.toLowerCase().contains("chance") || name.toLowerCase().contains("damage")) {
                    values.put(name, value.toString() + (name.toLowerCase().contains("percent") || name.toLowerCase().contains("chance") ? "%" : ""));
                } else {
                    values.put(name, value.toString());
                }
            } catch (Exception e) {
                logging.warn("Error reflecting CalculatedPlayerStats: " + e.getMessage());
            }
        });
        return values;
    }

    @Override
    protected boolean handleBaseCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        sendHelpMessage(sender, label);
        return true;
    }
}