package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.entities.EntityManager; // Use EntityManager
import io.github.x1f4r.mmocraft.stats.EntityStatsManager; // Needed to ensure stats applied first
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import io.github.x1f4r.mmocraft.core.MMOPlugin; // Import for logger

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public class SummonElderDragonCommand implements CommandExecutor {

    private final MMOCore core;
    private final EntityManager entityManager;
    private final EntityStatsManager entityStatsManager;

    public SummonElderDragonCommand(MMOCore core) {
        this.core = core;
        // Assuming EntityManager exists in core now
        this.entityManager = core.getEntityManager();
        this.entityStatsManager = core.getEntityStatsManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can summon the Elder Dragon.");
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("mmocraft.command.summon.elderdragon")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        Location spawnLocation = player.getLocation();

        if (NBTKeys.MOB_TYPE_KEY == null) {
            MMOPlugin.getMMOLogger().severe("MOB_TYPE_KEY is null! Cannot summon Elder Dragon properly.");
            player.sendMessage(ChatColor.RED + "Error: Mob Type Key not initialized. Please check server logs.");
            return true;
        }

        EnderDragon dragon = (EnderDragon) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.ENDER_DRAGON);

        // Set visual name
        dragon.setCustomName(ChatColor.translateAlternateColorCodes('&', "&5&lElder Dragon"));
        dragon.setCustomNameVisible(true);

        // Tag it BEFORE registering stats, so EntityStatsManager can potentially use the tag
        dragon.getPersistentDataContainer().set(NBTKeys.MOB_TYPE_KEY, PersistentDataType.STRING, "elder_dragon");
        MMOPlugin.getMMOLogger().info("Tagged spawned dragon as elder_dragon with UUID: " + dragon.getUniqueId());

        // Ensure EntityStatsManager applies base stats from mobs.yml first
        // This will overwrite vanilla defaults based on the ENDER_DRAGON entry in mobs.yml
        entityStatsManager.registerEntity(dragon); // This applies stats from config

        // Now, start the custom AI using the EntityManager
        if (entityManager != null) {
            entityManager.startElderDragonAI(dragon);
            player.sendMessage(ChatColor.GREEN + "An " + ChatColor.DARK_PURPLE + ChatColor.BOLD + "Elder Dragon" + ChatColor.GREEN + " with custom AI has been summoned!");
        } else {
             player.sendMessage(ChatColor.RED + "Error: EntityManager not available to start AI.");
             MMOPlugin.getMMOLogger().severe("SummonElderDragonCommand executed but EntityManager was null in MMOCore!");
             // Dragon still spawns with custom stats, just no custom AI behaviour
        }

        return true;
    }
}
