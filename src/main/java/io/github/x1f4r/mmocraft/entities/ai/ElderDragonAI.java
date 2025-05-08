package io.github.x1f4r.mmocraft.entities.ai;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
// No direct need for EntityManager here, core can provide if needed, but likely not
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
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ElderDragonAI extends BukkitRunnable {

    private final MMOCore core;
    private final MMOPlugin plugin;
    private final Logger log;
    private final UUID dragonUniqueId;
    private final Random random = new Random();

    // Cooldowns (in ticks)
    private int fireballVolleyCooldown = 0;
    private final int FIREBALL_VOLLEY_COOLDOWN_MAX = 20 * 10; // 10 seconds base

    private int lightningStrikeCooldown = 0;
    private final int LIGHTNING_STRIKE_COOLDOWN_MAX = 20 * 18; // 18 seconds base

    private int chargePlayerCooldown = 0;
    private final int CHARGE_PLAYER_COOLDOWN_MAX = 20 * 25; // 25 seconds base


    public ElderDragonAI(MMOCore core, EnderDragon dragon) {
        this.core = core;
        this.plugin = core.getPlugin();
        this.log = MMOPlugin.getMMOLogger();
        this.dragonUniqueId = dragon.getUniqueId();
    }

    @Override
    public void run() {
        org.bukkit.entity.Entity currentEntity = Bukkit.getEntity(dragonUniqueId);

        if (!(currentEntity instanceof EnderDragon) || !currentEntity.isValid() || currentEntity.isDead()) {
            log.info("Elder Dragon AI for " + dragonUniqueId + " stopping (Dragon invalid or gone).");
            this.cancel(); // Cancel this runnable
            // EntityManager is responsible for removing the task from its map via handleEntityDeath
            return;
        }
        EnderDragon dragon = (EnderDragon) currentEntity;

        // --- AI Logic ---
        fireballVolleyCooldown = Math.max(0, fireballVolleyCooldown - 20);
        lightningStrikeCooldown = Math.max(0, lightningStrikeCooldown - 20);
        chargePlayerCooldown = Math.max(0, chargePlayerCooldown - 20);

        List<Player> nearbyPlayers = dragon.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distanceSquared(dragon.getLocation()) < 100 * 100 &&
                             p.getGameMode() == org.bukkit.GameMode.SURVIVAL &&
                             !p.isDead())
                .collect(Collectors.toList());

        if (nearbyPlayers.isEmpty()) {
            return; // Skip combat logic if no players nearby
        }

        if (dragon.getPhase() != EnderDragon.Phase.DYING) {
            Player target = nearbyPlayers.get(random.nextInt(nearbyPlayers.size()));

            if (fireballVolleyCooldown <= 0 && random.nextInt(100) < 25) {
                performFireballVolley(dragon, nearbyPlayers);
                fireballVolleyCooldown = FIREBALL_VOLLEY_COOLDOWN_MAX + random.nextInt(20 * 5);
            } else if (lightningStrikeCooldown <= 0 && random.nextInt(100) < 15) {
                performLightningStrike(dragon, target);
                lightningStrikeCooldown = LIGHTNING_STRIKE_COOLDOWN_MAX + random.nextInt(20 * 8);
            } else if (chargePlayerCooldown <= 0 && random.nextInt(100) < 10) {
                 performChargePlayer(dragon, target);
                 chargePlayerCooldown = CHARGE_PLAYER_COOLDOWN_MAX + random.nextInt(20 * 10);
            }
        }
    }

    private void performFireballVolley(EnderDragon dragon, List<Player> targets) {
        if (targets.isEmpty() || !dragon.isValid()) return;
        log.finer("Elder Dragon performing Fireball Volley");
        // Consider broadcasting only to nearby players?
        dragon.getWorld().getPlayers().forEach(p -> p.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + dragon.getCustomName() + " unleashes a Fireball Volley!"));
        dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_GHAST_SHOOT, 2.0f, 0.5f);

        int volleyCount = 3 + random.nextInt(3);
        for (int i = 0; i < volleyCount; i++) {
             // Delay subsequent shots slightly?
             Bukkit.getScheduler().runTaskLater(plugin, () -> {
                 if (!dragon.isValid() || targets.isEmpty()) return; // Check validity again
                 Player target = targets.get(random.nextInt(targets.size()));
                 if (!target.isOnline()) return; // Check target validity

                 Location dragonEyeLoc = dragon.getEyeLocation();
                 Vector direction = target.getEyeLocation().toVector().subtract(dragonEyeLoc.toVector()).normalize();
                 direction.add(new Vector(random.nextDouble() * 0.2 - 0.1, random.nextDouble() * 0.2 - 0.1, random.nextDouble() * 0.2 - 0.1));

                 Fireball fireball = dragon.launchProjectile(Fireball.class, direction.multiply(1.5));
                 fireball.setShooter(dragon);
                 fireball.setYield(2.0F + random.nextFloat());
                 fireball.setIsIncendiary(false);

                 new BukkitRunnable() {
                     int ticks = 0;
                     @Override public void run() {
                         if (!fireball.isValid() || fireball.isDead() || ticks++ > 100) this.cancel();
                         else fireball.getWorld().spawnParticle(Particle.FLAME, fireball.getLocation(), 2, 0.1, 0.1, 0.1, 0.01);
                     }
                 }.runTaskTimer(plugin, 0L, 2L);
             }, i * 3L); // Stagger shots by 3 ticks
        }
    }

    private void performLightningStrike(EnderDragon dragon, Player target) {
         if (!dragon.isValid() || target == null || !target.isOnline()) return;
         log.finer("Elder Dragon performing Lightning Strike on " + target.getName());
         dragon.getWorld().getPlayers().forEach(p -> p.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + dragon.getCustomName() + " calls down lightning upon " + target.getName() + "!"));
         dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.5f, 1.0f);

         Location targetLoc = target.getLocation();
         int strikeCount = 1 + random.nextInt(3);
         for (int i = 0; i < strikeCount; i++) {
              Bukkit.getScheduler().runTaskLater(plugin, () -> {
                  if (!dragon.isValid() || !target.isOnline()) return; // Re-check validity
                  Location strikeLoc = targetLoc.clone().add(random.nextInt(11) - 5, 0, random.nextInt(11) - 5);
                  strikeLoc = strikeLoc.getWorld().getHighestBlockAt(strikeLoc).getLocation().add(0,1,0);
                  dragon.getWorld().strikeLightning(strikeLoc);
                  dragon.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, strikeLoc, 2, 0.5, 0.5, 0.5, 0);
              }, i * 5L); // Stagger strikes slightly
         }
    }

     private void performChargePlayer(EnderDragon dragon, Player target) {
         if (!dragon.isValid() || target == null || !target.isOnline()) return;
         log.finer("Elder Dragon performing Charge Player towards " + target.getName());
         dragon.getWorld().getPlayers().forEach(p -> p.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + dragon.getCustomName() + " focuses its rage on " + target.getName() + "!"));
         dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 2.0f, 0.7f);
         dragon.setPhase(EnderDragon.Phase.CHARGE_PLAYER);
     }
}
