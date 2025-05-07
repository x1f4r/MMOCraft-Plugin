package io.github.x1f4r.mmocraft.mobs; // Updated package

import io.github.x1f4r.mmocraft.MMOCraft;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class ElderDragonAI extends BukkitRunnable {

    private final MMOCraft plugin;
    private final UUID dragonUniqueId;
    private EnderDragon dragon; // Instance of the dragon
    private final Random random = new Random();
    private int ticksLived = 0;

    // Ability cooldowns (in ticks)
    private int fireballVolleyCooldown = 0;
    private final int FIREBALL_VOLLEY_COOLDOWN_MAX = 20 * 10; // 10 seconds

    private int lightningStrikeCooldown = 0;
    private final int LIGHTNING_STRIKE_COOLDOWN_MAX = 20 * 18; // 18 seconds

    private int chargePlayerCooldown = 0;
    private final int CHARGE_PLAYER_COOLDOWN_MAX = 20 * 25; // 25 seconds


    public ElderDragonAI(MMOCraft plugin, EnderDragon dragon) {
        this.plugin = plugin;
        this.dragon = dragon;
        this.dragonUniqueId = dragon.getUniqueId();
    }

    @Override
    public void run() {
        // Re-fetch the dragon instance if it's null or invalid
        if (dragon == null || !dragon.isValid() || dragon.isDead()) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(dragonUniqueId);
            if (entity instanceof EnderDragon && entity.isValid() && !entity.isDead()) {
                dragon = (EnderDragon) entity;
            } else {
                this.cancel(); // Dragon is gone or dead, stop AI
                plugin.getLogger().info("Elder Dragon AI for " + dragonUniqueId + " stopped (dragon gone or dead).");
                return;
            }
        }

        ticksLived++;

        // --- Custom Targeting ---
        // Vanilla Ender Dragon has complex phases. We're adding abilities on top.
        // If you want to override phases, it's much more complex.
        // Example: dragon.setPhase(EnderDragon.Phase.CHARGE_PLAYER);

        List<Player> nearbyPlayers = dragon.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distanceSquared(dragon.getLocation()) < 100 * 100 && // 100 blocks range
                              p.getGameMode() == org.bukkit.GameMode.SURVIVAL &&
                              !p.isDead())
                .collect(Collectors.toList());

        if (nearbyPlayers.isEmpty() && dragon.getPhase() != EnderDragon.Phase.DYING) {
             // If no players nearby, maybe make it circle its home or a predefined point.
             // Or reset/despawn after a timeout if that's desired.
            if (dragon.getPhase() == EnderDragon.Phase.CIRCLING && dragon.getTarget() == null) {
                // dragon.setPhase(EnderDragon.Phase.LEAVE_PORTAL); // Or some other less aggressive phase
            }
            return; 
        }


        // Decrement Cooldowns
        if (fireballVolleyCooldown > 0) fireballVolleyCooldown--;
        if (lightningStrikeCooldown > 0) lightningStrikeCooldown--;
        if (chargePlayerCooldown > 0) chargePlayerCooldown--;


        // --- Custom Abilities ---
        // Only execute abilities if players are nearby and dragon is in a combat phase
        // --- Custom Abilities ---
        // Only execute abilities if players are nearby and dragon is in a combat phase
        if (!nearbyPlayers.isEmpty() &&
                (dragon.getPhase() == EnderDragon.Phase.CIRCLING ||
                 dragon.getPhase() == EnderDragon.Phase.CHARGE_PLAYER ||
                 dragon.getPhase() == EnderDragon.Phase.STRAFING ||       // Corrected
                 dragon.getPhase() == EnderDragon.Phase.FLY_TO_PORTAL ||  // Corrected
                 dragon.getPhase() == EnderDragon.Phase.BREATH_ATTACK ||  // Adding another common combat phase
                 dragon.getPhase() == EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET)) { // Adding another common combat phase

            // Ability 1: Fireball Volley
            if (fireballVolleyCooldown <= 0 && random.nextInt(100) < 25) { // 25% chance
                performFireballVolley(nearbyPlayers);
                fireballVolleyCooldown = FIREBALL_VOLLEY_COOLDOWN_MAX + random.nextInt(20 * 3); // Add some variance
            }

            // Ability 2: Lightning Strike
            if (lightningStrikeCooldown <= 0 && random.nextInt(100) < 15) { // 15% chance
                performLightningStrike(nearbyPlayers);
                lightningStrikeCooldown = LIGHTNING_STRIKE_COOLDOWN_MAX + random.nextInt(20 * 5);
            }
            
            // Ability 3: Charge Player (more aggressive targeting)
            // Note: Vanilla EnderDragon already has a charge phase. This could augment it.
            if (chargePlayerCooldown <= 0 && random.nextInt(100) < 10 && dragon.getPhase() != EnderDragon.Phase.CHARGE_PLAYER) { // 10% chance
                Player target = nearbyPlayers.get(random.nextInt(nearbyPlayers.size()));
                // dragon.setPhase(EnderDragon.Phase.CHARGE_PLAYER); // This might trigger vanilla charge
                // dragon.setTarget(target); // This might not directly work as expected for EnderDragon pathfinding
                Bukkit.broadcastMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + dragon.getCustomName() + " focuses its rage on " + target.getName() + "!");
                dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 2.0f, 0.7f);
                // For a custom "charge", you might apply a strong velocity towards the player
                // This is very basic and might look jerky:
                // Vector direction = target.getLocation().toVector().subtract(dragon.getLocation().toVector()).normalize();
                // dragon.setVelocity(direction.multiply(1.5)); // Adjust speed
                chargePlayerCooldown = CHARGE_PLAYER_COOLDOWN_MAX + random.nextInt(20 * 7);
            }
        }
    }

    private void performFireballVolley(List<Player> targets) {
        if (targets.isEmpty() || dragon == null || !dragon.isValid()) return;
        Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + dragon.getCustomName() + " unleashes a Fireball Volley!");
        dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_GHAST_SHOOT, 2.0f, 0.5f);

        for (int i = 0; i < 3 + random.nextInt(3); i++) { // 3-5 fireballs
            Player target = targets.get(random.nextInt(targets.size()));
            Location dragonEyeLoc = dragon.getEyeLocation();
            Vector direction = target.getEyeLocation().toVector().subtract(dragonEyeLoc.toVector()).normalize();

            Fireball fireball = dragon.launchProjectile(Fireball.class, direction.multiply(1.2)); // Adjust speed
            fireball.setShooter(dragon);
            fireball.setYield(1.5F + random.nextFloat()); // Smaller explosions than ghast
            fireball.setIsIncendiary(false); // Optional: set true for fire
            
             // Schedule a task to make fireball more visible or add trail
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (!fireball.isValid() || fireball.isDead() || ticks++ > 100) { // Max 5 seconds
                        this.cancel();
                        return;
                    }
                    fireball.getWorld().spawnParticle(Particle.FLAME, fireball.getLocation(), 3, 0.1, 0.1, 0.1, 0.01);
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }
    }

    private void performLightningStrike(List<Player> targets) {
        if (targets.isEmpty() || dragon == null || !dragon.isValid()) return;
        Player target = targets.get(random.nextInt(targets.size()));

        Bukkit.broadcastMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + dragon.getCustomName() + " calls down lightning upon " + target.getName() + "!");
        dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.5f, 1.0f);

        Location targetLoc = target.getLocation();
        // Strike around the player for AOE feel
        for (int i = 0; i < 1 + random.nextInt(3); i++) { // 1-3 bolts
            Location strikeLoc = targetLoc.clone().add(random.nextInt(10) - 5, 0, random.nextInt(10) - 5); // Strike within 5 block radius
            // Ensure strike location is safe (e.g., highest block) to avoid striking underground if player is in cave
            strikeLoc = strikeLoc.getWorld().getHighestBlockAt(strikeLoc).getLocation().add(0,1,0); 
            
            dragon.getWorld().strikeLightning(strikeLoc); // This does damage
            // dragon.getWorld().strikeLightningEffect(strikeLoc); // Effect only, no damage
            dragon.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, strikeLoc, 2, 0.5, 0.5, 0.5, 0);
        }
    }

    public void start() {
        // Run task more frequently for smoother AI checks, but less frequent for heavy abilities
        this.runTaskTimer(plugin, 20L, 20L); // Run every second (20 ticks)
        plugin.getLogger().info("Elder Dragon AI for " + dragonUniqueId + " started.");
    }
}
