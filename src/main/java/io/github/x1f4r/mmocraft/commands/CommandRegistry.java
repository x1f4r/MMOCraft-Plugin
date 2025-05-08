package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

import java.util.logging.Logger;

public class CommandRegistry {

    private final MMOCore core;
    private final MMOPlugin plugin;
    private final Logger log;

    public CommandRegistry(MMOCore core) {
        this.core = core;
        this.plugin = core.getPlugin();
        this.log = MMOPlugin.getMMOLogger();
    }

    public void registerCommands() {
        log.info("Registering commands...");

        registerCommand("customcraft", new CustomCraftCommand(core));
        registerCommand("summonelderdragon", new SummonElderDragonCommand(core));
        registerCommand("givecustomitem", new GiveCustomItemCommand(core));
        registerCommand("stats", new PlayerStatsCommand(core));
        registerCommand("reloadmobs", new ReloadMobsConfigCommand(core));
        registerCommand("mmoadmin", new AdminStatsCommand(core));

        log.info("Commands registered.");
    }

    private void registerCommand(String commandName, CommandExecutor executor) {
        PluginCommand command = plugin.getCommand(commandName);
        if (command != null) {
            command.setExecutor(executor);
            if (executor instanceof TabCompleter) {
                command.setTabCompleter((TabCompleter) executor);
            }
            log.finer("Registered command: " + commandName);
        } else {
            log.warning("Command '" + commandName + "' not found in plugin.yml! Cannot register executor.");
        }
    }
}
