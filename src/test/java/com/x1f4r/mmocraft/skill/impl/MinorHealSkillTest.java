package com.x1f4r.mmocraft.skill.impl;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.playerdata.model.PlayerProfile;
import com.x1f4r.mmocraft.playerdata.model.Stat;
import com.x1f4r.mmocraft.skill.model.Skill;
import com.x1f4r.mmocraft.util.LoggingUtil;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Location; // Added
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinorHealSkillTest {

    @Mock private MMOCraftPlugin mockPlugin;
    @Mock private LoggingUtil mockLogger;
    @Mock private Player mockCasterPlayer;
    @Mock private PlayerProfile mockCasterProfile;
    @Mock private Server mockServer;
    @Mock private World mockWorld;
    @Mock private AttributeInstance mockMaxHealthAttribute;


    private MinorHealSkill minorHealSkill;
    private UUID casterUUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(mockPlugin.getLoggingUtil()).thenReturn(mockLogger);
        minorHealSkill = new MinorHealSkill(mockPlugin);

        when(mockCasterProfile.getPlayerUUID()).thenReturn(casterUUID);
        lenient().when(mockCasterPlayer.getUniqueId()).thenReturn(casterUUID);
        lenient().when(mockCasterPlayer.getName()).thenReturn("Healer");
        lenient().when(mockCasterPlayer.getWorld()).thenReturn(mockWorld);
        lenient().when(mockCasterProfile.getCurrentMana()).thenReturn(100L); // Corrected to 100L
        lenient().when(mockCasterProfile.isSkillOnCooldown(minorHealSkill.getSkillId())).thenReturn(false); // Not on cooldown

        // Mock Bukkit.getPlayer()
        // This static mock is crucial because the skill uses it.
        // try-with-resources for static mock is good practice if you need to control its scope,
        // but for class-level consistent mocking, it can be done in @BeforeAll or per test.
        // For simplicity here, we'll use it where needed.
    }

    @Test
    void canUse_sufficientManaAndNotOnCooldown_returnsTrue() {
        assertTrue(minorHealSkill.canUse(mockCasterProfile));
    }

    @Test
    void canUse_notEnoughMana_returnsFalse() {
        when(mockCasterProfile.getCurrentMana()).thenReturn(5L); // Corrected to 5L
        assertFalse(minorHealSkill.canUse(mockCasterProfile));
    }

    @Test
    void execute_healsPlayerAndUpdatesBukkitHealth() {
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer(casterUUID)).thenReturn(mockCasterPlayer);

            when(mockCasterProfile.getStatValue(Stat.WISDOM)).thenReturn(10.0); // Heal: 10 + 10*0.8 = 18
            when(mockCasterProfile.getCurrentHealth()).thenReturn(50L);
            when(mockCasterProfile.getMaxHealth()).thenReturn(100L);

            // Mock Bukkit Player health methods
            when(mockCasterPlayer.getHealth()).thenReturn(10.0); // Bukkit health (scale 0-20 for 100HP usually)
            when(mockCasterPlayer.getAttribute(Attribute.MAX_HEALTH)).thenReturn(mockMaxHealthAttribute);
            when(mockMaxHealthAttribute.getValue()).thenReturn(20.0); // Standard Bukkit max health

            minorHealSkill.execute(mockCasterProfile, null, null); // Target and location are null for self-heal

            // Verify PlayerProfile health update
            long expectedProfileHealth = 50L + 18L;
            verify(mockCasterProfile).setCurrentHealth(expectedProfileHealth);

            // Verify Bukkit Player health update
            // Assume 1 game health = 1 Bukkit health point for this test.
            // If there's scaling (e.g. 100 game HP = 20 Bukkit HP), math needs adjustment.
            // The skill code does: casterPlayer.setHealth(Math.min(casterPlayer.getAttribute(...).getValue(), casterPlayer.getHealth() + actualHeal));
            // actualHeal is based on profile's health change. Let's say it was 18.
            // newBukkitHealth = min(20.0, 10.0 + 18) = min(20, 28) = 20
            verify(mockCasterPlayer).setHealth(20.0);

            verify(mockCasterPlayer).sendMessage(contains("healed yourself for 18.0 health"));
            verify(mockCasterProfile).setCurrentMana((long)(100.0 - minorHealSkill.getManaCost())); // Corrected
            verify(mockWorld).playSound(any(Location.class), eq(Sound.ENTITY_PLAYER_LEVELUP), anyFloat(), anyFloat());
        }
    }

    @Test
    void execute_healAmountExceedsMaxHealth_clampsHealth() {
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer(casterUUID)).thenReturn(mockCasterPlayer);

            when(mockCasterProfile.getStatValue(Stat.WISDOM)).thenReturn(20.0); // Heal: 10 + 20*0.8 = 26
            when(mockCasterProfile.getCurrentHealth()).thenReturn(90L);
            when(mockCasterProfile.getMaxHealth()).thenReturn(100L); // Only 10 HP room to heal

            when(mockCasterPlayer.getHealth()).thenReturn(18.0); // Assuming 20 max Bukkit HP
            when(mockCasterPlayer.getAttribute(Attribute.MAX_HEALTH)).thenReturn(mockMaxHealthAttribute);
            when(mockMaxHealthAttribute.getValue()).thenReturn(20.0);

            minorHealSkill.execute(mockCasterProfile, null, null);

            verify(mockCasterProfile).setCurrentHealth(100L); // Should be clamped to maxHealth
            // actualHeal would be 10L in this case.
            // newBukkitHealth = min(20.0, 18.0 + 10.0) = min(20.0, 28.0) = 20.0
            verify(mockCasterPlayer).setHealth(20.0);
            verify(mockCasterPlayer).sendMessage(contains("healed yourself for 10.0 health"));
        }
    }

    @Test
    void execute_casterPlayerNotFound_logsWarning() {
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer(casterUUID)).thenReturn(null); // Player not online

            minorHealSkill.execute(mockCasterProfile, null, null);

            verify(mockLogger).warning("MinorHealSkill: Caster player not found for UUID " + casterUUID);
            verify(mockCasterProfile, never()).setCurrentHealth(anyLong());
        }
    }
}
