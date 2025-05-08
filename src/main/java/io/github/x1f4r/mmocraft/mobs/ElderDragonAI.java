package io.github.x1f4r.mmocraft.mobs;

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
import org.bukkit.scheduler.BukkitTask; // Import BukkitTask
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class ElderDragonAI extends BukkitRunnable {

    private final MMOCraft plugin;
    private final UUID dragonUniqueId;
    private EnderDragon dragon;
    private final Random random = new Random();
    private int ticksLived = 0;

    private int fireballVolleyCooldown = 0;
    private final int FIREBALL_VOLLEY_COOLDOWN_MAX = 20 * 10;

    private int lightningStrikeCooldown = 0;
    private final int LIGHTNING_STRIKE_COOLDOWN_MAX = 20 * 18;

    private int chargePlayerCooldown = 0;
    private final int CHARGE_PLAYER_COOLDOWN_MAX = 20 * 25;


    public ElderDragonAI(MMOCraft plugin, EnderDragon dragon) {
        this.plugin = plugin;
        this.dragon = dragon;
        this.dragonUniqueId = dragon.getUniqueId();
    }

    @Override
    public void run() {
        // Re-fetch the dragon instance if it's null or invalid
        // This also handles the case where the dragon might have been removed by other means
        org.bukkit.entity.Entity currentEntity = Bukkit.getEntity(dragonUniqueId);
        if (!(currentEntity instanceof EnderDragon) || !currentEntity.isValid() || currentEntity.isDead()) {
            this.cancel(); // Dragon is gone or dead, stop AI
            plugin.getLogger().info("Elder Dragon AI for " + dragonUniqueId + " stopped (dragon gone, dead, or no longer EnderDragon type).");
            plugin.getActiveDragonAIs().remove(dragonUniqueId); // Also remove from the tracking map
            return;
        }
        // Update the local dragon reference if it changed (e.g., due to chunk load/unload, though less likely for EnderDragon)
        this.dragon = (EnderDragon) currentEntity;


        ticksLived++;

        List<Player> nearbyPlayers = dragon.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distanceSquared(dragon.getLocation()) < 100 * 100 &&
                        p.getGameMode() == org.bukkit.GameMode.SURVIVAL &&
                        !p.isDead())
                .collect(Collectors.toList());

        if (nearbyPlayers.isEmpty() && dragon.getPhase() != EnderDragon.Phase.DYING) {
            // No players nearby, dragon might become idle or perform non-combat actions
            // For now, just return to avoid running combat logic
            return;
        }


        if (fireballVolleyCooldown > 0) fireballVolleyCooldown--;
        if (lightningStrikeCooldown > 0) lightningStrikeCooldown--;
        if (chargePlayerCooldown > 0) chargePlayerCooldown--;


        if (!nearbyPlayers.isEmpty() &&
                (dragon.getPhase() == EnderDragon.Phase.CIRCLING ||
                        dragon.getPhase() == EnderDragon.Phase.CHARGE_PLAYER ||
                        dragon.getPhase() == EnderDragon.Phase.STRAFING ||
                        dragon.getPhase() == EnderDragon.Phase.FLY_TO_PORTAL ||
                        dragon.getPhase() == EnderDragon.Phase.BREATH_ATTACK ||
                        dragon.getPhase() == EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET)) {

            if (fireballVolleyCooldown <= 0 && random.nextInt(100) < 25) {
                performFireballVolley(nearbyPlayers);
                fireballVolleyCooldown = FIREBALL_VOLLEY_COOLDOWN_MAX + random.nextInt(20 * 3);
            }

            if (lightningStrikeCooldown <= 0 && random.nextInt(100) < 15) {
                performLightningStrike(nearbyPlayers);
                lightningStrikeCooldown = LIGHTNING_STRIKE_COOLDOWN_MAX + random.nextInt(20 * 5);
            }

            if (chargePlayerCooldown <= 0 && random.nextInt(100) < 10 && dragon.getPhase() != EnderDragon.Phase.CHARGE_PLAYER) {
                Player target = nearbyPlayers.get(random.nextInt(nearbyPlayers.size()));
                Bukkit.broadcastMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + dragon.getCustomName() + " focuses its rage on " + target.getName() + "!");
                dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 2.0f, 0.7f);
                // Setting phase might be enough, or you could manually guide it if vanilla charge isn't aggressive enough
                // dragon.setPhase(EnderDragon.Phase.CHARGE_PLAYER);
                // dragon.setTarget(target); // May not work as expected for EnderDragon pathfinding directly
                chargePlayerCooldown = CHARGE_PLAYER_COOLDOWN_MAX + random.nextInt(20 * 7);
            }
        }
    }

    private void performFireballVolley(List<Player> targets) {
        if (targets.isEmpty() || dragon == null || !dragon.isValid()) return;
        Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + dragon.getCustomName() + " unleashes a Fireball Volley!");
        dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_GHAST_SHOOT, 2.0f, 0.5f);

        for (int i = 0; i < 3 + random.nextInt(3); i++) {
            Player target = targets.get(random.nextInt(targets.size()));
            Location dragonEyeLoc = dragon.getEyeLocation();
            Vector direction = target.getEyeLocation().toVector().subtract(dragonEyeLoc.toVector()).normalize();

            Fireball fireball = dragon.launchProjectile(Fireball.class, direction.multiply(1.2));
            fireball.setShooter(dragon);
            fireball.setYield(1.5F + random.nextFloat());
            fireball.setIsIncendiary(false);

            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (!fireball.isValid() || fireball.isDead() || ticks++ > 100) {
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
        for (int i = 0; i < 1 + random.nextInt(3); i++) {
            Location strikeLoc = targetLoc.clone().add(random.nextInt(10) - 5, 0, random.nextInt(10) - 5);
            strikeLoc = strikeLoc.getWorld().getHighestBlockAt(strikeLoc).getLocation().add(0,1,0);

            dragon.getWorld().strikeLightning(strikeLoc);
            dragon.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, strikeLoc, 2, 0.5, 0.5, 0.5, 0);
        }
    }

    // Modified start method to return BukkitTask
    public BukkitTask start() {
        plugin.getLogger().info("Elder Dragon AI for " + dragonUniqueId + " is attempting to start.");
        return this.runTaskTimer(plugin, 20L, 20L); // Run every second
    }
}
