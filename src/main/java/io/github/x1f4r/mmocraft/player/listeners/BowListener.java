package io.github.x1f4r.mmocraft.listeners;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.player.PlayerStats;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public class BowListener implements Listener {

    private final MMOCraft plugin;
    private final PlayerStatsManager statsManager;

    public BowListener(MMOCraft plugin) {
        this.plugin = plugin;
        this.statsManager = plugin.getPlayerStatsManager();
    }

    // This listener now primarily handles normally drawn bows,
    // applying shooting speed to their projectiles if they are custom bows.
    // Instant-fire bows are handled in PlayerAbilityListener.
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof Arrow)) {
            return;
        }
        Player player = (Player) event.getEntity();
        ItemStack bow = event.getBow();
        Arrow arrow = (Arrow) event.getProjectile();

        if (bow == null || bow.getType() != Material.BOW || !bow.hasItemMeta()) {
            return; // Not a custom bow or not a bow at all
        }
        ItemMeta bowMeta = bow.getItemMeta();
        if (bowMeta == null) return;

        PersistentDataContainer bowPDC = bowMeta.getPersistentDataContainer();
        String itemId = bowPDC.get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);

        // If this bow is an INSTANT_SHOOT_BOW, its shots are handled by PlayerAbilityListener's right-click.
        // This event (EntityShootBowEvent) fires *after* drawing. We don't want to double-process or apply speed twice.
        // However, if a player *somehow* fully draws an instant bow, this event would still fire.
        // For simplicity, we assume instant bows are *only* fired via right-click.
        // If an INSTANT_SHOOT_BOW is *drawn*, it will behave like a normal bow here but still get speed bonus.

        if (itemId != null) { // It's a custom MMOCraft bow
            PlayerStats playerStats = statsManager.getStats(player);
            int shootingSpeedStat = playerStats.getShootingSpeed();

            // Apply SHOOTING_SPEED to projectile velocity for drawn custom bows
            if (shootingSpeedStat > 0) {
                double baseVelocityMultiplier = event.getForce() * 3.0D; // Vanilla scaling with draw force
                double shootingSpeedBonus = 1.0 + (shootingSpeedStat / 100.0);
                Vector newVelocity = player.getEyeLocation().getDirection().multiply(baseVelocityMultiplier * shootingSpeedBonus);
                arrow.setVelocity(newVelocity);
            }

            // Tree Bow specific logic for drawn shots (if it's not purely instant)
            // This part might be redundant if Tree Bow is exclusively instant-fire.
            // If Tree Bow *can* be drawn, this applies its magical ammo and power.
            if ("tree_bow".equalsIgnoreCase(itemId)) {
                boolean magicalAmmo = bowPDC.getOrDefault(NBTKeys.TREE_BOW_MAGICAL_AMMO_KEY, PersistentDataType.BYTE, (byte) 0) == 1;
                if (magicalAmmo) {
                    event.setConsumeItem(false); // Don't consume arrows from inventory
                    arrow.setPickupStatus(Arrow.PickupStatus.CREATIVE_ONLY);
                }

                PersistentDataContainer arrowPDC = arrow.getPersistentDataContainer();
                int powerMultiplier = bowPDC.getOrDefault(NBTKeys.TREE_BOW_POWER_KEY, PersistentDataType.INTEGER, 1);
                if (NBTKeys.PROJECTILE_DAMAGE_MULTIPLIER_KEY != null) {
                    arrowPDC.set(NBTKeys.PROJECTILE_DAMAGE_MULTIPLIER_KEY, PersistentDataType.INTEGER, powerMultiplier);
                }
                if (NBTKeys.PROJECTILE_SOURCE_BOW_TYPE_KEY != null) {
                    arrowPDC.set(NBTKeys.PROJECTILE_SOURCE_BOW_TYPE_KEY, PersistentDataType.STRING, "tree_bow");
                }
            } else if (NBTKeys.PROJECTILE_SOURCE_BOW_TYPE_KEY != null) {
                // Tag arrows from other custom bows
                arrow.getPersistentDataContainer().set(NBTKeys.PROJECTILE_SOURCE_BOW_TYPE_KEY, PersistentDataType.STRING, itemId);
            }
        }
        // Vanilla bows without custom NBT tags will behave normally.
    }
}
