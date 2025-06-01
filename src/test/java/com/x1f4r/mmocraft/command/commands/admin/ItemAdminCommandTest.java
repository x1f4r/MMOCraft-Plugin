package com.x1f4r.mmocraft.command.commands.admin;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.item.model.CustomItem;
import com.x1f4r.mmocraft.item.service.CustomItemRegistry;
import com.x1f4r.mmocraft.util.LoggingUtil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemAdminCommandTest {

    @Mock private MMOCraftPlugin mockPlugin;
    @Mock private CustomItemRegistry mockCustomItemRegistry;
    @Mock private LoggingUtil mockLogger;
    @Mock private CommandSender mockSender;
    @Mock private Player mockTargetPlayer;
    @Mock private PlayerInventory mockPlayerInventory;
    @Mock private Server mockServer; // For Bukkit.getPlayerExact
    @Mock private org.bukkit.command.Command mockBukkitCommand; // Added

    @Captor private ArgumentCaptor<String> messageCaptor;
    @Captor private ArgumentCaptor<ItemStack> itemStackCaptor;

    private ItemAdminCommand itemAdminCommand;
    private UUID targetPlayerUUID = UUID.randomUUID();
    private String targetPlayerName = "TargetPlayer";
    private String testItemId = "test_item_id";

    private static class TestItem extends CustomItem {
        public TestItem(MMOCraftPlugin plugin, String id, String name) {
            super(plugin);
            this.itemId = id;
            this.displayName = name;
        }
        private String itemId;
        private String displayName;
        @Override public String getItemId() { return itemId; }
        @Override public Material getMaterial() { return Material.STONE; }
        @Override public String getDisplayName() { return displayName; }
        @Override public List<String> getLore() { return Collections.emptyList(); }
    }

    @BeforeEach
    void setUp() {
        when(mockPlugin.getCustomItemRegistry()).thenReturn(mockCustomItemRegistry);
        when(mockPlugin.getLoggingUtil()).thenReturn(mockLogger);

        itemAdminCommand = new ItemAdminCommand(mockPlugin);

        lenient().when(mockTargetPlayer.getName()).thenReturn(targetPlayerName);
        lenient().when(mockTargetPlayer.getUniqueId()).thenReturn(targetPlayerUUID);
        lenient().when(mockTargetPlayer.getInventory()).thenReturn(mockPlayerInventory);
    }

    @Test
    void baseCommand_noArgs_sendsHelp() {
        itemAdminCommand.onCommand(mockSender, new String[]{}); // Simulating "/mmocadm item"
        verify(mockSender).sendMessage(contains("Item Admin Help"));
    }

    // --- GIVE Subcommand ---
    @Test
    void giveCmd_noPermission_sendsNoPermMessage() {
        when(mockSender.hasPermission("mmocraft.admin.item")).thenReturn(true); // Main command permission
        when(mockSender.hasPermission("mmocraft.admin.item.give")).thenReturn(false);
        itemAdminCommand.onCommand(mockSender, mockBukkitCommand, "itemadmin", new String[]{"give", "Player", "item_id"});
        verify(mockSender).sendMessage(ChatColor.RED + "You don't have permission for this command.");
    }

    @Test
    void giveCmd_notEnoughArgs_sendsUsage() {
        when(mockSender.hasPermission("mmocraft.admin.item")).thenReturn(true);
        when(mockSender.hasPermission("mmocraft.admin.item.give")).thenReturn(true);
        itemAdminCommand.onCommand(mockSender, mockBukkitCommand, "itemadmin", new String[]{"give", "PlayerOnly"});
        verify(mockSender).sendMessage(ChatColor.RED + "Usage: /mmocadm item give <playerName> <customItemId> [amount]");
    }

    @Test
    void giveCmd_playerOffline_sendsError() {
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayerExact("OfflinePlayer")).thenReturn(null);
            when(mockSender.hasPermission("mmocraft.admin.item")).thenReturn(true);
            when(mockSender.hasPermission("mmocraft.admin.item.give")).thenReturn(true);

            itemAdminCommand.onCommand(mockSender, mockBukkitCommand, "itemadmin", new String[]{"give", "OfflinePlayer", testItemId});
            verify(mockSender).sendMessage(ChatColor.RED + "Player 'OfflinePlayer' not found or not online.");
        }
    }

    @Test
    void giveCmd_itemNotFound_sendsError() {
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayerExact(targetPlayerName)).thenReturn(mockTargetPlayer);
            when(mockSender.hasPermission("mmocraft.admin.item")).thenReturn(true);
            when(mockSender.hasPermission("mmocraft.admin.item.give")).thenReturn(true);
            when(mockCustomItemRegistry.getCustomItem("unknown_item")).thenReturn(Optional.empty());

            itemAdminCommand.onCommand(mockSender, mockBukkitCommand, "itemadmin", new String[]{"give", targetPlayerName, "unknown_item"});
            verify(mockSender).sendMessage(ChatColor.RED + "Custom item with ID 'unknown_item' not found.");
        }
    }

    @Test
    void giveCmd_invalidAmount_sendsError() {
        when(mockSender.hasPermission("mmocraft.admin.item")).thenReturn(true);
        when(mockSender.hasPermission("mmocraft.admin.item.give")).thenReturn(true);
        // This test might not need Bukkit.getPlayerExact if it fails before that.
        // However, the command logic might try to resolve player first.
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayerExact(targetPlayerName)).thenReturn(mockTargetPlayer);
            itemAdminCommand.onCommand(mockSender, mockBukkitCommand, "itemadmin", new String[]{"give", targetPlayerName, testItemId, "not_a_number"});
        }
        verify(mockSender).sendMessage(ChatColor.RED + "Invalid amount: not_a_number");
    }

    @Test
    void giveCmd_validArgs_givesItemAndSendsConfirmation() {
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayerExact(targetPlayerName)).thenReturn(mockTargetPlayer);
            when(mockSender.hasPermission("mmocraft.admin.item.give")).thenReturn(true);

            CustomItem mockCustomItem = new TestItem(mockPlugin, testItemId, "&aTest Item");
            ItemStack expectedItemStack = new ItemStack(Material.STONE, 5); // Actual stack created by item.createItemStack

            when(mockCustomItemRegistry.getCustomItem(testItemId)).thenReturn(Optional.of(mockCustomItem));
            // We assume customItem.createItemStack(amount) works as tested in CustomItemTest
            // For this test, we can mock its return directly if we want to avoid NBTUtil/ItemMeta complexity here.
            // Or, more integrated: allow it to create real item & verify.
            // Let's mock the createItemStack for simpler verification of addItem.
            when(mockCustomItem.createItemStack(5)).thenReturn(expectedItemStack);


            itemAdminCommand.onCommand(mockSender, mockBukkitCommand, "itemadmin", new String[]{"give", targetPlayerName, testItemId, "5"});

            verify(mockPlayerInventory).addItem(itemStackCaptor.capture());
            assertSame(expectedItemStack, itemStackCaptor.getValue());

            verify(mockSender).sendMessage(messageCaptor.capture());
            assertTrue(messageCaptor.getValue().contains("Gave 5 of " + mockCustomItem.getDisplayName()));
            verify(mockTargetPlayer).sendMessage(contains("You received 5 of " + mockCustomItem.getDisplayName()));
            verify(mockLogger).info(contains(mockSender.getName() + " gave 5 of " + testItemId));
        }
    }

    @Test
    void tabComplete_give_customItemId_suggestsItemIds() {
        when(mockSender.hasPermission("mmocraft.admin.item.give")).thenReturn(true);
        CustomItem item1 = new TestItem(mockPlugin, "ruby_sword", "Ruby Sword");
        CustomItem item2 = new TestItem(mockPlugin, "sapphire_staff", "Sapphire Staff");
        when(mockCustomItemRegistry.getAllItems()).thenReturn(Arrays.asList(item1, item2));

        List<String> completions = itemAdminCommand.onTabComplete(mockSender, new String[]{"item", "give", "PlayerName", "ruby"});
        assertTrue(completions.contains("ruby_sword"));
        assertFalse(completions.contains("sapphire_staff"));
    }

    @Test
    void tabComplete_give_playerName_returnsNullForBukkitCompletion() {
         when(mockSender.hasPermission("mmocraft.admin.item.give")).thenReturn(true);
         List<String> completions = itemAdminCommand.onTabComplete(mockSender, new String[]{"item", "give", "Play"});
         assertNull(completions); // Bukkit handles player name completion
    }
}
