package com.x1f4r.mmocraft.skill.impl;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.playerdata.PlayerDataService;
import com.x1f4r.mmocraft.playerdata.model.PlayerProfile;
import com.x1f4r.mmocraft.playerdata.model.Stat;
import com.x1f4r.mmocraft.skill.model.Skill;
import com.x1f4r.mmocraft.util.LoggingUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location; // Added
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity; // Added
import org.bukkit.entity.LivingEntity;
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
class StrongStrikeSkillTest {

    @Mock private MMOCraftPlugin mockPlugin;
    @Mock private LoggingUtil mockLogger;
    @Mock private PlayerDataService mockPlayerDataService;
    @Mock private Player mockCasterPlayer;
    @Mock private PlayerProfile mockCasterProfile;
    @Mock private LivingEntity mockTargetEntity;
    @Mock private Player mockTargetPlayer; // For testing player victim with profile
    @Mock private PlayerProfile mockVictimProfile;
    @Mock private Server mockServer;
    @Mock private World mockWorld;


    private StrongStrikeSkill strongStrikeSkill;
    private UUID casterUUID = UUID.randomUUID();
    private UUID targetUUID = UUID.randomUUID();
    private UUID targetPlayerUUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(mockPlugin.getLoggingUtil()).thenReturn(mockLogger);
        lenient().when(mockPlugin.getPlayerDataService()).thenReturn(mockPlayerDataService); // Lenient for tests not needing it

        strongStrikeSkill = new StrongStrikeSkill(mockPlugin);

        // Caster setup
        when(mockCasterProfile.getPlayerUUID()).thenReturn(casterUUID);
        lenient().when(mockCasterPlayer.getUniqueId()).thenReturn(casterUUID);
        lenient().when(mockCasterPlayer.getName()).thenReturn("CasterP");
        lenient().when(mockCasterPlayer.getWorld()).thenReturn(mockWorld); // For sound
        lenient().when(mockCasterProfile.getCurrentMana()).thenReturn(100L); // Corrected to 100L
        lenient().when(mockCasterProfile.isSkillOnCooldown(strongStrikeSkill.getSkillId())).thenReturn(false); // Not on cooldown

        // Target setup
        lenient().when(mockTargetEntity.getUniqueId()).thenReturn(targetUUID);
        lenient().when(mockTargetEntity.getName()).thenReturn("TargetMob");
        lenient().when(mockTargetEntity.getWorld()).thenReturn(mockWorld); // For sound

        lenient().when(mockTargetPlayer.getUniqueId()).thenReturn(targetPlayerUUID);
        lenient().when(mockTargetPlayer.getName()).thenReturn("TargetP");

