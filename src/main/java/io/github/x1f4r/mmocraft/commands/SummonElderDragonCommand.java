package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.mobs.ElderDragonAI;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
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
import org.bukkit.scheduler.BukkitTask; // Import BukkitTask
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

        // Apply stats from mobs.yml if defined, otherwise use defaults here
        // This ensures EntityStatsManager applies its configured stats first if an ENDER_DRAGON entry exists.
        // The Summon command can then override specifics if needed.
        plugin.getEntityStatsManager().registerEntity(dragon); // This will apply stats from mobs.yml

        // Example: Override health specifically for this summoned dragon if desired,
        // or rely on mobs.yml for consistency.
        AttributeInstance maxHealthAttribute = dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttribute != null) {
            // If you want a specific health for summoned ones, different from mobs.yml:
            // maxHealthAttribute.setBaseValue(5000.0); // Example higher health
            // dragon.setHealth(5000.0);
            // Otherwise, the value from mobs.yml (via registerEntity) will be used.
            // Ensure health is full based on the applied max health.
            dragon.setHealth(maxHealthAttribute.getValue());
        } else {
            plugin.getLogger().warning("Could not set Max Health attribute for Elder Dragon!");
        }

        // Tag it as a custom mob type
        dragon.getPersistentDataContainer().set(NBTKeys.MOB_TYPE_KEY, PersistentDataType.STRING, "elder_dragon");
        plugin.getLogger().info("Tagged spawned dragon as elder_dragon with UUID: " + dragon.getUniqueId());

        // Start Custom AI and store the task
        if (dragon.isValid()) {
            ElderDragonAI dragonAI = new ElderDragonAI(plugin, dragon);
            BukkitTask aiTask = dragonAI.start(); // Start the AI and get the task
            plugin.getActiveDragonAIs().put(dragon.getUniqueId(), aiTask); // Store the task
            plugin.getLogger().info("Stored AI task for dragon " + dragon.getUniqueId());
        }

        player.sendMessage(ChatColor.GREEN + "An " + ChatColor.DARK_PURPLE + ChatColor.BOLD + "Elder Dragon" + ChatColor.GREEN + " with custom AI has been summoned!");
        return true;
    }
}
