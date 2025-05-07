package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.mobs.ElderDragonAI; // Updated import
import io.github.x1f4r.mmocraft.utils.NBTKeys; // Updated import
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public class SummonElderDragonCommand implements CommandExecutor {

    private final MMOCraft plugin;

    public SummonElderDragonCommand(MMOCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can summon the Elder Dragon.");
            return true;
        }
        Player player = (Player) sender;
        Location spawnLocation = player.getLocation();

        if (NBTKeys.MOB_TYPE_KEY == null) {
            plugin.getLogger().severe("MOB_TYPE_KEY is null! Cannot summon Elder Dragon properly.");
            player.sendMessage(ChatColor.RED + "Error: Mob Type Key not initialized. Please check server logs.");
            return true;
        }

        EnderDragon dragon = (EnderDragon) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.ENDER_DRAGON);

        dragon.setCustomName(ChatColor.translateAlternateColorCodes('&', "&5&lElder Dragon"));
        dragon.setCustomNameVisible(true);

        AttributeInstance maxHealthAttribute = dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(300.0); // Example health
            dragon.setHealth(300.0);
        } else {
            plugin.getLogger().warning("Could not set Max Health attribute for Elder Dragon!");
        }

        dragon.getPersistentDataContainer().set(NBTKeys.MOB_TYPE_KEY, PersistentDataType.STRING, "elder_dragon");
        plugin.getLogger().info("Tagged spawned dragon as elder_dragon.");

        // --- Start Custom AI ---
        if (dragon.isValid()) {
            ElderDragonAI dragonAI = new ElderDragonAI(plugin, dragon);
            dragonAI.start(); 
        }
        // --- End Custom AI ---

        player.sendMessage(ChatColor.GREEN + "An " + ChatColor.DARK_PURPLE + ChatColor.BOLD + "Elder Dragon" + ChatColor.GREEN + " with custom AI has been summoned!");
        return true;
    }
}
