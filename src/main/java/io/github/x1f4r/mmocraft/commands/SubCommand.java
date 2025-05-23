package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.exceptions.CommandArgumentException;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface SubCommand {
    String getName(); // Typically the main command name
    String getUsage();
    String getDescription();
    @Nullable String getPermission();
    @Nullable List<String> getAliases();
    boolean isConsoleAllowed();
    boolean execute(CommandSender sender, String[] args) throws CommandArgumentException;
    @Nullable List<String> onTabComplete(CommandSender sender, String[] args);
}
