package com.x1f4r.mmocraft.command;

import org.bukkit.command.CommandSender;
import java.util.List;

public interface CommandExecutable {
    /**
     * Executes the given command, returning its success.
     *
     * @param sender Source of the command
     * @param args   Passed command arguments
     * @return true if a valid command, otherwise false
     */
    boolean onCommand(CommandSender sender, String[] args);
}
