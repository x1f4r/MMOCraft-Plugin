package io.github.x1f4r.mmocraft.commands.admin;

import io.github.x1f4r.mmocraft.commands.AbstractMMOCommand;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.items.CustomItem;
import io.github.x1f4r.mmocraft.services.ItemService;
import io.github.x1f4r.mmocraft.services.NBTService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull; // Added import

import java.util.ArrayList;
import java.util.List;
import java.util.Set; // Added import
import java.util.stream.Collectors;

public class AdminItemCommands extends AbstractMMOCommand {

    public AdminItemCommands(MMOCore core, String commandLabelForUsage) {
        super(core, commandLabelForUsage, null); // Base permission handled by master command

        ItemService itemService = core.getService(ItemService.class);

        // Subcommand: give <customItemId> [amount] [playerName]
        addSubCommand("give", "mmocraft.admin.item.give", 1, "<itemID> [amount] [playerName]",
                (sender, args) -> {
                    String itemId = args[0];
                    int amount = (args.length > 1) ? parseInt(args[1], "amount") : 1;
                    Player targetPlayer = null;

                    if (args.length > 2) {
                        targetPlayer = parsePlayer(sender, args[2], "playerName");
                    } else if (sender instanceof Player p) {
                        targetPlayer = p;
                    } else {
                        throw new CommandArgumentException("You must specify a player name when running from console.");
                    }
                    if (amount < 1) amount = 1;

                    ItemStack itemStack = itemService.createItemStack(itemId, amount);
                    if (itemStack.getType() == Material.BARRIER && !itemId.equalsIgnoreCase("barrier")) { // Barrier is error item
                        sender.sendMessage(Component.text("Custom item with ID '" + itemId + "' not found.", NamedTextColor.RED));
                        return;
                    }

                    targetPlayer.getInventory().addItem(itemStack);
                    sender.sendMessage(Component.text("Gave " + targetPlayer.getName() + " " + amount + "x " + itemId, NamedTextColor.GREEN));
                    targetPlayer.sendMessage(Component.text("You received " + amount + "x ", NamedTextColor.GOLD)
                            .append(itemStack.displayName().colorIfAbsent(NamedTextColor.AQUA))
                            .append(Component.text("!", NamedTextColor.GOLD)));
                },
                (sender, args) -> { // Tab completer for "give"
                    if (args.length == 1) return tabCompleteFromList(new ArrayList<>(itemService.getAllCustomItemIds()), args[0]);
                    if (args.length == 2) return List.of("1", "16", "32", "64");
                    if (args.length == 3) return tabCompletePlayerNames(args[2]);
                    return List.of();
                });

        // Subcommand: nbt <customItemId> (Displays NBT for a template item)
        // This is a conceptual command to show ItemService data, not live item NBT.
        addSubCommand("info", "mmocraft.admin.item.info", 1, "<itemID>",
                (sender, args) -> {
                    String itemId = args[0];
                    CustomItem template = itemService.getCustomItemTemplate(itemId);
                    if (template == null) {
                        sender.sendMessage(Component.text("Custom item template with ID '" + itemId + "' not found.", NamedTextColor.RED));
                        return;
                    }
                    sender.sendMessage(Component.text("--- Item Info: " + itemId + " ---", NamedTextColor.GOLD));
                    sender.sendMessage(Component.text("Material: ", NamedTextColor.GRAY).append(Component.text(template.material().name(), NamedTextColor.YELLOW)));
                    sender.sendMessage(Component.text("Display Name: ").append(template.displayName()));
                    sender.sendMessage(Component.text("Unbreakable: ", NamedTextColor.GRAY).append(Component.text(template.unbreakable(), NamedTextColor.YELLOW)));

                    sender.sendMessage(Component.text("MMO Stats:", NamedTextColor.AQUA));
                    if (template.mmoStrength() != 0) sender.sendMessage(Component.text("  Strength: " + template.mmoStrength(), NamedTextColor.WHITE));
                    if (template.mmoDefense() != 0) sender.sendMessage(Component.text("  Defense: " + template.mmoDefense(), NamedTextColor.WHITE));
                    // ... (add all other MMO stats) ...
                    if (template.linkedAbilityId() != null) {
                        sender.sendMessage(Component.text("Ability ID: ", NamedTextColor.GRAY).append(Component.text(template.linkedAbilityId(), NamedTextColor.LIGHT_PURPLE)));
                        if (template.overrideManaCost() != null) sender.sendMessage(Component.text("  Override Mana: " + template.overrideManaCost(), NamedTextColor.WHITE));
                        if (template.overrideCooldownTicks() != null) sender.sendMessage(Component.text("  Override Cooldown: " + template.overrideCooldownTicks() + "t", NamedTextColor.WHITE));
                    }
                    if (!template.genericCustomNbt().isEmpty()) {
                        sender.sendMessage(Component.text("Generic NBT Params:", NamedTextColor.AQUA));
                        template.genericCustomNbt().forEach((key, value) ->
                                sender.sendMessage(Component.text("  " + key + ": ", NamedTextColor.GRAY).append(Component.text(value.toString(), NamedTextColor.WHITE)))
                        );
                    }
                    // For live item NBT, you'd use /data get entity @s SelectedItem
                },
                (sender, args) -> {
                    if (args.length == 1) return tabCompleteFromList(new ArrayList<>(itemService.getAllCustomItemIds()), args[0]);
                    return List.of();
                });

        // Subcommand: list
        addSubCommand("list", "mmocraft.admin.item.list", 0, "",
                (sender, args) -> {
                    Set<String> itemIds = itemService.getAllCustomItemIds();
                    if (itemIds.isEmpty()) {
                        sender.sendMessage(Component.text("No custom items are currently defined.", NamedTextColor.YELLOW));
                        return;
                    }
                    sender.sendMessage(Component.text("Available Custom Item IDs (" + itemIds.size() + "):", NamedTextColor.GOLD));
                    // Paginate this if list gets too long
                    String joinedIds = String.join(", ", new ArrayList<>(itemIds).stream().sorted().toList());
                    sender.sendMessage(Component.text(joinedIds, NamedTextColor.YELLOW));
                },
                null // No specific args for list
        );
    }

    @Override
    protected boolean handleBaseCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        sendHelpMessage(sender, label);
        return true;
    }
}