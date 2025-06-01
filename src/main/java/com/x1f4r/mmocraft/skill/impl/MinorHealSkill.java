package com.x1f4r.mmocraft.skill.impl;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.playerdata.model.PlayerProfile;
import com.x1f4r.mmocraft.playerdata.model.Stat;
import com.x1f4r.mmocraft.skill.model.Skill;
import com.x1f4r.mmocraft.skill.model.SkillType;
import com.x1f4r.mmocraft.util.LoggingUtil;
import org.bukkit.attribute.Attribute;
import com.x1f4r.mmocraft.util.StringUtil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class MinorHealSkill extends Skill {

    private static final double BASE_HEAL_AMOUNT = 10.0;
    private static final double WISDOM_SCALING_FACTOR = 0.8;

    public MinorHealSkill(MMOCraftPlugin plugin) {
        super(plugin,
              "minor_heal",
              "Minor Heal",
              "Heals yourself for a small amount, scaled with Wisdom.",
              15.0,  // Mana cost
              8.0,   // Cooldown in seconds
              0.5,   // Cast time in seconds
              SkillType.ACTIVE_SELF);
    }

    @Override
    public boolean canUse(PlayerProfile casterProfile) {
        // Base implementation checks mana and cooldown.
        // No specific target needed for self-cast.
        return super.canUse(casterProfile);
    }

    @Override
    public void execute(PlayerProfile casterProfile, Entity target, Location targetLocation) {
        LoggingUtil logger = plugin.getLoggingUtil();
        Player casterPlayer = Bukkit.getPlayer(casterProfile.getPlayerUUID());

        if (casterPlayer == null) {
            logger.warning("MinorHealSkill: Caster player not found for UUID " + casterProfile.getPlayerUUID());
            return;
        }

        // Simulate cast time if > 0
        // For a real cast time, you'd schedule a task or use a movement listener to interrupt.
        // This is a simplified direct execution.
        if (this.getCastTimeSeconds() > 0) {
            // casterPlayer.sendMessage(StringUtil.colorize("&eCasting " + getSkillName() + "..."));
            // In a real scenario, you would delay the actual effect.
            // For this example, we'll just note it and proceed.
        }

        double healAmount = BASE_HEAL_AMOUNT + (casterProfile.getStatValue(Stat.WISDOM) * WISDOM_SCALING_FACTOR);
        healAmount = Math.max(0, healAmount); // Ensure heal isn't negative

        long oldHealth = casterProfile.getCurrentHealth();
        casterProfile.setCurrentHealth(Math.min(casterProfile.getMaxHealth(), oldHealth + (long)healAmount));
        long actualHeal = casterProfile.getCurrentHealth() - oldHealth;

        // Update Bukkit's health for the player
        double newBukkitHealth = Math.min(casterPlayer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(), casterPlayer.getHealth() + actualHeal);
        casterPlayer.setHealth(newBukkitHealth);

        casterPlayer.sendMessage(StringUtil.colorize("&aYou cast " + getSkillName() + " and healed yourself for &f" + String.format("%.1f", (double)actualHeal) + "&a health."));
        logger.info(casterPlayer.getName() + " used " + getSkillName() + ", healing for " + String.format("%.1f", (double)actualHeal) + ".");

        // Play sound and particle effects
        casterPlayer.getWorld().playSound(casterPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f); // Example sound
        // casterPlayer.getWorld().spawnParticle(org.bukkit.Particle.HEART, casterPlayer.getLocation().add(0,1,0), 5, 0.5, 0.5, 0.5);


        // Deduct mana
        casterProfile.setCurrentMana(casterProfile.getCurrentMana() - (long) this.getManaCost());
    }
}
