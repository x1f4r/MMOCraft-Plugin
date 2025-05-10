package io.github.x1f4r.mmocraft.listeners; // General listeners package

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import io.github.x1f4r.mmocraft.stats.PlayerStats; // Correct import
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import io.github.x1f4r.mmocraft.core.MMOPlugin; // Import for logger

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

import java.util.logging.Logger;

public class BowListener implements Listener {

    // private final MMOCore core; // Removed
    private final PlayerStatsManager statsManager;
    private final Logger log;

    public BowListener(MMOCore core) {
        // this.core = core; // Removed assignment
        this.statsManager = core.getPlayerStatsManager();
        this.log = MMOPlugin.getMMOLogger();
    }

    // Handles bows that are DRAWN normally (not instant-fire via right-click)
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof Arrow)) {
            return;
        }
        Player player = (Player) event.getEntity();
        ItemStack bow = event.getBow();
        Arrow arrow = (Arrow) event.getProjectile();

        if (bow == null || bow.getType() != Material.BOW || !bow.hasItemMeta()) {
            return; // Not a bow or has no meta
        }
        ItemMeta bowMeta = bow.getItemMeta();
        if (bowMeta == null) return;

        PersistentDataContainer bowPDC = bowMeta.getPersistentDataContainer();
        String itemId = bowPDC.get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);

        // If it's not a custom MMOCraft bow, do nothing extra
        if (itemId == null || itemId.isEmpty()) {
            return;
        }

        // Check if this is an INSTANT_SHOOT bow being drawn normally.
        // PlayerAbilityListener handles the right-click instant shot.
        // If drawn, we still apply speed bonus but maybe log a warning?
        boolean isInstantBow = bowPDC.getOrDefault(NBTKeys.INSTANT_SHOOT_BOW_TAG, PersistentDataType.BYTE, (byte)0) == 1;
        if (isInstantBow) {
             log.finer("Player " + player.getName() + " drew an instant-fire bow (" + itemId + ") normally.");
             // Let it fire, but the primary mechanism is right-click via PlayerAbilityListener
        }

        PlayerStats playerStats = statsManager.getStats(player);
        int shootingSpeedStat = playerStats.getShootingSpeed();

        // Apply SHOOTING_SPEED to projectile velocity for drawn custom bows
        if (shootingSpeedStat != 0) { // Allow negative speed stat? Maybe clamp at 0?
            // Vanilla velocity is direction * force * 3.0
            // We modify this base velocity.
            double currentVelocityMagnitude = arrow.getVelocity().length();
            double shootingSpeedMultiplier = 1.0 + (shootingSpeedStat / 100.0); // Convert percentage to multiplier
            // Calculate new magnitude, ensuring it's not negative
            double newMagnitude = Math.max(0.1, currentVelocityMagnitude * shootingSpeedMultiplier); // Min speed threshold
            // Apply new magnitude to the original direction
            Vector newVelocity = arrow.getVelocity().normalize().multiply(newMagnitude);
            arrow.setVelocity(newVelocity);
            log.finer("Applied shooting speed (" + shootingSpeedStat + "%) to " + itemId + " arrow. New velocity factor: " + newMagnitude);
        }

        // Apply bow-specific NBT to the arrow
        PersistentDataContainer arrowPDC = arrow.getPersistentDataContainer();
        if (NBTKeys.PROJECTILE_SOURCE_ITEM_ID_KEY != null) {
             arrowPDC.set(NBTKeys.PROJECTILE_SOURCE_ITEM_ID_KEY, PersistentDataType.STRING, itemId);
        }
        // Tree Bow specific tags (Power multiplier, magical ammo)
        if ("tree_bow".equalsIgnoreCase(itemId)) {
            boolean magicalAmmo = bowPDC.getOrDefault(NBTKeys.TREE_BOW_MAGICAL_AMMO_KEY, PersistentDataType.BYTE, (byte) 0) == 1;
            if (magicalAmmo) {
                event.setConsumeItem(false); // Don't consume arrows from inventory if drawn
                arrow.setPickupStatus(Arrow.PickupStatus.CREATIVE_ONLY);
            }
            if (NBTKeys.PROJECTILE_DAMAGE_MULTIPLIER_KEY != null) {
                int powerMultiplier = bowPDC.getOrDefault(NBTKeys.TREE_BOW_POWER_KEY, PersistentDataType.INTEGER, 1);
                arrowPDC.set(NBTKeys.PROJECTILE_DAMAGE_MULTIPLIER_KEY, PersistentDataType.INTEGER, powerMultiplier);
            }
        }
         // Apply true damage flag from bow to arrow
         if (NBTKeys.TRUE_DAMAGE_FLAG_KEY != null && bowPDC.getOrDefault(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE, (byte)0) == 1) {
             arrowPDC.set(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE, (byte)1);
         }
    }
}
