package io.github.x1f4r.mmocraft.commands;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.entities.EntityManager; // Use EntityManager
import io.github.x1f4r.mmocraft.stats.EntityStatsManager; // Needed to ensure stats applied first
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import io.github.x1f4r.mmocraft.core.MMOPlugin; // Import for logger
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

    private final EntityManager entityManager;
    private final EntityStatsManager entityStatsManager;

    public SummonElderDragonCommand(MMOCore core) {
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
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        Location spawnLocation = player.getLocation();

        if (NBTKeys.MOB_TYPE_KEY == null) {
            MMOPlugin.getMMOLogger().severe("MOB_TYPE_KEY is null! Cannot summon Elder Dragon properly.");
            player.sendMessage(Component.text("Error: Mob Type Key not initialized. Please check server logs.", NamedTextColor.RED));
            return true;
        }

        EnderDragon dragon = (EnderDragon) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.ENDER_DRAGON);

        // Set visual name
        dragon.customName(LegacyComponentSerializer.legacyAmpersand().deserialize("&5&lElder Dragon"));
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
            player.sendMessage(Component.text("An ", NamedTextColor.GREEN)
                .append(Component.text("Elder Dragon", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text(" with custom AI has been summoned!", NamedTextColor.GREEN)));
        } else {
             player.sendMessage(Component.text("Error: EntityManager not available to start AI.", NamedTextColor.RED));
             MMOPlugin.getMMOLogger().severe("SummonElderDragonCommand executed but EntityManager was null in MMOCore!");
             // Dragon still spawns with custom stats, just no custom AI behaviour
        }

        return true;
    }
}