        // Static mock for Bukkit.getPlayer is needed if the skill calls it directly.
        // The skill currently does: Player casterPlayer = Bukkit.getPlayer(casterProfile.getPlayerUUID());
    }

    @Test
    void canUse_sufficientManaAndNotOnCooldown_returnsTrue() {
        assertTrue(strongStrikeSkill.canUse(mockCasterProfile));
    }

    @Test
    void canUse_notEnoughMana_returnsFalse() {
        when(mockCasterProfile.getCurrentMana()).thenReturn(5L); // Corrected to 5L
        assertFalse(strongStrikeSkill.canUse(mockCasterProfile));
    }

    @Test
    void canUse_skillOnCooldown_returnsFalse() {
        when(mockCasterProfile.isSkillOnCooldown(strongStrikeSkill.getSkillId())).thenReturn(true);
        assertFalse(strongStrikeSkill.canUse(mockCasterProfile));
    }

    @Test
    void execute_targetNotLivingEntity_sendsMessageToCaster() {
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer(casterUUID)).thenReturn(mockCasterPlayer);
            Entity nonLivingTarget = mock(Entity.class); // Not LivingEntity

            strongStrikeSkill.execute(mockCasterProfile, nonLivingTarget, null);

            verify(mockCasterPlayer).sendMessage(contains("Invalid target"));
            verify(mockTargetEntity, never()).damage(anyDouble(), any(Entity.class));
        }
    }

    @Test
    void execute_validTargetMob_appliesDamageAndEffects() {
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer(casterUUID)).thenReturn(mockCasterPlayer);

            when(mockCasterProfile.getStatValue(Stat.STRENGTH)).thenReturn(10.0); // Base: 5 + 10*1.2 = 17. Multi: 17*1.5 = 25.5
            when(mockCasterProfile.getCriticalHitChance()).thenReturn(0.0); // No crit for predictable damage

            strongStrikeSkill.execute(mockCasterProfile, mockTargetEntity, null);

            verify(mockTargetEntity).damage(eq(25.5), eq(mockCasterPlayer));
            verify(mockCasterPlayer).sendMessage(contains("hit TargetMob with Strong Strike for 25.50 damage!"));
            verify(mockCasterProfile).setCurrentMana((long)(100.0 - strongStrikeSkill.getManaCost())); // Corrected
            verify(mockWorld).playSound(any(Location.class), eq(Sound.ENTITY_PLAYER_ATTACK_STRONG), anyFloat(), anyFloat());
        }
    }

    @Test
    void execute_criticalHit_appliesBonusDamage() {
         try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer(casterUUID)).thenReturn(mockCasterPlayer);

            when(mockCasterProfile.getStatValue(Stat.STRENGTH)).thenReturn(10.0); // Base skill damage part: 5 + 10*1.2 = 17. Total potential: 17 * 1.5 = 25.5
            when(mockCasterProfile.getCriticalHitChance()).thenReturn(1.0); // Guaranteed crit
            when(mockCasterProfile.getCriticalDamageBonus()).thenReturn(2.0); // 2x crit damage

            strongStrikeSkill.execute(mockCasterProfile, mockTargetEntity, null);

            double expectedDamage = 25.5 * 2.0; // 51.0
            verify(mockTargetEntity).damage(eq(expectedDamage), eq(mockCasterPlayer));
            verify(mockCasterPlayer).sendMessage(contains("(Critical!)"));
        }
    }

    @Test
    void execute_targetIsPlayer_appliesReductionsAndEvasion() {
         try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer(casterUUID)).thenReturn(mockCasterPlayer);
            mockedBukkit.when(() -> Bukkit.getPlayer(targetPlayerUUID)).thenReturn(mockTargetPlayer); // If skill needs it

            when(mockPlugin.getPlayerDataService().getPlayerProfile(targetPlayerUUID)).thenReturn(mockVictimProfile); // Corrected plugin to mockPlugin

            when(mockCasterProfile.getStatValue(Stat.STRENGTH)).thenReturn(20.0); // Base: 5 + 20*1.2 = 29. Multi: 29*1.5 = 43.5
            when(mockCasterProfile.getCriticalHitChance()).thenReturn(0.0);

            when(mockVictimProfile.getEvasionChance()).thenReturn(0.0); // No evasion for this path
            when(mockVictimProfile.getPhysicalDamageReduction()).thenReturn(0.2); // 20% reduction

            strongStrikeSkill.execute(mockCasterProfile, mockTargetPlayer, null); // Target is Player

            double expectedDamage = 43.5 * (1.0 - 0.2); // 43.5 * 0.8 = 34.8
            verify(mockTargetPlayer).damage(eq(expectedDamage), eq(mockCasterPlayer));
            verify(mockCasterPlayer).sendMessage(contains("hit TargetP with Strong Strike for 34.80 damage!"));
        }
    }

    @Test
    void execute_targetEvades_noDamageDealt() {
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer(casterUUID)).thenReturn(mockCasterPlayer);
            mockedBukkit.when(() -> Bukkit.getPlayer(targetPlayerUUID)).thenReturn(mockTargetPlayer); // If skill needs it for messages

            when(mockPlugin.getPlayerDataService().getPlayerProfile(targetPlayerUUID)).thenReturn(mockVictimProfile); // Corrected plugin to mockPlugin
            when(mockVictimProfile.getEvasionChance()).thenReturn(1.0); // Guaranteed evasion

            strongStrikeSkill.execute(mockCasterProfile, mockTargetPlayer, null);

            verify(mockTargetPlayer, never()).damage(anyDouble(), any(Entity.class));
            verify(mockCasterPlayer).sendMessage(contains("EVADED"));
        }
    }
}
