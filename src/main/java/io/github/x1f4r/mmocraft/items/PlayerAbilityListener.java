package io.github.x1f4r.mmocraft.items;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.player.PlayerStats;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class PlayerAbilityListener implements Listener {

    private final MMOCraft plugin;
    private final Map<UUID, Long> aotdCooldown = new HashMap<>();
    private final long AOTD_COOLDOWN_MS = 5000; // 5 seconds for AOTD

    private final Map<UUID, Long> aoteCooldown = new HashMap<>();
    private final long AOTE_COOLDOWN_MS = 1500; // 1.5 seconds for AOTE (configurable)
    private final int AOTE_TELEPORT_RANGE = 8; // Max 8 blocks

    public PlayerAbilityListener(MMOCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand == null || itemInHand.getType() == Material.AIR || !itemInHand.hasItemMeta()) {
            return;
        }
        ItemMeta meta = itemInHand.getItemMeta();
        // meta null check is good practice though hasItemMeta usually implies meta is not null
        if (meta == null || !meta.getPersistentDataContainer().has(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING)) {
            return;
        }
        String itemId = meta.getPersistentDataContainer().get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);
        if (itemId == null) return; // Should not happen if key exists

        PersistentDataContainer pdc = meta.getPersistentDataContainer(); // Get PDC once

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if ("aspect_of_the_dragons".equalsIgnoreCase(itemId)) {
                event.setCancelled(true);
                handleAspectOfTheDragonsAbility(player, itemInHand, pdc);
            } else if ("aspect_of_the_end".equalsIgnoreCase(itemId)) {
                event.setCancelled(true);
                handleAspectOfTheEndAbility(player, itemInHand, pdc);
            }
        }
    }

    private void handleAspectOfTheDragonsAbility(Player player, ItemStack item, PersistentDataContainer pdc) {
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (aotdCooldown.getOrDefault(playerUUID, 0L) + AOTD_COOLDOWN_MS > currentTime) {
            long timeLeft = ( (aotdCooldown.get(playerUUID) + AOTD_COOLDOWN_MS) - currentTime) / 1000;
            player.sendMessage(ChatColor.RED + "Dragon's Fury is on cooldown for " + Math.max(1, timeLeft) + "s!");
            return;
        }

        PlayerStats stats = plugin.getPlayerStatsManager().getStats(player);
        int manaCost = 0;
        if (NBTKeys.MANA_KEY != null && pdc.has(NBTKeys.MANA_KEY, PersistentDataType.INTEGER)) {
            manaCost = pdc.get(NBTKeys.MANA_KEY, PersistentDataType.INTEGER);
        }

        if (!stats.consumeMana(manaCost)) {
            player.sendMessage(ChatColor.RED + "Not enough mana! (" + stats.getCurrentMana() + "/" + manaCost + ")");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "You unleash " + ChatColor.BOLD + "Dragon's Fury!");
        // AOE Damage
        final double abilityBaseDamage = 20.0; // Base damage for AOTD ability
        final double strengthScaling = 0.5;   // 50% of strength added to ability damage
        player.getWorld().getNearbyEntities(player.getLocation().add(player.getLocation().getDirection().multiply(2)), 5, 5, 5,
                        entity -> entity instanceof LivingEntity && !entity.equals(player) && !(entity instanceof org.bukkit.entity.ArmorStand))
                .forEach(entity -> {
                    ((LivingEntity) entity).damage(abilityBaseDamage + (stats.getStrength() * strengthScaling), player);
                    if (entity.isValid()) { // Check if entity is still valid after damage
                        entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_LARGE, entity.getLocation().add(0,1,0), 1);
                    }
                });
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.FLAME, player.getEyeLocation().add(player.getLocation().getDirection()), 30, 0.5, 0.5, 0.5, 0.1);
        aotdCooldown.put(playerUUID, currentTime);
    }

    private void handleAspectOfTheEndAbility(Player player, ItemStack item, PersistentDataContainer pdc) {
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (aoteCooldown.getOrDefault(playerUUID, 0L) + AOTE_COOLDOWN_MS > currentTime) {
            long timeLeft = ((aoteCooldown.get(playerUUID) + AOTE_COOLDOWN_MS) - currentTime) / 1000;
            player.sendMessage(ChatColor.RED + "Instant Transmission is on cooldown for " + Math.max(1, timeLeft) + "s!");
            return;
        }

        PlayerStats stats = plugin.getPlayerStatsManager().getStats(player);
        int manaCost = 0;
        if (NBTKeys.MANA_KEY != null && pdc.has(NBTKeys.MANA_KEY, PersistentDataType.INTEGER)) {
            manaCost = pdc.get(NBTKeys.MANA_KEY, PersistentDataType.INTEGER);
        }

        if (!stats.consumeMana(manaCost)) {
            player.sendMessage(ChatColor.RED + "Not enough mana! (" + stats.getCurrentMana() + "/" + manaCost + ")");
            return;
        }

        Location originalLocation = player.getLocation().clone(); // Clone to preserve original pitch/yaw
        Location targetLocation = null;

        // Use Bukkit's ray tracing for blocks
        RayTraceResult rayTrace = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                AOTE_TELEPORT_RANGE,
                FluidCollisionMode.NEVER, // Don't pass through liquids to teleport
                true  // Ignore passable blocks like grass, flowers in the path, but will hit solid blocks
        );

        if (rayTrace != null && rayTrace.getHitBlock() != null) {
            // Player is looking at a block within range
            Block hitBlock = rayTrace.getHitBlock();
            BlockFace hitFace = rayTrace.getHitBlockFace();
            if (hitFace != null) {
                targetLocation = hitBlock.getRelative(hitFace).getLocation().add(0.5, 0, 0.5); // Center of block next to hit face
            } else { // Should usually have a hit face
                targetLocation = hitBlock.getLocation().add(0.5, 1.0, 0.5); // On top of block if no face
            }
        } else {
            // Player is looking at air or past max range, teleport max range in looking direction
            targetLocation = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(AOTE_TELEPORT_RANGE));
        }

        // Find the nearest safe spot (solid ground with 2 air blocks above)
        Location safeSpot = findSafeSpotNearby(targetLocation, player.getWorld());

        if (safeSpot != null) {
            safeSpot.setPitch(originalLocation.getPitch()); // Preserve original pitch
            safeSpot.setYaw(originalLocation.getYaw());   // Preserve original yaw

            player.teleport(safeSpot);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
            player.getWorld().spawnParticle(Particle.PORTAL, originalLocation.add(0,1,0), 30, 0.3, 0.5, 0.3, 0.2);
            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0,1,0), 30, 0.3, 0.5, 0.3, 0.2);
            aoteCooldown.put(playerUUID, currentTime);
        } else {
            player.sendMessage(ChatColor.RED + "Could not find a safe place to teleport!");
            stats.addMana(manaCost); // Refund mana
        }
    }

    private Location findSafeSpotNearby(Location target, World world) {
        // Check target directly
        if (isSafeLocation(target)) return target.add(0.5,0,0.5); // Center it

        // Check downwards for up to 3 blocks
        for (int i = 1; i <= 3; i++) {
            Location checkDown = target.clone().subtract(0, i, 0);
            if (isSafeLocation(checkDown)) return checkDown.add(0.5,0,0.5);
        }
        // Check upwards for up to 1 block (e.g. if target was in ground)
        Location checkUp = target.clone().add(0,1,0);
        if(isSafeLocation(checkUp)) return checkUp.add(0.5,0,0.5);

        return null; // No safe spot found easily
    }

    private boolean isSafeLocation(Location location) {
        if (location == null || location.getWorld() == null) return false;
        Block feet = location.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block ground = feet.getRelative(BlockFace.DOWN); // Block below feet

        // Feet and head must be non-solid (passable)
        // Ground must be solid to stand on
        return !feet.getType().isSolid() && feet.getType() != Material.LAVA &&
                !head.getType().isSolid() && head.getType() != Material.LAVA &&
                ground.getType().isSolid() && ground.getType() != Material.CACTUS; // Example of unsafe ground
    }
}