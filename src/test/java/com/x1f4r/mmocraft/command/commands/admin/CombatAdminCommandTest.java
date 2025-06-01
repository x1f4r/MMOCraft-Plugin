package com.x1f4r.mmocraft.command.commands.admin;

import com.x1f4r.mmocraft.combat.model.DamageInstance;
import com.x1f4r.mmocraft.combat.model.DamageType;
import com.x1f4r.mmocraft.combat.service.DamageCalculationService;
import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.playerdata.PlayerDataService; // Needed for DamageInstance context, though not directly mocked here
import com.x1f4r.mmocraft.playerdata.model.PlayerProfile;
import com.x1f4r.mmocraft.util.LoggingUtil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack; // Not directly used, but conceptually for weapon
import org.bukkit.inventory.PlayerInventory;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CombatAdminCommandTest {

    @Mock private MMOCraftPlugin mockPlugin;
    @Mock private DamageCalculationService mockDamageCalcService;
    @Mock private LoggingUtil mockLogger;
    @Mock private CommandSender mockSender;
    @Mock private Player mockAttackerPlayer;
    @Mock private Player mockVictimPlayer;
    @Mock private Server mockServer; // For Bukkit.getPlayerExact
    @Mock private org.bukkit.command.Command mockBukkitCommand; // Added

    @Captor private ArgumentCaptor<String> messageCaptor;

    private CombatAdminCommand combatAdminCommand;
    private UUID attackerUUID = UUID.randomUUID();
    private UUID victimUUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(mockPlugin.getDamageCalculationService()).thenReturn(mockDamageCalcService);
        when(mockPlugin.getLoggingUtil()).thenReturn(mockLogger);

        combatAdminCommand = new CombatAdminCommand(mockPlugin);

        lenient().when(mockAttackerPlayer.getName()).thenReturn("AttackerP");
        lenient().when(mockAttackerPlayer.getUniqueId()).thenReturn(attackerUUID);
        lenient().when(mockVictimPlayer.getName()).thenReturn("VictimP");
        lenient().when(mockVictimPlayer.getUniqueId()).thenReturn(victimUUID);
    }

    @Test
    void baseCommand_noArgs_sendsHelp() {
        combatAdminCommand.onCommand(mockSender, new String[]{}); // Simulating "/mmocadm combat"
        verify(mockSender).sendMessage(contains("Combat Admin Help"));
    }

    // --- TESTDAMAGE Subcommand ---
    @Test
    void testDamageCmd_noPermission_sendsNoPermMessage() {
        when(mockSender.hasPermission("mmocraft.admin.combat")).thenReturn(true); // Main command permission
        when(mockSender.hasPermission("mmocraft.admin.combat.testdamage")).thenReturn(false);
        // Assuming the subcommand itself checks its specific permission.
        combatAdminCommand.onCommand(mockSender, mockBukkitCommand, "combatadmin", new String[]{"testdamage", "AttackerP", "VictimP"});
        verify(mockSender).sendMessage(ChatColor.RED + "You don't have permission for this command.");
    }

    @Test
    void testDamageCmd_notEnoughArgs_sendsUsage() {
        when(mockSender.hasPermission("mmocraft.admin.combat")).thenReturn(true);
        when(mockSender.hasPermission("mmocraft.admin.combat.testdamage")).thenReturn(true);
        combatAdminCommand.onCommand(mockSender, mockBukkitCommand, "combatadmin", new String[]{"testdamage", "AttackerP"});
        verify(mockSender).sendMessage(ChatColor.RED + "Usage: /mmocadm combat testdamage <attackerPlayerName> <victimPlayerName> [weaponMaterialName]");
    }

    @Test
    void testDamageCmd_attackerOffline_sendsError() {
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayerExact("OfflineAttacker")).thenReturn(null);
            mockedBukkit.when(() -> Bukkit.getPlayerExact("VictimP")).thenReturn(mockVictimPlayer);
            when(mockSender.hasPermission("mmocraft.admin.combat")).thenReturn(true);
            when(mockSender.hasPermission("mmocraft.admin.combat.testdamage")).thenReturn(true);

            combatAdminCommand.onCommand(mockSender, mockBukkitCommand, "combatadmin", new String[]{"testdamage", "OfflineAttacker", "VictimP"});
            verify(mockSender).sendMessage(ChatColor.RED + "Attacker player 'OfflineAttacker' not found or not online.");
        }
    }

    @Test
    void testDamageCmd_victimOffline_sendsError() {
         try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayerExact("AttackerP")).thenReturn(mockAttackerPlayer);
            mockedBukkit.when(() -> Bukkit.getPlayerExact("OfflineVictim")).thenReturn(null);
            when(mockSender.hasPermission("mmocraft.admin.combat")).thenReturn(true);
            when(mockSender.hasPermission("mmocraft.admin.combat.testdamage")).thenReturn(true);

            combatAdminCommand.onCommand(mockSender, mockBukkitCommand, "combatadmin", new String[]{"testdamage", "AttackerP", "OfflineVictim"});
            verify(mockSender).sendMessage(ChatColor.RED + "Victim player 'OfflineVictim' not found or not online.");
        }
    }

    @Test
    void testDamageCmd_invalidWeaponMaterial_sendsError() {
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayerExact("AttackerP")).thenReturn(mockAttackerPlayer);
            mockedBukkit.when(() -> Bukkit.getPlayerExact("VictimP")).thenReturn(mockVictimPlayer);
            when(mockSender.hasPermission("mmocraft.admin.combat")).thenReturn(true);
            when(mockSender.hasPermission("mmocraft.admin.combat.testdamage")).thenReturn(true);

            combatAdminCommand.onCommand(mockSender, mockBukkitCommand, "combatadmin", new String[]{"testdamage", "AttackerP", "VictimP", "INVALID_STONE"});
            verify(mockSender).sendMessage(ChatColor.RED + "Invalid weapon material: INVALID_STONE");
        }
    }

    @Test
    void testDamageCmd_validPlayersAndWeapon_calculatesAndDisplaysDamage() {
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayerExact("AttackerP")).thenReturn(mockAttackerPlayer);
            mockedBukkit.when(() -> Bukkit.getPlayerExact("VictimP")).thenReturn(mockVictimPlayer);
            when(mockSender.hasPermission("mmocraft.admin.combat")).thenReturn(true);
            when(mockSender.hasPermission("mmocraft.admin.combat.testdamage")).thenReturn(true);

            DamageInstance fakeInstance = new DamageInstance(
                mockAttackerPlayer, mockVictimPlayer, attackerUUID, victimUUID, null, null,
                15.0, DamageType.PHYSICAL, true, false, "Reduced by 10%", 12.0
            );
            when(mockDamageCalcService.calculateDamage(
                eq(mockAttackerPlayer), eq(mockVictimPlayer), anyDouble(), eq(DamageType.PHYSICAL))
            ).thenReturn(fakeInstance);

            combatAdminCommand.onCommand(mockSender, mockBukkitCommand, "combatadmin", new String[]{"testdamage", "AttackerP", "VictimP", "DIAMOND_SWORD"});

            verify(mockDamageCalcService).calculateDamage(mockAttackerPlayer, mockVictimPlayer, 7.0, DamageType.PHYSICAL);
            verify(mockSender, times(8)).sendMessage(anyString()); // 1 title + 7 data lines
            verify(mockSender).sendMessage(contains("Final Damage: " + String.format("%.2f", 12.0)));
            verify(mockSender).sendMessage(contains("Critical Hit: &cYes"));
            verify(mockLogger).info(contains("performed damage test: " + fakeInstance.toString()));
        }
    }

     @Test
    void testDamageCmd_validPlayersUnarmed_calculatesAndDisplaysDamage() {
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayerExact("AttackerP")).thenReturn(mockAttackerPlayer);
            mockedBukkit.when(() -> Bukkit.getPlayerExact("VictimP")).thenReturn(mockVictimPlayer);
            when(mockSender.hasPermission("mmocraft.admin.combat")).thenReturn(true);
            when(mockSender.hasPermission("mmocraft.admin.combat.testdamage")).thenReturn(true);

            DamageInstance fakeInstance = new DamageInstance(
                mockAttackerPlayer, mockVictimPlayer, attackerUUID, victimUUID, null, null,
                1.0, DamageType.PHYSICAL, false, false, "", 0.5
            );
            // Default weapon is AIR (unarmed) with 1.0 base damage
            when(mockDamageCalcService.calculateDamage(
                eq(mockAttackerPlayer), eq(mockVictimPlayer), eq(1.0), eq(DamageType.PHYSICAL))
            ).thenReturn(fakeInstance);

            combatAdminCommand.onCommand(mockSender, mockBukkitCommand, "combatadmin", new String[]{"testdamage", "AttackerP", "VictimP"}); // No weapon specified

            verify(mockDamageCalcService).calculateDamage(mockAttackerPlayer, mockVictimPlayer, 1.0, DamageType.PHYSICAL);
            verify(mockSender).sendMessage(contains("Weapon Base: " + String.format("%.2f", 1.0) + " (&7Simulated AIR&7)"));
            verify(mockSender).sendMessage(contains("Final Damage: " + String.format("%.2f", 0.5)));
        }
    }

    @Test
    void tabComplete_testdamage_weaponMaterial_suggestsMaterials() {
        when(mockSender.hasPermission("mmocraft.admin.combat.testdamage")).thenReturn(true); // Used string literal
        List<String> completions = combatAdminCommand.onTabComplete(mockSender, mockBukkitCommand, "combatadmin", new String[]{"testdamage", "AttackerP", "VictimP", "DIA"});
        assertTrue(completions.contains("DIAMOND_SWORD"));
        assertTrue(completions.contains("DIAMOND_AXE"));
        assertFalse(completions.contains("IRON_SWORD"));
    }
}
