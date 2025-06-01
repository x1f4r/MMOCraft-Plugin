package com.x1f4r.mmocraft.command.commands;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.playerdata.PlayerDataService;
import com.x1f4r.mmocraft.playerdata.model.PlayerProfile;
import com.x1f4r.mmocraft.skill.model.Skill;
import com.x1f4r.mmocraft.skill.model.SkillType;
import com.x1f4r.mmocraft.skill.service.SkillRegistryService;
import com.x1f4r.mmocraft.skill.impl.StrongStrikeSkill; // Added import
import com.x1f4r.mmocraft.skill.impl.MinorHealSkill; // Added import
import com.x1f4r.mmocraft.util.LoggingUtil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays; // Added
import java.util.Collections;
import java.util.List;   // Added
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecuteSkillCommandTest {

    @Mock private MMOCraftPlugin mockPlugin;
    @Mock private SkillRegistryService mockSkillRegistry;
    @Mock private PlayerDataService mockPlayerDataService;
    @Mock private LoggingUtil mockLogger; // Though not directly used by command, plugin might use it

    @Mock private Player mockSenderPlayer; // The player executing /useskill
    @Mock private PlayerProfile mockSenderProfile;
    @Mock private Skill mockSkill;
    @Mock private LivingEntity mockTargetEntity; // For targeted skills
    @Mock private Server mockServer;


    private ExecuteSkillCommand executeSkillCommand;
    private UUID senderUUID = UUID.randomUUID();
    private String testSkillId = "test_skill";

    @BeforeEach
    void setUp() {
        when(mockPlugin.getSkillRegistryService()).thenReturn(mockSkillRegistry);
        when(mockPlugin.getPlayerDataService()).thenReturn(mockPlayerDataService);
        // when(mockPlugin.getLoggingUtil()).thenReturn(mockLogger); // If command needed it directly

        executeSkillCommand = new ExecuteSkillCommand(mockPlugin);

        when(mockSenderPlayer.getUniqueId()).thenReturn(senderUUID);
        when(mockPlayerDataService.getPlayerProfile(senderUUID)).thenReturn(mockSenderProfile);

        // Common skill setup
        lenient().when(mockSkill.getSkillId()).thenReturn(testSkillId);
        lenient().when(mockSkill.getSkillName()).thenReturn("Test Skill");
    }

    @Test
    void onCommand_notPlayer_sendsErrorMessage() {
        CommandSender mockNonPlayerSender = mock(CommandSender.class);
        executeSkillCommand.onCommand(mockNonPlayerSender, new String[]{testSkillId});
        verify(mockNonPlayerSender).sendMessage(ChatColor.RED + "This command can only be used by a player.");
    }

    @Test
    void onCommand_noSkillIdArg_sendsUsageMessage() {
        executeSkillCommand.onCommand(mockSenderPlayer, new String[]{});
        verify(mockSenderPlayer).sendMessage(ChatColor.RED + "Usage: /useskill <skillId> [targetName]");
    }

    @Test
    void onCommand_skillNotFound_sendsErrorMessage() {
        when(mockSkillRegistry.getSkill("unknown_skill")).thenReturn(Optional.empty());
        executeSkillCommand.onCommand(mockSenderPlayer, new String[]{"unknown_skill"});
        verify(mockSenderPlayer).sendMessage(ChatColor.RED + "Skill 'unknown_skill' not found.");
    }

    @Test
    void onCommand_cannotUseSkill_sendsCooldownMessage() {
        when(mockSkillRegistry.getSkill(testSkillId)).thenReturn(Optional.of(mockSkill));
        when(mockSkill.canUse(mockSenderProfile)).thenReturn(false);
        when(mockSenderProfile.isSkillOnCooldown(testSkillId)).thenReturn(true); // Specific reason: cooldown
        when(mockSenderProfile.getSkillRemainingCooldown(testSkillId)).thenReturn(5000L); // 5 seconds

        executeSkillCommand.onCommand(mockSenderPlayer, new String[]{testSkillId});
        verify(mockSenderPlayer).sendMessage(contains("is on cooldown for 5.0s"));
    }

    @Test
    void onCommand_cannotUseSkill_sendsNotEnoughManaMessage() {
        when(mockSkillRegistry.getSkill(testSkillId)).thenReturn(Optional.of(mockSkill));
        when(mockSkill.canUse(mockSenderProfile)).thenReturn(false);
        when(mockSenderProfile.isSkillOnCooldown(testSkillId)).thenReturn(false); // Not on cooldown
        when(mockSenderProfile.getCurrentMana()).thenReturn(5L); // Corrected to 5L
        when(mockSkill.getManaCost()).thenReturn(10.0);

        executeSkillCommand.onCommand(mockSenderPlayer, new String[]{testSkillId});
        verify(mockSenderPlayer).sendMessage(contains("Not enough mana"));
    }

    @Test
    void onCommand_targetedSkill_noTargetArg_sendsUsage() {
        when(mockSkillRegistry.getSkill(testSkillId)).thenReturn(Optional.of(mockSkill));
        when(mockSkill.canUse(mockSenderProfile)).thenReturn(true);
        when(mockSkill.getSkillType()).thenReturn(SkillType.ACTIVE_TARGETED_ENTITY);

        executeSkillCommand.onCommand(mockSenderPlayer, new String[]{testSkillId});
        verify(mockSenderPlayer).sendMessage(ChatColor.RED + "Usage: /useskill " + testSkillId + " <targetName>");
    }

    @Test
    void onCommand_targetedSkill_targetNotFound_sendsError() {
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayerExact("NonExistent")).thenReturn(null);
            // Also mock getNearbyEntities if that part of the code is reached
            when(mockSenderPlayer.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(Collections.emptyList());


            when(mockSkillRegistry.getSkill(testSkillId)).thenReturn(Optional.of(mockSkill));
            when(mockSkill.canUse(mockSenderProfile)).thenReturn(true);
            when(mockSkill.getSkillType()).thenReturn(SkillType.ACTIVE_TARGETED_ENTITY);

            executeSkillCommand.onCommand(mockSenderPlayer, new String[]{testSkillId, "NonExistent"});
            verify(mockSenderPlayer).sendMessage(ChatColor.RED + "Target 'NonExistent' not found or not online/nearby.");
        }
    }

    @Test
    void onCommand_selfSkill_executesAndPutsOnCooldown() {
        when(mockSkillRegistry.getSkill(testSkillId)).thenReturn(Optional.of(mockSkill));
        when(mockSkill.canUse(mockSenderProfile)).thenReturn(true);
        when(mockSkill.getSkillType()).thenReturn(SkillType.ACTIVE_SELF);

        executeSkillCommand.onCommand(mockSenderPlayer, new String[]{testSkillId});

        verify(mockSkill).execute(mockSenderProfile, null, null);
        verify(mockSkill).onCooldown(mockSenderProfile);
    }

    @Test
    void onCommand_targetedSkill_validTarget_executesAndPutsOnCooldown() {
         try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayerExact("TargetP")).thenReturn((Player)mockTargetEntity); // Assume target is a Player for this
            when(mockTargetEntity.getUniqueId()).thenReturn(UUID.randomUUID()); // Ensure target has a UUID

            when(mockSkillRegistry.getSkill(testSkillId)).thenReturn(Optional.of(mockSkill));
            when(mockSkill.canUse(mockSenderProfile)).thenReturn(true);
            when(mockSkill.getSkillType()).thenReturn(SkillType.ACTIVE_TARGETED_ENTITY);

            executeSkillCommand.onCommand(mockSenderPlayer, new String[]{testSkillId, "TargetP"});

            verify(mockSkill).execute(mockSenderProfile, mockTargetEntity, null);
            verify(mockSkill).onCooldown(mockSenderProfile);
        }
    }

    @Test
    void tabComplete_firstArg_suggestsSkillIds() {
        when(mockSkillRegistry.getAllSkills()).thenReturn(Arrays.asList(
            new StrongStrikeSkill(mockPlugin),
            new MinorHealSkill(mockPlugin)
        ));
        List<String> completions = executeSkillCommand.onTabComplete(mockSenderPlayer, new String[]{"str"});
        assertTrue(completions.contains("strong_strike"));
        assertFalse(completions.contains("minor_heal"));
    }

    @Test
    void tabComplete_secondArgForTargetedSkill_returnsNullForPlayerCompletion() {
        when(mockSkillRegistry.getSkill(testSkillId)).thenReturn(Optional.of(mockSkill));
        when(mockSkill.getSkillType()).thenReturn(SkillType.ACTIVE_TARGETED_ENTITY);

        List<String> completions = executeSkillCommand.onTabComplete(mockSenderPlayer, new String[]{testSkillId, "tar"});
        assertNull(completions); // Bukkit handles player name completion
    }
}
