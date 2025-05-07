package io.github.x1f4r.mmocraft.player.listeners;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.player.PlayerStats;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Random;

public class PlayerDamageListener implements Listener {

    // private final MMOCraft plugin; // Not strictly needed if only using StatsManager
    private final PlayerStatsManager statsManager;
    private final Random random = new Random();

    public PlayerDamageListener(MMOCraft plugin) {
        // this.plugin = plugin;
        this.statsManager = plugin.getPlayerStatsManager();
    }

    @EventHandler(priority = EventPriority.NORMAL) // NORMAL allows other plugins to modify damage before/after
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return; 
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return; 
        }

        Player damager = (Player) event.getDamager();
        LivingEntity target = (LivingEntity) event.getEntity();
        PlayerStats damagerStats = statsManager.getStats(damager); // Get player's calculated stats

        double currentDamage = event.getDamage();

        // 1. Apply Custom Strength
        // This strength is an additive bonus on top of Minecraft's default damage calculations.
        currentDamage += damagerStats.getStrength();

        // 2. Apply Custom Critical Hit
        double critChancePercentage = damagerStats.getCritChance();
        if (critChancePercentage > 0 && random.nextDouble() * 100.0 < critChancePercentage) {
            // It's a crit!
            // Default Minecraft crit is 1.5x. Our critDamage is an *additional* percentage.
            // If base crit is 1.5x (this is implicit in vanilla if conditions met), 
            // and player has +50% crit damage stat, this could mean:
            // Option A: Total = BaseDamage * 1.5 * (1 + CustomCritDamage/100.0)
            // Option B: Total = BaseDamage * (1.5 + CustomCritDamage/100.0)
            // Option C: If vanilla crit didn't happen, our crit is BaseDamage * (1 + CustomCritDamage/100.0)
            // Let's go with a simple model: our crit damage stat directly boosts the damage by that percentage.
            // And we assume our crit is independent of vanilla's jump-crit.
            
            double critDamageMultiplier = 1.0 + (damagerStats.getCritDamage() / 100.0); // e.g., 50 stat -> 1.5x multiplier
            currentDamage *= critDamageMultiplier;

            // Visual/Audio Feedback for Crit
            damager.playSound(damager.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
            if (target.isValid()) { // Ensure target is still valid for particle effect
                target.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC, target.getEyeLocation(), 15, 0.5, 0.5, 0.5, 0.1);
            }
            // damager.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "CRITICAL HIT! " + ChatColor.GRAY + String.format("%.1f", currentDamage));
        }

        event.setDamage(Math.max(0.1, currentDamage)); // Ensure damage is at least a small amount, not negative
    }
}
