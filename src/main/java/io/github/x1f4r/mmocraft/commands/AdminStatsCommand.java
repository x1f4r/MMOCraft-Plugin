package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import io.github.x1f4r.mmocraft.stats.PlayerStats; // Correct package
import io.github.x1f4r.mmocraft.core.MMOPlugin; // Import for logger if needed directly

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections; // <<< ADDED IMPORT
import java.util.List;
import java.util.stream.Collectors;

public class AdminStatsCommand implements CommandExecutor, TabCompleter {

    private final MMOCore core;
    private final PlayerStatsManager statsManager;

    public AdminStatsCommand(MMOCore core) {
        this.core = core;
        this.statsManager = core.getPlayerStatsManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mmocraft.command.adminstats")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length < 5) {
            sendUsage(sender, label);
            return true;
        }

        String statType = args[0].toLowerCase();
        String subType = args[1].toLowerCase();
        String operation = args[2].toLowerCase();
        String playerName = args[3];
        String amountStr = args[4];

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' not found.");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount: " + amountStr);
            return true;
        }

        PlayerStats targetStats = statsManager.getStats(target);
        AttributeInstance maxHealthAttr = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);

        switch (statType) {
            case "health":
                if (maxHealthAttr == null) {
                    sender.sendMessage(ChatColor.RED + "Could not access health attribute for " + target.getName());
                    return true;
                }
                switch (subType) {
                    case "current":
                        handleNumericOperation(sender, target, operation, amount, target.getHealth(), target::setHealth, maxHealthAttr::getValue, "Current Health");
                        break;
                    case "max":
                        // IMPORTANT: Modifying BASE max health attribute. PlayerStatsManager handles applying bonuses on top.
                        handleNumericOperation(sender, target, operation, amount, maxHealthAttr.getBaseValue(), maxHealthAttr::setBaseValue, () -> Double.MAX_VALUE, "Max Health (Base)");
                        // Ensure current health doesn't exceed new max
                        if (target.getHealth() > maxHealthAttr.getValue()) {
                            target.setHealth(maxHealthAttr.getValue());
                        }
                        // No need to call statsManager.scheduleStatsUpdate here, Bukkit handles attribute changes.
                        break;
                    default:
                        sender.sendMessage(ChatColor.RED + "Invalid health sub-type. Use 'current' or 'max'.");
                        return true;
                }
                break;

            case "mana":
                int intAmount = (int) amount; // Mana uses integers
                switch (subType) {
                    case "current":
                        handleNumericOperation(sender, target, operation, intAmount, targetStats.getCurrentMana(), targetStats::setCurrentMana, targetStats::getMaxMana, "Current Mana");
                        break;
                    case "max":
                        // This modifies the BASE max mana stored in PlayerStats
                        handleNumericOperation(sender, target, operation, intAmount, targetStats.getBaseMaxMana(), targetStats::setBaseMaxMana, () -> Integer.MAX_VALUE, "Max Mana (Base)");
                        // Schedule update needed as PlayerStats object was changed
                        statsManager.scheduleStatsUpdate(target);
                        break;
                    default:
                        sender.sendMessage(ChatColor.RED + "Invalid mana sub-type. Use 'current' or 'max'.");
                        return true;
                }
                break;

            // TODO: Add cases for modifying other BASE stats (strength, defense, etc.) in PlayerStats
            // case "strength":
            //    if (!subType.equals("base")) { sender.sendMessage(ChatColor.RED + "Only 'base' sub-type allowed for strength."); return true; }
            //    handleNumericOperation(sender, target, operation, (int)amount, targetStats.getBaseStrength(), targetStats::setBaseStrength, () -> Integer.MAX_VALUE, "Strength (Base)");
            //    statsManager.scheduleStatsUpdate(target); // Update needed
            //    break;

            default:
                sendUsage(sender, label);
                return true;
        }
        return true;
    }

    // Overload for double values (Health)
    private void handleNumericOperation(CommandSender sender, Player target, String operation, double amount,
                                        double currentValue, java.util.function.Consumer<Double> setter,
                                        java.util.function.Supplier<Double> maxProvider, String statName) {
        double newValue = currentValue;
        String operationText = "";
        switch (operation) {
            case "set": newValue = amount; operationText = "set to"; break;
            case "add": newValue = currentValue + amount; operationText = "increased by"; break;
            case "remove": newValue = currentValue - amount; operationText = "decreased by"; break;
            default: sender.sendMessage(ChatColor.RED + "Invalid operation: " + operation); return;
        }
        // Clamp value between 0 and maxProvider result
        newValue = Math.max(0.0, Math.min(newValue, maxProvider.get()));
        setter.accept(newValue);

        // Get the value *after* setting it to report correctly
        double finalValue = 0;
        if (statName.contains("Current Health")) {
            finalValue = target.getHealth();
        } else if (statName.contains("Max Health")) {
            AttributeInstance healthAttr = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            finalValue = (healthAttr != null) ? healthAttr.getBaseValue() : 0; // Report base value
        } else {
            finalValue = newValue; // Fallback
        }

        sender.sendMessage(ChatColor.GREEN + statName + " for " + target.getName() + " " + operationText + " " + String.format("%.2f", amount) + ". New value: " + String.format("%.2f", finalValue));
    }

    // Overload for integer values (Mana, Base Stats)
    private void handleNumericOperation(CommandSender sender, Player target, String operation, int amount,
                                        int currentValue, java.util.function.Consumer<Integer> setter,
                                        java.util.function.Supplier<Integer> maxProvider, String statName) {
        int newValue = currentValue;
        String operationText = "";
        switch (operation) {
            case "set": newValue = amount; operationText = "set to"; break;
            case "add": newValue = currentValue + amount; operationText = "increased by"; break;
            case "remove": newValue = currentValue - amount; operationText = "decreased by"; break;
            default: sender.sendMessage(ChatColor.RED + "Invalid operation: " + operation); return;
        }
        newValue = Math.max(0, Math.min(newValue, maxProvider.get()));
        setter.accept(newValue);

        int finalValue = 0;
        PlayerStats currentStats = statsManager.getStats(target); // Get potentially updated stats
        if (statName.contains("Current Mana")) {
            finalValue = currentStats.getCurrentMana();
        } else if (statName.contains("Max Mana")) {
            finalValue = currentStats.getBaseMaxMana(); // Report base value
        }
        // Add cases for other base stats if implemented
        // else if (statName.contains("Strength")) { finalValue = currentStats.getBaseStrength(); }
        else {
            finalValue = newValue; // Fallback
        }

        sender.sendMessage(ChatColor.GREEN + statName + " for " + target.getName() + " " + operationText + " " + amount + ". New value: " + finalValue);
    }


    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <statType> <subType> <operation> <player> <amount>");
        sender.sendMessage(ChatColor.YELLOW + "Stat Types: health, mana"); // Add strength, defense etc. when implemented
        sender.sendMessage(ChatColor.YELLOW + "Sub Types (health/mana): current, max");
        // sender.sendMessage(ChatColor.YELLOW + "Sub Types (strength/defense/...): base");
        sender.sendMessage(ChatColor.YELLOW + "Operations: set, add, remove");
        sender.sendMessage(ChatColor.YELLOW + "Example: /" + label + " health current set Notch 20");
        sender.sendMessage(ChatColor.YELLOW + "Example: /" + label + " mana max add Notch 50");
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> options = new ArrayList<>();

        if (!sender.hasPermission("mmocraft.command.adminstats")) {
            return completions;
        }

        String currentArg = args[args.length - 1].toLowerCase(); // Get current argument for filtering

        if (args.length == 1) {
            options.addAll(Arrays.asList("health", "mana")); // Add other base stats later
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("health") || args[0].equalsIgnoreCase("mana")) {
                options.addAll(Arrays.asList("current", "max"));
            }
            // else if (args[0].equalsIgnoreCase("strength") || args[0].equalsIgnoreCase("defense")) {
            //    options.add("base");
            // }
        } else if (args.length == 3) {
            if (args[1].equalsIgnoreCase("current") || args[1].equalsIgnoreCase("max") || args[1].equalsIgnoreCase("base")) {
                options.addAll(Arrays.asList("set", "add", "remove"));
            }
        } else if (args.length == 4) {
            options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        } else if (args.length == 5) {
            options.addAll(Arrays.asList("1", "10", "50", "100", "1000")); // Amount suggestions
        }

        // Filter options based on current argument
        for (String option : options) {
            if (option.toLowerCase().startsWith(currentArg)) {
                completions.add(option);
            }
        }
        Collections.sort(completions); // <<< USES IMPORT
        return completions;
    }
}
