package com.x1f4r.mmocraft.skill.impl;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.playerdata.model.PlayerProfile;
import com.x1f4r.mmocraft.playerdata.model.Stat;
import com.x1f4r.mmocraft.skill.model.Skill;
import com.x1f4r.mmocraft.skill.model.SkillType;
import com.x1f4r.mmocraft.util.LoggingUtil;
import com.x1f4r.mmocraft.util.StringUtil;

import org.bukkit.Bukkit;
// import org.bukkit.ChatColor; // Unused
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class StrongStrikeSkill extends Skill {

    private static final double BASE_SKILL_DAMAGE = 5.0; // Base damage added by the skill itself
    private static final double STRENGTH_SCALING_FACTOR = 1.2; // How much Strength contributes
    private static final double DAMAGE_MULTIPLIER = 1.5; // Overall multiplier for this skill

    public StrongStrikeSkill(MMOCraftPlugin plugin) {
        super(plugin, // Pass the plugin instance
              "strong_strike",
              "Strong Strike",
              "A powerful blow that deals " + DAMAGE_MULTIPLIER + "x increased physical damage based on Strength.",
              10.0,  // Mana cost
              5.0,   // Cooldown in seconds
              0.0,   // Cast time (instant)
              SkillType.ACTIVE_TARGETED_ENTITY);
    }

    @Override
    public boolean canUse(PlayerProfile casterProfile) {
        if (!super.canUse(casterProfile)) { // Checks mana and cooldown
            return false;
        }
        // Add specific checks if needed, e.g., requires melee weapon
        Player player = Bukkit.getPlayer(casterProfile.getPlayerUUID());
        if (player != null && player.getInventory().getItemInMainHand().getType().isAir()) {
            // player.sendMessage(StringUtil.colorize("&cYou need a weapon to use Strong Strike!"));
            // For now, allow unarmed, damage calculation will be low.
        }
        return true;
    }

    @Override
    public void execute(PlayerProfile casterProfile, Entity target, Location targetLocation) {
        LoggingUtil logger = plugin.getLoggingUtil(); // Get logger from plugin instance
        Player casterPlayer = Bukkit.getPlayer(casterProfile.getPlayerUUID());

        if (casterPlayer == null) {
            logger.warning("StrongStrikeSkill: Caster player not found for UUID " + casterProfile.getPlayerUUID());
            return;
        }

        if (!(target instanceof LivingEntity)) {
            casterPlayer.sendMessage(StringUtil.colorize("&cInvalid target for Strong Strike."));
            return;
        }
        LivingEntity victim = (LivingEntity) target;

        // 1. Calculate Skill's Effective Damage
        double skillDamageContribution = BASE_SKILL_DAMAGE + (casterProfile.getStatValue(Stat.STRENGTH) * STRENGTH_SCALING_FACTOR);
        double totalDamagePotential = skillDamageContribution * DAMAGE_MULTIPLIER;

        // For this example, we'll apply damage directly.
        // A more integrated system would use DamageCalculationService for applying defenses, crits etc.
        // Or, this skill could provide a temporary buff that PlayerCombatListener then picks up.

        // Simplified direct damage application for this example:
        // Check for critical hit (based on caster's stats)
        boolean isCrit = Math.random() < casterProfile.getCriticalHitChance();
        if (isCrit) {
            totalDamagePotential *= casterProfile.getCriticalDamageBonus();
        }

        // Apply victim's defenses (if victim is a player with a profile)
        PlayerProfile victimProfile = null;
        if (victim instanceof Player) {
            victimProfile = plugin.getPlayerDataService().getPlayerProfile(victim.getUniqueId());
        }

        double finalDamage;
        if (victimProfile != null) {
            // Check evasion first
            if (Math.random() < victimProfile.getEvasionChance()) {
                casterPlayer.sendMessage(StringUtil.colorize("&eYour Strong Strike was &fEVADED&e by " + victim.getName() + "!"));
                if (victim instanceof Player) {
                    ((Player) victim).sendMessage(StringUtil.colorize("&aYou &fEVADED&a a Strong Strike from " + casterPlayer.getName() + "!"));
                }
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1.0f, 1.0f); // Or a specific evade sound
                return; // No damage dealt
            }
            finalDamage = totalDamagePotential * (1 - victimProfile.getPhysicalDamageReduction());
        } else {
            // For mobs, assume no complex reduction for this direct damage skill example
            finalDamage = totalDamagePotential;
        }
        finalDamage = Math.max(0, finalDamage); // Ensure damage is not negative

        // Apply damage
        victim.damage(finalDamage, casterPlayer); // Bukkit's damage method, attacker is source

        String critTag = isCrit ? " &c(Critical!)" : "";
        casterPlayer.sendMessage(StringUtil.colorize("&aYou hit " + victim.getName() + " with Strong Strike for &f" + String.format("%.2f", finalDamage) + "&a damage!" + critTag));
        logger.info(casterPlayer.getName() + " used Strong Strike on " + victim.getName() + ", dealing " + String.format("%.2f", finalDamage) + " damage." + (isCrit ? " (CRIT)" : ""));

        // Play sound effect
        casterPlayer.getWorld().playSound(casterPlayer.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GENERIC_HURT, 1.0f, 1.0f);

        // Deduct mana (after successful execution)
        casterProfile.setCurrentMana(casterProfile.getCurrentMana() - (long) this.getManaCost());
    }
}
