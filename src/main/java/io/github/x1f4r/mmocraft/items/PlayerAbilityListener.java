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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class PlayerAbilityListener implements Listener {

    private final PlayerStatsManager statsManager;
    private final Logger log;
    private final MMOPlugin plugin; // Added for scheduler tasks

    // Cooldown map for abilities (Ability ID -> Player UUID -> Timestamp)
    private final Map<String, Map<UUID, Long>> abilityCooldowns = new HashMap<>();
    // Cooldown map specifically for instant bows (Player UUID -> Timestamp)
    private final Map<UUID, Long> instantBowCooldowns = new HashMap<>();

    // Configurable values (Load from config later?)
    private final int AOTE_TELEPORT_RANGE = 8;
    private final double AOTD_DAMAGE = 20.0;
    private final double AOTD_STRENGTH_SCALING = 0.5;
    private final double AOTD_RANGE = 5.0;

    private static final String ICE_BOLT_METADATA = "mmocraft_ice_bolt_dmg";
    private static final String ICE_BOLT_SLOW_DURATION = "mmocraft_ice_bolt_slow_dur";
    private static final String ICE_BOLT_SLOW_AMPLIFIER = "mmocraft_ice_bolt_slow_amp";


    public PlayerAbilityListener(MMOCore core) {
        this.statsManager = core.getPlayerStatsManager();
        this.log = MMOPlugin.getMMOLogger();
        this.plugin = core.getPlugin(); // Initialize plugin
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
            long cooldownMillis;
            if (pdc.has(NBTKeys.ABILITY_COOLDOWN_KEY, PersistentDataType.INTEGER)) {
                cooldownMillis = pdc.get(NBTKeys.ABILITY_COOLDOWN_KEY, PersistentDataType.INTEGER) * 1000L;
                log.finer("[AbilityListener] Using NBT cooldown for " + abilityId + ": " + cooldownMillis + "ms");
            } else {
                cooldownMillis = getAbilityCooldown(abilityId); // Fallback to hardcoded/default
                log.finer("[AbilityListener] Using default cooldown for " + abilityId + ": " + cooldownMillis + "ms (NBT not found)");
            }

            if (isOnCooldown(abilityId, player.getUniqueId(), cooldownMillis)) {
                // Optional: Send cooldown message
                long remainingCooldown = getRemainingCooldown(abilityId, player.getUniqueId(), cooldownMillis);
                player.sendMessage(Component.text("Ability on cooldown! (" + String.format("%.1f", remainingCooldown / 1000.0) + "s)", NamedTextColor.RED));
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
            case "lesser_ice_bolt":
            case "glacial_ice_bolt":
                success = executeIceBolt(player, pdc, abilityId.toLowerCase());
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
                // Determine cooldown duration again (could be from NBT or default)
                long currentCooldownDurationMillis;
                 if (pdc.has(NBTKeys.ABILITY_COOLDOWN_KEY, PersistentDataType.INTEGER)) {
                    currentCooldownDurationMillis = pdc.get(NBTKeys.ABILITY_COOLDOWN_KEY, PersistentDataType.INTEGER) * 1000L;
                } else {
                    currentCooldownDurationMillis = getAbilityCooldown(abilityId);
                }
                setCooldown(abilityId, player.getUniqueId(), currentCooldownDurationMillis);
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
        final int MAX_ITERATIONS_FOR_SAFE_SPOT = 5; // How many times to try stepping back

        log.finer("[AOTE] Player " + player.getName() + " using Instant Transmission from " + formatLocation(originalLoc));

        // Ray trace to find the target block or max distance
        RayTraceResult rayTrace = player.getWorld().rayTraceBlocks(eyeLoc, direction, AOTE_TELEPORT_RANGE, FluidCollisionMode.NEVER, true);

        Location initialTargetPoint;

        if (rayTrace != null && rayTrace.getHitBlock() != null) {
            initialTargetPoint = rayTrace.getHitPosition().toLocation(player.getWorld());
            log.finer("[AOTE] Raytrace hit block: " + rayTrace.getHitBlock().getType() + " at " + formatLocation(initialTargetPoint));
            // Start search from slightly before the hit block
            initialTargetPoint.subtract(direction.multiply(0.2)); // a bit further back than before
        } else {
            // No block hit, target max distance
            initialTargetPoint = originalLoc.clone().add(direction.multiply(AOTE_TELEPORT_RANGE));
            log.finer("[AOTE] Raytrace hit nothing, initial target is max range: " + formatLocation(initialTargetPoint));
        }

        // Iterate backwards from the initial target point to find a safe spot
        for (int i = 0; i < MAX_ITERATIONS_FOR_SAFE_SPOT; i++) {
            Location checkLocFeet = initialTargetPoint.clone(); // Base for feet
            Location checkLocHead = initialTargetPoint.clone().add(0, 1, 0); // Head position

            // Ensure the player doesn't teleport below the world or too high (e.g. build limit)
            if (checkLocFeet.getY() < player.getWorld().getMinHeight() || checkLocFeet.getY() > player.getWorld().getMaxHeight() -2) { // -2 to ensure space for head
                log.finer("[AOTE] Attempt " + i + ": Unsafe Y level at " + formatLocation(checkLocFeet) + ". Stepping back.");
                initialTargetPoint.subtract(direction.multiply(0.5)); // Step back further
                continue;
            }

            if (isSafeLocation(checkLocFeet) && isSafeLocation(checkLocHead)) {
                 // Check block below feet is solid
                Block blockBelow = checkLocFeet.clone().subtract(0, 1, 0).getBlock();
                if(!blockBelow.getType().isSolid() && blockBelow.getType() != Material.WATER) { // Allow teleporting onto water surface
                    log.finer("[AOTE] Attempt " + i + ": Unsafe, block below is not solid (" + blockBelow.getType() + ") at " + formatLocation(checkLocFeet) + ". Stepping back.");
                    initialTargetPoint.subtract(direction.multiply(0.5)); // Step back
                    continue;
                }
                targetLoc = checkLocFeet; // Found a safe spot!
                log.fine("[AOTE] Found safe teleport location after " + i + " iterations: " + formatLocation(targetLoc));
                break;
            } else {
                log.finer("[AOTE] Attempt " + i + ": Unsafe at " + formatLocation(checkLocFeet) + " (Feet: " + checkLocFeet.getBlock().getType() + ", Head: " + checkLocHead.getBlock().getType() + "). Stepping back.");
            }
            // If not safe, step back along the direction vector
            initialTargetPoint.subtract(direction.multiply(0.5)); // Step back by 0.5 blocks
        }


        if (targetLoc != null) {
            // Preserve original pitch and yaw
            targetLoc.setPitch(originalLoc.getPitch());
            targetLoc.setYaw(originalLoc.getYaw());

            // Center the player in the block
            targetLoc.setX(targetLoc.getBlockX() + 0.5);
            targetLoc.setZ(targetLoc.getBlockZ() + 0.5);
            // Y should remain as found by isSafeLocation

            player.teleport(targetLoc);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
            player.getWorld().spawnParticle(Particle.PORTAL, eyeLoc, 30, 0.3, 0.5, 0.3, 0.2);
            player.getWorld().spawnParticle(Particle.PORTAL, player.getEyeLocation(), 30, 0.3, 0.5, 0.3, 0.2);
            log.fine("[AOTE] Teleported " + player.getName() + " to " + formatLocation(targetLoc));
            return true; // Teleport successful
        } else {
            player.sendMessage(Component.text("Cannot teleport, destination blocked or unsafe!", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 1.0f);
            log.warning("[AOTE] Failed to find a safe teleport location for " + player.getName() + " after " + MAX_ITERATIONS_FOR_SAFE_SPOT + " iterations.");
            return false; // Teleport failed
        }
    }

    // Helper to check if a location is safe for teleport (passable blocks)
    private boolean isSafeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Block feetBlock = loc.getBlock();
        Block headBlock = loc.clone().add(0, 1, 0).getBlock();
        // Check if both feet and head space are passable (air, grass, water, etc.)
        // Allow teleporting into water
        return feetBlock.isPassable() && headBlock.isPassable();
    }

    // Helper method to format location for logging
    private String formatLocation(Location loc) {
        if (loc == null) return "null";
        return String.format("%.2f, %.2f, %.2f in %s", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld() != null ? loc.getWorld().getName() : "null_world");
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

    private long getRemainingCooldown(String abilityId, UUID playerId, long cooldownMillis) {
        if (cooldownMillis <= 0) return 0L;
        Map<UUID, Long> playerCooldowns = abilityCooldowns.get(abilityId);
        if (playerCooldowns == null) return 0L;
        long lastUsed = playerCooldowns.getOrDefault(playerId, 0L);
        if (lastUsed == 0L) return 0L;
        long timePassed = System.currentTimeMillis() - lastUsed;
        return Math.max(0L, cooldownMillis - timePassed);
    }

    private void setCooldown(String abilityId, UUID playerId, long currentCooldownDurationMillis) {
        // Only set cooldown if the ability is supposed to have one (duration > 0)
        if (currentCooldownDurationMillis > 0) {
            abilityCooldowns.computeIfAbsent(abilityId, k -> new HashMap<>()).put(playerId, System.currentTimeMillis());
            log.finer("[AbilityListener] Set cooldown for \"" + abilityId + "\" for player \"" + playerId + "\" to \"" + currentCooldownDurationMillis + "ms\"");
        } else {
            log.finer("[AbilityListener] Cooldown for \"" + abilityId + "\" is 0ms or less, not setting timestamp.");
        }
    }

    private boolean executeIceBolt(Player player, PersistentDataContainer itemPdc, String abilityId) {
        int abilityDamage = itemPdc.getOrDefault(NBTKeys.ABILITY_DAMAGE_KEY, PersistentDataType.INTEGER, 0);
        int slowDuration = "glacial_ice_bolt".equals(abilityId) ? 5 * 20 : 3 * 20; // 5s or 3s in ticks
        int slowAmplifier = "glacial_ice_bolt".equals(abilityId) ? 1 : 0; // Slowness II or I

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getEyeLocation(), 30, 0.5, 0.5, 0.5, 0.1);

        Snowball iceBolt = player.launchProjectile(Snowball.class);
        iceBolt.setShooter(player);
        iceBolt.setGravity(true); // Or false for a more "magical" straight shot initially
        iceBolt.setVelocity(player.getEyeLocation().getDirection().multiply(1.8)); // Adjust speed

        // Store damage and slow info on the projectile
        iceBolt.setMetadata(ICE_BOLT_METADATA, new FixedMetadataValue(plugin, abilityDamage));
        iceBolt.setMetadata(ICE_BOLT_SLOW_DURATION, new FixedMetadataValue(plugin, slowDuration));
        iceBolt.setMetadata(ICE_BOLT_SLOW_AMPLIFIER, new FixedMetadataValue(plugin, slowAmplifier));

        // Particle trail for the bolt
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!iceBolt.isValid() || iceBolt.isDead() || iceBolt.isOnGround()) {
                    this.cancel();
                    return;
                }
                iceBolt.getWorld().spawnParticle(Particle.ITEM_CRACK, iceBolt.getLocation(), 5, 0.1, 0.1, 0.1, 0.01, new ItemStack(Material.ICE));
                iceBolt.getWorld().spawnParticle(Particle.SNOWFLAKE, iceBolt.getLocation(), 2, 0,0,0, 0);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof Snowball) || !projectile.hasMetadata(ICE_BOLT_METADATA)) {
            return;
        }

        Snowball iceBolt = (Snowball) projectile;
        int damage = iceBolt.getMetadata(ICE_BOLT_METADATA).get(0).asInt();
        int slowDuration = iceBolt.getMetadata(ICE_BOLT_SLOW_DURATION).get(0).asInt();
        int slowAmplifier = iceBolt.getMetadata(ICE_BOLT_SLOW_AMPLIFIER).get(0).asInt();
        boolean isGlacial = slowAmplifier == 1; // Crude check if it was the stronger bolt

        Location hitLocation = iceBolt.getLocation();
        World world = iceBolt.getWorld();

        world.spawnParticle(Particle.SNOWBALL, hitLocation, 20, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.BLOCK_CRACK, hitLocation, 30, 0.5, 0.5, 0.5, 0.1, Material.ICE.createBlockData());
        world.playSound(hitLocation, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);


        if (event.getHitEntity() != null && event.getHitEntity() instanceof LivingEntity && event.getHitEntity() != projectile.getShooter()) {
            LivingEntity target = (LivingEntity) event.getHitEntity();
            target.damage(damage); // Direct damage, not going through PlayerDamageListener custom calcs for this "ability damage"
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, slowDuration, slowAmplifier));
            world.spawnParticle(Particle.CRIT_MAGIC, target.getEyeLocation(), 15, 0.3, 0.3, 0.3);
        }

        // Explosion effect on any hit (block or entity) for Glacial Scythe's stronger version
        if (isGlacial) {
            world.spawnParticle(Particle.EXPLOSION_LARGE, hitLocation, 1, 0,0,0,0);
            world.playSound(hitLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
            for (org.bukkit.entity.Entity nearbyEntity : world.getNearbyEntities(hitLocation, 3, 3, 3)) {
                if (nearbyEntity instanceof LivingEntity && nearbyEntity != projectile.getShooter()) {
                    LivingEntity nearbyTarget = (LivingEntity) nearbyEntity;
                    if (nearbyTarget != event.getHitEntity()) { // Don't double-damage the direct hit target
                         nearbyTarget.damage(damage); // Same damage as direct hit for AoE
                         nearbyTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, slowDuration / 2, slowAmplifier)); // Shorter slow for AoE
                    }
                }
            }
        }
        iceBolt.remove(); // Clean up snowball
    }
}
