package io.github.x1f4r.mmocraft.items;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.player.PlayerStats;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerAbilityListener implements Listener {

    private final MMOCraft plugin;
    private final PlayerStatsManager statsManager;
    private final Map<UUID, Long> instantBowCooldowns = new HashMap<>();

    private final int AOTE_TELEPORT_RANGE = 8;

    public PlayerAbilityListener(MMOCraft plugin) {
        this.plugin = plugin;
        this.statsManager = plugin.getPlayerStatsManager();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand == null || itemInHand.getType() == Material.AIR || !itemInHand.hasItemMeta()) {
            return;
        }
        ItemMeta meta = itemInHand.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING)) {
            return;
        }
        String itemId = meta.getPersistentDataContainer().get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);
        if (itemId == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Aspect of the Dragons
            if ("aspect_of_the_dragons".equalsIgnoreCase(itemId)) {
                event.setCancelled(true);
                handleAspectOfTheDragonsAbility(player, pdc);
                return; // Return to prevent other checks for this item
            }
            // Aspect of the End
            else if ("aspect_of_the_end".equalsIgnoreCase(itemId)) {
                event.setCancelled(true);
                handleAspectOfTheEndAbility(player, pdc);
                return; // Return
            }

            // Check for Instant Shoot Bow Tag
            if (NBTKeys.INSTANT_SHOOT_BOW_TAG != null && pdc.has(NBTKeys.INSTANT_SHOOT_BOW_TAG, PersistentDataType.BYTE) &&
                    pdc.get(NBTKeys.INSTANT_SHOOT_BOW_TAG, PersistentDataType.BYTE) == 1 &&
                    itemInHand.getType() == Material.BOW) { // Ensure it's actually a bow material

                event.setCancelled(true); // Prevent normal bow drawing
                handleInstantBowShot(player, itemInHand, pdc);
            }
        }
    }

    private void handleInstantBowShot(Player player, ItemStack bow, PersistentDataContainer bowPDC) {
        PlayerStats playerStats = statsManager.getStats(player);
        int shootingSpeedStat = playerStats.getShootingSpeed(); // Higher is faster fire rate

        // Calculate cooldown in ticks (20 ticks = 1 second)
        // Example: 0 shooting speed = 20 ticks (1s). 100 shooting speed = 10 ticks (0.5s). 200 shooting speed = 5 ticks (0.25s)
        // Max fire rate of 1 tick (20 arrows/sec) if shooting speed is very high.
        long cooldownTicks = Math.max(1, 20 - (shootingSpeedStat / 10)); // Each 10 shooting speed reduces cooldown by 1 tick from base 20.
        long cooldownMillis = cooldownTicks * 50; // Convert ticks to milliseconds

        long currentTime = System.currentTimeMillis();
        if (instantBowCooldowns.getOrDefault(player.getUniqueId(), 0L) + cooldownMillis > currentTime) {
            // player.sendMessage(ChatColor.RED + "Not ready to shoot yet!"); // Optional feedback
            return;
        }

        // Mana Cost (optional, can be added to items.yml for instant bows)
        int manaCost = 0;
        if (NBTKeys.MANA_COST_KEY != null && bowPDC.has(NBTKeys.MANA_COST_KEY, PersistentDataType.INTEGER)) {
            manaCost = bowPDC.get(NBTKeys.MANA_COST_KEY, PersistentDataType.INTEGER);
        }
        if (manaCost > 0 && !playerStats.consumeMana(manaCost)) {
            player.sendMessage(ChatColor.RED + "Not enough mana!");
            return;
        }

        // Arrow consumption logic
        boolean magicalAmmo = false;
        if (NBTKeys.TREE_BOW_MAGICAL_AMMO_KEY != null && bowPDC.has(NBTKeys.TREE_BOW_MAGICAL_AMMO_KEY, PersistentDataType.BYTE)) {
            magicalAmmo = bowPDC.get(NBTKeys.TREE_BOW_MAGICAL_AMMO_KEY, PersistentDataType.BYTE) == 1;
        }

        if (!magicalAmmo) {
            // Check for arrows and consume one if not magical ammo
            if (!player.getInventory().contains(Material.ARROW) && player.getGameMode() != GameMode.CREATIVE) {
                player.sendMessage(ChatColor.RED + "No arrows!");
                return;
            }
            if (player.getGameMode() != GameMode.CREATIVE) {
                player.getInventory().removeItem(new ItemStack(Material.ARROW, 1));
            }
        }

        // Launch arrow
        // Calculate velocity based on player's SHOOTING_SPEED stat for projectile speed
        double baseVelocityMultiplier = 3.0D; // Standard base for fully drawn bow
        double shootingSpeedBonus = 1.0 + (playerStats.getShootingSpeed() / 100.0); // e.g., 100 shooting speed = 2.0x velocity
        Vector velocity = player.getEyeLocation().getDirection().multiply(baseVelocityMultiplier * shootingSpeedBonus);

        Arrow arrow = player.launchProjectile(Arrow.class, velocity);
        arrow.setShooter(player);
        if (magicalAmmo) {
            arrow.setPickupStatus(Arrow.PickupStatus.CREATIVE_ONLY);
        }

        // Apply Tree Bow power if it's the Tree Bow
        String itemId = bowPDC.get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);
        if ("tree_bow".equalsIgnoreCase(itemId)) {
            PersistentDataContainer arrowPDC = arrow.getPersistentDataContainer();
            int powerMultiplier = bowPDC.getOrDefault(NBTKeys.TREE_BOW_POWER_KEY, PersistentDataType.INTEGER, 1);
            if (NBTKeys.PROJECTILE_DAMAGE_MULTIPLIER_KEY != null) {
                arrowPDC.set(NBTKeys.PROJECTILE_DAMAGE_MULTIPLIER_KEY, PersistentDataType.INTEGER, powerMultiplier);
            }
            if (NBTKeys.PROJECTILE_SOURCE_BOW_TYPE_KEY != null) {
                arrowPDC.set(NBTKeys.PROJECTILE_SOURCE_BOW_TYPE_KEY, PersistentDataType.STRING, "tree_bow");
            }
        } else if (itemId != null && NBTKeys.PROJECTILE_SOURCE_BOW_TYPE_KEY != null) {
            // Tag arrow from other custom instant bows
            arrow.getPersistentDataContainer().set(NBTKeys.PROJECTILE_SOURCE_BOW_TYPE_KEY, PersistentDataType.STRING, itemId);
        }


        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
        instantBowCooldowns.put(player.getUniqueId(), currentTime);
    }


    private void handleAspectOfTheDragonsAbility(Player player, PersistentDataContainer pdc) {
        PlayerStats stats = plugin.getPlayerStatsManager().getStats(player);
        int manaCost = 0;
        if (NBTKeys.MANA_COST_KEY != null && pdc.has(NBTKeys.MANA_COST_KEY, PersistentDataType.INTEGER)) {
            manaCost = pdc.get(NBTKeys.MANA_COST_KEY, PersistentDataType.INTEGER);
        } else if (NBTKeys.MANA_KEY != null && pdc.has(NBTKeys.MANA_KEY, PersistentDataType.INTEGER)) { // Fallback to MANA_KEY
            manaCost = pdc.get(NBTKeys.MANA_KEY, PersistentDataType.INTEGER);
        } else {
            manaCost = 100; // Default AOTD mana cost
        }

        if (!stats.consumeMana(manaCost)) {
            player.sendMessage(ChatColor.RED + "Not enough mana! (" + stats.getCurrentMana() + "/" + manaCost + ")");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "You unleash " + ChatColor.BOLD + "Dragon's Fury!");
        final double abilityBaseDamage = 20.0;
        final double strengthScaling = 0.5;

        player.getWorld().getNearbyEntities(player.getLocation().add(player.getLocation().getDirection().multiply(2)), 5, 5, 5,
                        entity -> entity instanceof LivingEntity && !entity.equals(player) && !(entity instanceof org.bukkit.entity.ArmorStand))
                .forEach(entity -> {
                    double damageToDeal = abilityBaseDamage + (stats.getStrength() * strengthScaling);
                    ((LivingEntity) entity).damage(damageToDeal, player);
                    if (entity.isValid()) {
                        entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_LARGE, entity.getLocation().add(0,1,0), 1);
                    }
                });
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.FLAME, player.getEyeLocation().add(player.getLocation().getDirection()), 30, 0.5, 0.5, 0.5, 0.1);
    }

    private void handleAspectOfTheEndAbility(Player player, PersistentDataContainer pdc) {
        PlayerStats stats = plugin.getPlayerStatsManager().getStats(player);
        int manaCost = 0;
        if (NBTKeys.MANA_COST_KEY != null && pdc.has(NBTKeys.MANA_COST_KEY, PersistentDataType.INTEGER)) {
            manaCost = pdc.get(NBTKeys.MANA_COST_KEY, PersistentDataType.INTEGER);
        } else if (NBTKeys.MANA_KEY != null && pdc.has(NBTKeys.MANA_KEY, PersistentDataType.INTEGER)) { // Fallback to MANA_KEY
            manaCost = pdc.get(NBTKeys.MANA_KEY, PersistentDataType.INTEGER);
        } else {
            manaCost = 50; // Default AOTE mana cost
        }

        if (!stats.consumeMana(manaCost)) {
            player.sendMessage(ChatColor.RED + "Not enough mana! (" + stats.getCurrentMana() + "/" + manaCost + ")");
            return;
        }

        Location originalPlayerLocation = player.getLocation().clone();
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection().normalize();
        Location targetTeleportLocation;

        RayTraceResult rayTrace = player.getWorld().rayTraceBlocks(
                eyeLocation,
                direction,
                AOTE_TELEPORT_RANGE,
                FluidCollisionMode.NEVER,
                true
        );

        if (rayTrace != null && rayTrace.getHitBlock() != null) {
            Block hitBlock = rayTrace.getHitBlock();
            BlockFace hitFace = rayTrace.getHitBlockFace();

            if (hitFace != null) {
                Location adjacentBlockLocation = hitBlock.getRelative(hitFace).getLocation();
                targetTeleportLocation = new Location(
                        adjacentBlockLocation.getWorld(),
                        adjacentBlockLocation.getX() + 0.5,
                        adjacentBlockLocation.getY(),
                        adjacentBlockLocation.getZ() + 0.5,
                        originalPlayerLocation.getYaw(),
                        originalPlayerLocation.getPitch()
                );
            } else {
                targetTeleportLocation = rayTrace.getHitPosition().toLocation(player.getWorld());
                targetTeleportLocation.setYaw(originalPlayerLocation.getYaw());
                targetTeleportLocation.setPitch(originalPlayerLocation.getPitch());
            }
        } else {
            targetTeleportLocation = originalPlayerLocation.clone().add(direction.multiply(AOTE_TELEPORT_RANGE));
        }

        player.teleport(targetTeleportLocation);
        player.setFallDistance(0f); // <-- ADDED LINE: Reset fall damage
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.PORTAL, eyeLocation, 30, 0.3, 0.5, 0.3, 0.2);
        player.getWorld().spawnParticle(Particle.PORTAL, player.getEyeLocation(), 30, 0.3, 0.5, 0.3, 0.2);
    }
}