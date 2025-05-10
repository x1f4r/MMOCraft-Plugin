package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.items.ItemManager; // Get manager from core
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays; // Import Arrays for amount suggestions
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GiveCustomItemCommand implements CommandExecutor, TabCompleter {

    private final ItemManager itemManager;

    public GiveCustomItemCommand(MMOCore core) {
        this.itemManager = core.getItemManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mmocraft.command.givecustomitem")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /" + label + " <item_id> [amount] [player]", NamedTextColor.RED));
            return true;
        }

        String itemId = args[0].toLowerCase();
        ItemStack customItem = itemManager.getItem(itemId); // getItem already returns a clone

        if (customItem == null || customItem.getType() == Material.AIR) {
            sender.sendMessage(Component.text("Error: Custom item with ID '" + itemId + "' not found.", NamedTextColor.RED));
            sender.sendMessage(Component.text("Available items: " + String.join(", ", itemManager.getAllItemIds()), NamedTextColor.YELLOW));
            return true;
        }

        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount < 1) {
                    sender.sendMessage(Component.text("Amount must be at least 1.", NamedTextColor.RED));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount: '" + args[1] + "'. Must be a number.", NamedTextColor.RED));
                return true;
            }
        }

        Player targetPlayer;
        if (args.length >= 3) {
            targetPlayer = Bukkit.getPlayerExact(args[2]);
            if (targetPlayer == null) {
                sender.sendMessage(Component.text("Player '" + args[2] + "' not found or is offline.", NamedTextColor.RED));
                return true;
            }
        } else {
            if (sender instanceof Player) {
                targetPlayer = (Player) sender;
            } else {
                sender.sendMessage(Component.text("You must specify a player when running from console.", NamedTextColor.RED));
                return true;
            }
        }

        // Give the item(s)
        int remainingAmount = amount;
        final int maxStack = customItem.getMaxStackSize(); // Get max stack size of the specific item

        while (remainingAmount > 0) {
            ItemStack itemToGive = customItem.clone(); // Use the template clone
            int stackAmount = Math.min(remainingAmount, maxStack);
            itemToGive.setAmount(stackAmount);

            // Add to inventory, drop leftovers if full
            targetPlayer.getInventory().addItem(itemToGive).forEach((index, item) -> {
                targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), item);
                targetPlayer.sendMessage(Component.text("Your inventory was full, some items dropped!", NamedTextColor.YELLOW));
            });
            remainingAmount -= stackAmount;
        }

        // Confirmation message
        Component itemNameDisplay;
        ItemMeta meta = customItem.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            itemNameDisplay = meta.displayName();
        } else {
            itemNameDisplay = Component.text(itemId, NamedTextColor.GOLD);
        }

        sender.sendMessage(Component.text("Gave ", NamedTextColor.GREEN)
                .append(Component.text(amount + "x ", NamedTextColor.AQUA))
                .append(itemNameDisplay)
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text(targetPlayer.getName(), NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.GREEN)));
        if (sender != targetPlayer) {
            targetPlayer.sendMessage(Component.text("You received ", NamedTextColor.GREEN)
                    .append(Component.text(amount + "x ", NamedTextColor.AQUA))
                    .append(itemNameDisplay)
                    .append(Component.text(".", NamedTextColor.GREEN)));
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

        String currentArg = args[args.length - 1].toLowerCase();
        List<String> options = new ArrayList<>(); // Define options list here

        if (args.length == 1) { // Item ID
            options.addAll(itemManager.getAllItemIds());
        } else if (args.length == 2) { // Amount
            options.addAll(Arrays.asList("1", "16", "32", "64"));
        } else if (args.length == 3) { // Player Name
            options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        }

        // Filter options based on current argument
        for (String option : options) {
            if (option.toLowerCase().startsWith(currentArg)) {
                completions.add(option);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}
