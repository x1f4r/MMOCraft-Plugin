package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.items.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GiveCustomItemCommand implements CommandExecutor, TabCompleter {

    private final MMOCraft plugin;
    private final ItemManager itemManager;

    public GiveCustomItemCommand(MMOCraft plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mmocraft.command.givecustomitem")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <item_id> [amount] [player]");
            return true;
        }

        String itemId = args[0].toLowerCase();
        ItemStack customItem = itemManager.getItem(itemId);

        if (customItem == null || customItem.getType() == Material.AIR) {
            sender.sendMessage(ChatColor.RED + "Error: Custom item with ID '" + itemId + "' not found.");
            if (itemManager.getAllItemIds() != null && !itemManager.getAllItemIds().isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "Available items: " + String.join(", ", itemManager.getAllItemIds()));
            } else {
                sender.sendMessage(ChatColor.YELLOW + "No custom items seem to be loaded.");
            }
            return true;
        }

        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount < 1) {
                    sender.sendMessage(ChatColor.RED + "Error: Amount must be at least 1.");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Error: Invalid amount specified. Must be a number.");
                return true;
            }
        }

        Player targetPlayer = null; // Initialized to null
        if (args.length >= 3) {
            targetPlayer = Bukkit.getPlayerExact(args[2]);
            if (targetPlayer == null) {
                sender.sendMessage(ChatColor.RED + "Error: Player '" + args[2] + "' not found or is offline.");
                return true;
            }
        } else {
            if (sender instanceof Player) {
                targetPlayer = (Player) sender;
            } else {
                sender.sendMessage(ChatColor.RED + "Error: You must specify a player when running this command from the console.");
                return true;
            }
        }

        // Give the item(s)
        // Handle amounts greater than max stack size by giving multiple stacks
        int remainingAmount = amount;
        final Player finalTargetPlayer = targetPlayer; // Create a final reference for the lambda

        while (remainingAmount > 0) {
            ItemStack itemToGive = customItem.clone(); // Clone for each stack
            int stackAmount = Math.min(remainingAmount, itemToGive.getMaxStackSize());
            if (stackAmount <= 0) stackAmount = 1; // Ensure at least 1 if something went wrong with calculation
            itemToGive.setAmount(stackAmount);

            // Use the final reference inside the lambda
            finalTargetPlayer.getInventory().addItem(itemToGive).forEach((index, item) -> {
                finalTargetPlayer.getWorld().dropItemNaturally(finalTargetPlayer.getLocation(), item);
                finalTargetPlayer.sendMessage(ChatColor.YELLOW + "Your inventory was full, some items were dropped on the ground!");
            });
            remainingAmount -= stackAmount;
        }


        String itemNameDisplay = customItem.hasItemMeta() && customItem.getItemMeta().hasDisplayName() ?
                customItem.getItemMeta().getDisplayName() :
                ChatColor.GOLD + itemId;

        sender.sendMessage(ChatColor.GREEN + "Gave " + ChatColor.AQUA + amount + "x " + itemNameDisplay +
                ChatColor.GREEN + " to " + ChatColor.YELLOW + finalTargetPlayer.getName() + ChatColor.GREEN + ".");
        if (sender != finalTargetPlayer) {
            finalTargetPlayer.sendMessage(ChatColor.GREEN + "You received " + ChatColor.AQUA + amount + "x " + itemNameDisplay +
                    ChatColor.GREEN + ".");
        }

        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("mmocraft.command.givecustomitem")) {
            return completions;
        }

        if (args.length == 1) {
            String currentArg = args[0].toLowerCase();
            if (itemManager.getAllItemIds() != null) {
                itemManager.getAllItemIds().stream()
                        .filter(itemId -> itemId.toLowerCase().startsWith(currentArg))
                        .forEach(completions::add);
            }
        } else if (args.length == 2) {
            completions.add("1");
            completions.add("16");
            completions.add("32");
            completions.add("64");
        } else if (args.length == 3) {
            String currentArg = args[2].toLowerCase();
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(playerName -> playerName.toLowerCase().startsWith(currentArg))
                    .forEach(completions::add);
        }

        Collections.sort(completions);
        return completions;
    }
}