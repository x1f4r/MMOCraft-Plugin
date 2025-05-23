package io.github.x1f4r.mmocraft.commands.user;

import io.github.x1f4r.mmocraft.commands.AbstractMMOCommand;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.services.CraftingGUIService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class UserCustomCraftCommand extends AbstractMMOCommand {

    public UserCustomCraftCommand(MMOCore core) {
        super(core, "customcraft", "mmocraft.user.command.customcraft");
    }

    @Override
    protected boolean handleBaseCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be run by a player.", NamedTextColor.RED));
            return true;
        }

        CraftingGUIService craftingService = core.getService(CraftingGUIService.class);
        craftingService.openCustomCraftingGUI(player);
        return true;
    }
}