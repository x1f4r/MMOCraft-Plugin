package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.player.PlayerStats;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager; // Ensure this import is correct
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
import java.util.List;
import java.util.stream.Collectors;

public class AdminStatsCommand implements CommandExecutor, TabCompleter {

    private final MMOCraft plugin;
    private final PlayerStatsManager statsManager;

    public AdminStatsCommand(MMOCraft plugin) {
        this.plugin = plugin;
        // Ensure PlayerStatsManager exists and is accessible
        if (plugin.getPlayerStatsManager() == null) {
            throw new IllegalStateException("PlayerStatsManager is not initialized in MMOCraft plugin!");
        }
        this.statsManager = plugin.getPlayerStatsManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mmocraft.command.adminstats")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        // Command structure: /mmoadmin <statType> <subType> <operation> <player> <amount>
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

        PlayerStats targetStats = statsManager.getStats(target); // Get stats object for the target player
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
                        handleNumericOperation(sender, target, operation, amount, maxHealthAttr.getBaseValue(), maxHealthAttr::setBaseValue, () -> Double.MAX_VALUE, "Max Health (Base)");
                        if (target.getHealth() > maxHealthAttr.getValue()) {
                            target.setHealth(maxHealthAttr.getValue());
                        }
                        statsManager.scheduleStatsUpdate(target);
                        break;
                    default:
                        sender.sendMessage(ChatColor.RED + "Invalid health sub-type. Use 'current' or 'max'.");
                        return true;
                }
                break;

            case "mana":
                int intAmount = (int) amount;
                switch (subType) {
                    case "current":
                        handleNumericOperation(sender, target, operation, intAmount, targetStats.getCurrentMana(), targetStats::setCurrentMana, targetStats::getMaxMana, "Current Mana");
                        break;
                    case "max":
                        handleNumericOperation(sender, target, operation, intAmount, targetStats.getBaseMaxMana(), targetStats::setBaseMaxMana, () -> Integer.MAX_VALUE, "Max Mana (Base)");
                        statsManager.scheduleStatsUpdate(target);
                        break;
                    default:
                        sender.sendMessage(ChatColor.RED + "Invalid mana sub-type. Use 'current' or 'max'.");
                        return true;
                }
                break;

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
        switch (operation) {
            case "set": newValue = amount; break;
            case "add": newValue = currentValue + amount; break;
            case "remove": newValue = currentValue - amount; break;
            default: sender.sendMessage(ChatColor.RED + "Invalid operation."); return;
        }
        newValue = Math.max(0, Math.min(newValue, maxProvider.get()));
        setter.accept(newValue);
        double finalValue = 0;
        if(statName.contains("Current")){
            finalValue = target.getHealth();
        } else if (statName.contains("Max Health")) {
            AttributeInstance healthAttr = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            finalValue = (healthAttr != null) ? healthAttr.getBaseValue() : 0;
        }
        sender.sendMessage(ChatColor.GREEN + statName + " for " + target.getName() + " " + operation + (operation.equals("set") ? " to " : " by ") + String.format("%.2f", amount) + ". New value: " + String.format("%.2f", finalValue));
    }

    // Overload for integer values (Mana)
    private void handleNumericOperation(CommandSender sender, Player target, String operation, int amount,
                                        int currentValue, java.util.function.Consumer<Integer> setter,
                                        java.util.function.Supplier<Integer> maxProvider, String statName) {
        int newValue = currentValue;
        switch (operation) {
            case "set": newValue = amount; break;
            case "add": newValue = currentValue + amount; break;
            case "remove": newValue = currentValue - amount; break;
            default: sender.sendMessage(ChatColor.RED + "Invalid operation."); return;
        }
        newValue = Math.max(0, Math.min(newValue, maxProvider.get()));
        setter.accept(newValue);
        int finalValue;
        PlayerStats currentStats = statsManager.getStats(target);
        if (statName.contains("Current")) {
            finalValue = currentStats.getCurrentMana();
        } else {
            finalValue = currentStats.getBaseMaxMana();
        }
        sender.sendMessage(ChatColor.GREEN + statName + " for " + target.getName() + " " + operation + (operation.equals("set") ? " to " : " by ") + amount + ". New value: " + finalValue);
    }


    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <statType> <subType> <operation> <player> <amount>");
        sender.sendMessage(ChatColor.YELLOW + "Stat Types: health, mana");
        sender.sendMessage(ChatColor.YELLOW + "Sub Types (for health/mana): current, max");
        sender.sendMessage(ChatColor.YELLOW + "Operations: set, add, remove");
        sender.sendMessage(ChatColor.YELLOW + "Example: /" + label + " health current set Notch 20");
        sender.sendMessage(ChatColor.YELLOW + "Example: /" + label + " mana max add Notch 50");
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("mmocraft.command.adminstats")) {
            return completions;
        }

        if (args.length == 1) { // <statType>
            completions.addAll(Arrays.asList("health", "mana"));
        } else if (args.length == 2) { // <subType>
            if (args[0].equalsIgnoreCase("health") || args[0].equalsIgnoreCase("mana")) {
                completions.addAll(Arrays.asList("current", "max"));
            }
        } else if (args.length == 3) { // <operation>
            if (args[1].equalsIgnoreCase("current") || args[1].equalsIgnoreCase("max")) {
                completions.addAll(Arrays.asList("set", "add", "remove"));
            }
        } else if (args.length == 4) { // <player>
            completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        } else if (args.length == 5) { // <amount>
            completions.addAll(Arrays.asList("1", "10", "20", "50", "100", "1000", "10000"));
        }

        // Filter completions based on current argument
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
