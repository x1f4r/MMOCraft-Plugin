package io.github.x1f4r.mmocraft.items; // Keep in items package

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import io.github.x1f4r.mmocraft.stats.PlayerStats; // Correct import
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class PlayerAbilityListener implements Listener {

    private final PlayerStatsManager statsManager;
    private final Logger log;

    // Cooldown map for abilities (Ability ID -> Player UUID -> Timestamp)
    private final Map<String, Map<UUID, Long>> abilityCooldowns = new HashMap<>();
    // Cooldown map specifically for instant bows (Player UUID -> Timestamp)
    private final Map<UUID, Long> instantBowCooldowns = new HashMap<>();

    // Configurable values (Load from config later?)
    private final int AOTE_TELEPORT_RANGE = 8;
    private final double AOTD_DAMAGE = 20.0;
    private final double AOTD_STRENGTH_SCALING = 0.5;
    private final double AOTD_RANGE = 5.0;


    public PlayerAbilityListener(MMOCore core) {
        this.statsManager = core.getPlayerStatsManager();
        this.log = MMOPlugin.getMMOLogger();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle main hand right-clicks for abilities for now
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand == null || itemInHand.getType() == Material.AIR || !itemInHand.hasItemMeta()) return;

        ItemMeta meta = itemInHand.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // --- Instant Bow Check ---
        // Check for the INSTANT_SHOOT_BOW tag specifically
        if (itemInHand.getType() == Material.BOW && pdc.has(NBTKeys.INSTANT_SHOOT_BOW_TAG, PersistentDataType.BYTE)) {
            if (pdc.getOrDefault(NBTKeys.INSTANT_SHOOT_BOW_TAG, PersistentDataType.BYTE, (byte)0) == 1) {
                event.setCancelled(true); // Prevent normal bow drawing
                handleInstantBowShot(player, itemInHand, pdc);
                return; // Don't process other abilities if it's an instant bow shot
            }
        }

        // --- Generic Ability Check ---
        // Check for the ABILITY_ID tag
        if (pdc.has(NBTKeys.ABILITY_ID_KEY, PersistentDataType.STRING)) {
            String abilityId = pdc.get(NBTKeys.ABILITY_ID_KEY, PersistentDataType.STRING);
            if (abilityId != null && !abilityId.isEmpty()) {
                event.setCancelled(true); // Cancel default interaction if ability exists
                handleGenericAbility(player, itemInHand, pdc, abilityId);
            }
        }
    }

    private void handleGenericAbility(Player player, ItemStack item, PersistentDataContainer pdc, String abilityId) {
        // --- START Cooldown Change ---
        // Check cooldowns *UNLESS* it's the instant_transmission ability
        if (!"instant_transmission".equalsIgnoreCase(abilityId)) {
            long cooldownMillis = getAbilityCooldown(abilityId);
            if (isOnCooldown(abilityId, player.getUniqueId(), cooldownMillis)) {
                // Optional: Send cooldown message
                // player.sendMessage(ChatColor.RED + "Ability is on cooldown!");
                return;
            }
        }
        // --- END Cooldown Change ---

        // Check mana cost
        PlayerStats stats = statsManager.getStats(player);
        int manaCost = pdc.getOrDefault(NBTKeys.MANA_COST_KEY, PersistentDataType.INTEGER, 0);
        if (manaCost > 0 && !stats.consumeMana(manaCost)) {
            player.sendMessage(Component.text("Not enough mana! (" + stats.getCurrentMana() + "/" + manaCost + ")", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            return;
        }

        // Execute ability based on ID
        boolean success = false;
        switch (abilityId.toLowerCase()) {
            case "dragon_fury":
                success = executeDragonFury(player, stats);
                break;
            case "instant_transmission":
                success = executeInstantTransmission(player);
                break;
            // Add cases for other ability IDs here
            // case "some_other_ability":
            //     success = executeSomeOtherAbility(player, stats, pdc);
            //     break;
            default:
                log.warning("Player " + player.getName() + " tried to use unknown ability ID: " + abilityId);
                // Refund mana if cost was deducted but ability is unknown
                if (manaCost > 0) stats.addMana(manaCost);
                return;
        }

        // If ability executed successfully, set cooldown
        if (success) {
            // --- START Cooldown Change ---
            // Set cooldown *UNLESS* it's the instant_transmission ability
            if (!"instant_transmission".equalsIgnoreCase(abilityId)) {
                setCooldown(abilityId, player.getUniqueId());
            }
            // --- END Cooldown Change ---
            player.setFallDistance(0.0f); // Prevent fall damage after ability use
        } else {
            // Refund mana if ability failed internally
            if (manaCost > 0) stats.addMana(manaCost);
        }
    }

    // --- Specific Ability Implementations ---

    private boolean executeDragonFury(Player player, PlayerStats stats) {
        player.sendMessage(Component.text("You unleash ", NamedTextColor.GOLD).append(Component.text("Dragon's Fury!").decorate(TextDecoration.BOLD)));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.FLAME, player.getEyeLocation().add(player.getLocation().getDirection()), 30, 0.5, 0.5, 0.5, 0.1);

        // Damage nearby entities
        player.getWorld().getNearbyEntities(player.getLocation().add(player.getLocation().getDirection().multiply(2)), AOTD_RANGE, AOTD_RANGE, AOTD_RANGE,
                        entity -> entity instanceof LivingEntity && !entity.equals(player) && !(entity instanceof ArmorStand))
                .forEach(entity -> {
                    double damageToDeal = AOTD_DAMAGE + (stats.getStrength() * AOTD_STRENGTH_SCALING);
                    ((LivingEntity) entity).damage(damageToDeal, player); // Let PlayerDamageListener handle defense etc.
                    if (entity.isValid()) {
                        entity.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, entity.getLocation().add(0, 1, 0), 1);
                    }
                });
        return true; // Indicate success
    }

    private boolean executeInstantTransmission(Player player) {
        Location originalLoc = player.getLocation().clone(); // Use clone to avoid modifying original object
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize();
        Location targetLoc = null;

        // Ray trace to find the target block or max distance
        RayTraceResult rayTrace = player.getWorld().rayTraceBlocks(eyeLoc, direction, AOTE_TELEPORT_RANGE, FluidCollisionMode.NEVER, true);

        if (rayTrace != null && rayTrace.getHitBlock() != null) {
            // Hit a block, find safe spot adjacent to hit face
            Block hitBlock = rayTrace.getHitBlock();
            BlockFace hitFace = rayTrace.getHitBlockFace();

            if (hitFace != null) {
                Location adjacentBlockLoc = hitBlock.getRelative(hitFace).getLocation();
                // Check if the space above the adjacent block is safe
                Location headLoc = adjacentBlockLoc.clone().add(0.5, 1.0, 0.5);
                Location feetLoc = adjacentBlockLoc.clone().add(0.5, 0.0, 0.5);

                if (isSafeLocation(feetLoc) && isSafeLocation(headLoc)) {
                    targetLoc = feetLoc;
                }
            }
            // Fallback if adjacent isn't safe or no hit face: try position before the block
            if (targetLoc == null) {
                targetLoc = rayTrace.getHitPosition().toLocation(player.getWorld()).subtract(direction.multiply(0.1)); // Move slightly back
                // Check safety of fallback location
                if (!isSafeLocation(targetLoc) || !isSafeLocation(targetLoc.clone().add(0,1,0))) {
                    targetLoc = null; // Fallback is also unsafe
                }
            }

        } else {
            // No block hit, teleport max distance
            Location maxDistLoc = originalLoc.clone().add(direction.multiply(AOTE_TELEPORT_RANGE));
            // Check safety of max distance location
            if (isSafeLocation(maxDistLoc) && isSafeLocation(maxDistLoc.clone().add(0,1,0))) {
                targetLoc = maxDistLoc;
            } else {
                // Try finding the last safe block along the path (more complex)
                // For simplicity, we might just disallow teleport if max distance is unsafe
                targetLoc = null;
            }
        }


        if (targetLoc != null) {
            // Preserve original pitch and yaw
            targetLoc.setPitch(originalLoc.getPitch());
            targetLoc.setYaw(originalLoc.getYaw());

            player.teleport(targetLoc);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
            player.getWorld().spawnParticle(Particle.PORTAL, eyeLoc, 30, 0.3, 0.5, 0.3, 0.2);
            player.getWorld().spawnParticle(Particle.PORTAL, player.getEyeLocation(), 30, 0.3, 0.5, 0.3, 0.2);
            return true; // Teleport successful
        } else {
            player.sendMessage(Component.text("Cannot teleport, destination blocked!", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 1.0f);
            return false; // Teleport failed
        }
    }

    // Helper to check if a location is safe for teleport (passable blocks)
    private boolean isSafeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Block feetBlock = loc.getBlock();
        Block headBlock = loc.clone().add(0, 1, 0).getBlock();
        // Check if both feet and head space are passable (air, grass, water, etc.)
        return feetBlock.isPassable() && !feetBlock.isLiquid() && headBlock.isPassable() && !headBlock.isLiquid();
    }


    // --- Instant Bow Shot Logic ---
    private void handleInstantBowShot(Player player, ItemStack bow, PersistentDataContainer bowPDC) {
        PlayerStats playerStats = statsManager.getStats(player);
        int shootingSpeedStat = playerStats.getShootingSpeed();

        // Calculate cooldown based on shooting speed (higher speed = lower cooldown)
        // Example: 0 speed = 20 ticks (1s). 100 speed = 10 ticks (0.5s). 200 speed = 0 ticks (capped at 1 tick).
        long cooldownTicks = Math.max(1, 20 - (shootingSpeedStat / 10));
        long cooldownMillis = cooldownTicks * 50; // Convert ticks to milliseconds

        long currentTime = System.currentTimeMillis();
        if (instantBowCooldowns.getOrDefault(player.getUniqueId(), 0L) + cooldownMillis > currentTime) {
            // Still on cooldown
            return;
        }

        int manaCost = bowPDC.getOrDefault(NBTKeys.MANA_COST_KEY, PersistentDataType.INTEGER, 0);
        if (manaCost > 0 && !playerStats.consumeMana(manaCost)) {
            player.sendMessage(Component.text("Not enough mana!", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            return;
        }

        boolean magicalAmmo = bowPDC.getOrDefault(NBTKeys.TREE_BOW_MAGICAL_AMMO_KEY, PersistentDataType.BYTE, (byte)0) == 1;

        if (!magicalAmmo) {
            if (!player.getInventory().contains(Material.ARROW) && player.getGameMode() != GameMode.CREATIVE) {
                player.sendMessage(Component.text("No arrows!", NamedTextColor.RED));
                if (manaCost > 0) playerStats.addMana(manaCost); // Refund mana
                return;
            }
            if (player.getGameMode() != GameMode.CREATIVE) {
                player.getInventory().removeItem(new ItemStack(Material.ARROW, 1));
            }
        }

        // Calculate velocity based on shooting speed
        double baseVelocityMultiplier = 3.0D; // Standard bow velocity multiplier
        double shootingSpeedBonus = 1.0 + (shootingSpeedStat / 100.0); // Apply speed stat as multiplier
        Vector velocity = player.getEyeLocation().getDirection().multiply(baseVelocityMultiplier * shootingSpeedBonus);

        Arrow arrow = player.launchProjectile(Arrow.class, velocity);
        arrow.setShooter(player);
        if (magicalAmmo) {
            arrow.setPickupStatus(Arrow.PickupStatus.CREATIVE_ONLY);
        }

        // Tag the arrow with bow info for damage calculation listener
        PersistentDataContainer arrowPDC = arrow.getPersistentDataContainer();
        String itemId = bowPDC.get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);
        if (itemId != null && NBTKeys.PROJECTILE_SOURCE_ITEM_ID_KEY != null) {
            arrowPDC.set(NBTKeys.PROJECTILE_SOURCE_ITEM_ID_KEY, PersistentDataType.STRING, itemId);
        }
        // Add Tree Bow specific power multiplier if applicable
        if ("tree_bow".equalsIgnoreCase(itemId) && NBTKeys.PROJECTILE_DAMAGE_MULTIPLIER_KEY != null) {
            int powerMultiplier = bowPDC.getOrDefault(NBTKeys.TREE_BOW_POWER_KEY, PersistentDataType.INTEGER, 1);
            arrowPDC.set(NBTKeys.PROJECTILE_DAMAGE_MULTIPLIER_KEY, PersistentDataType.INTEGER, powerMultiplier);
        }
        // Add true damage flag if bow has it
        if (NBTKeys.TRUE_DAMAGE_FLAG_KEY != null && bowPDC.getOrDefault(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE, (byte)0) == 1) {
            arrowPDC.set(NBTKeys.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE, (byte)1);
        }


        player.setFallDistance(0.0f); // Reset fall damage
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
        instantBowCooldowns.put(player.getUniqueId(), currentTime); // Set cooldown
    }


    // --- Cooldown Management ---
    private long getAbilityCooldown(String abilityId) {
        switch (abilityId.toLowerCase()) {
            case "dragon_fury": return 500; // 0.5 seconds example
            case "instant_transmission": return 0; // <<< SET COOLDOWN TO 0
            default: return 1000; // Default 1 second cooldown
        }
    }

    private boolean isOnCooldown(String abilityId, UUID playerId, long cooldownMillis) {
        // If cooldown is 0 or less, it's never on cooldown
        if (cooldownMillis <= 0) return false;

        Map<UUID, Long> playerCooldowns = abilityCooldowns.get(abilityId);
        if (playerCooldowns == null) {
            return false; // No cooldowns tracked for this ability yet
        }
        long lastUsed = playerCooldowns.getOrDefault(playerId, 0L);
        return (System.currentTimeMillis() - lastUsed) < cooldownMillis;
    }

    private void setCooldown(String abilityId, UUID playerId) {
        // Only set cooldown if the ability is supposed to have one (check getAbilityCooldown)
        if (getAbilityCooldown(abilityId) > 0) {
            abilityCooldowns.computeIfAbsent(abilityId, k -> new HashMap<>()).put(playerId, System.currentTimeMillis());
        }
    }
}
