package com.x1f4r.mmocraft.command.commands.admin;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.util.LoggingUtil;
import com.x1f4r.mmocraft.world.resourcegathering.model.ActiveResourceNode;
import com.x1f4r.mmocraft.world.resourcegathering.model.ResourceNodeType;
import com.x1f4r.mmocraft.world.resourcegathering.service.ActiveNodeManager;
import com.x1f4r.mmocraft.world.resourcegathering.service.ResourceNodeRegistryService;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor; // Added
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;


import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceAdminCommandTest {

    @Mock private MMOCraftPlugin mockPlugin;
    @Mock private ActiveNodeManager mockActiveNodeManager;
    @Mock private ResourceNodeRegistryService mockNodeRegistryService;
    @Mock private LoggingUtil mockLogger;

    @Mock private CommandSender mockSender;
    @Mock private Player mockPlayer;
    @Mock private Server mockServer;
    @Mock private World mockWorld;
    @Mock private Block mockBlock;

    private ResourceAdminCommand resourceAdminCommand;
    private ResourceNodeType testNodeType;

    @BeforeEach
    void setUp() {
        // It's important to mock Bukkit.getServer() if your command uses it.
        // This is a common pattern for Bukkit unit testing.
        // try (MockedStatic<Bukkit> mockedBukkit = Mockito.mockStatic(Bukkit.class)) {
        //    mockedBukkit.when(Bukkit::getServer).thenReturn(mockServer);
        //    when(mockServer.getWorld(anyString())).thenReturn(mockWorld); // Simplified world mocking
             // If you need specific world names:
             when(mockServer.getWorld("test_world")).thenReturn(mockWorld);
             when(mockWorld.getName()).thenReturn("test_world");
        // }
        // MMOCraftPlugin provides the logger, so no need to mock Bukkit.getLogger() directly
        // if the command correctly uses plugin.getLoggingUtil()
        lenient().when(mockPlugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("TestPluginLogger"));
        lenient().when(mockPlugin.getServer()).thenReturn(mockServer);


        lenient().when(mockPlugin.getActiveNodeManager()).thenReturn(mockActiveNodeManager);
        lenient().when(mockPlugin.getResourceNodeRegistryService()).thenReturn(mockNodeRegistryService);
        lenient().when(mockPlugin.getLoggingUtil()).thenReturn(mockLogger);


        resourceAdminCommand = new ResourceAdminCommand(mockPlugin, "mmocadm resource", "mmocraft.admin.resource");

        testNodeType = new ResourceNodeType("test_stone", Material.STONE, 5.0, Set.of(), "loot", 60);
        lenient().when(mockNodeRegistryService.getNodeType("test_stone")).thenReturn(Optional.of(testNodeType));
        lenient().when(mockNodeRegistryService.getNodeType("invalid_type")).thenReturn(Optional.empty());
        lenient().when(mockNodeRegistryService.getAllNodeTypes()).thenReturn(List.of(testNodeType));


        // Player specific mocks
        lenient().when(mockPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        lenient().when(mockPlayer.getName()).thenReturn("TestPlayer");
        lenient().when(mockPlayer.getWorld()).thenReturn(mockWorld);
        lenient().when(mockPlayer.getTargetBlockExact(5)).thenReturn(mockBlock);
        lenient().when(mockBlock.getLocation()).thenReturn(new Location(mockWorld, 1, 2, 3));

    }

    // Helper to simulate Bukkit's static methods if needed deeper
    private void mockBukkitStatic() {
        try (MockedStatic<Bukkit> mockedBukkit = Mockito.mockStatic(Bukkit.class)) {
            mockedBukkit.when(Bukkit::getServer).thenReturn(mockServer);
            // If worlds are looked up by name:
            mockedBukkit.when(() -> Bukkit.getWorld("test_world")).thenReturn(mockWorld);
            // If worlds are iterated:
            lenient().when(mockServer.getWorlds()).thenReturn(List.of(mockWorld));
        }
    }


    @Test
    void onCommand_noArgs_sendsHelp() {
        resourceAdminCommand.onCommand(mockSender, new String[]{}); // Changed to onCommand
        verify(mockSender).sendMessage(contains("--- Resource Admin Commands ---"));
    }

    @Test
    void onCommand_unknownSubCommand_sendsHelp() {
        resourceAdminCommand.onCommand(mockSender, new String[]{"unknown"}); // Changed to onCommand
        verify(mockSender).sendMessage(contains("Unknown subcommand 'unknown'"));
        verify(mockSender).sendMessage(contains("--- Resource Admin Commands ---"));
    }

    // --- Place Command Tests ---
    @Test
    void placeCommand_validArgsWithCoords_callsPlaceNewNode() {
        mockBukkitStatic(); // Ensure Bukkit.getWorld is mocked for this call path
        resourceAdminCommand.onCommand(mockSender, new String[]{"place", "test_stone", "test_world", "10", "20", "30"}); // Changed to onCommand
        ArgumentCaptor<Location> locCaptor = ArgumentCaptor.forClass(Location.class);
        verify(mockActiveNodeManager).placeNewNode(locCaptor.capture(), eq("test_stone"));
        assertEquals("test_world", locCaptor.getValue().getWorld().getName());
        assertEquals(10, locCaptor.getValue().getX());
        verify(mockSender).sendMessage(contains("Placed resource node 'test_stone'"));
    }

    @Test
    void placeCommand_playerTargetsBlock_callsPlaceNewNode() {
        resourceAdminCommand.onCommand(mockPlayer, new String[]{"place", "test_stone"}); // Changed to onCommand
        verify(mockActiveNodeManager).placeNewNode(eq(mockBlock.getLocation()), eq("test_stone"));
        verify(mockPlayer).sendMessage(contains("Placed resource node 'test_stone'"));
    }

    @Test
    void placeCommand_invalidNodeType_sendsError() {
        resourceAdminCommand.onCommand(mockSender, new String[]{"place", "invalid_type", "test_world", "10", "20", "30"}); // Changed to onCommand
        verify(mockSender).sendMessage(contains("Invalid ResourceNodeType ID: 'invalid_type'"));
        verify(mockActiveNodeManager, never()).placeNewNode(any(), anyString());
    }

    @Test
    void placeCommand_invalidWorld_sendsError() {
        mockBukkitStatic();
        when(Bukkit.getWorld("bad_world")).thenReturn(null); // Specific mock for this test
        resourceAdminCommand.onCommand(mockSender, new String[]{"place", "test_stone", "bad_world", "10", "20", "30"}); // Changed to onCommand
        verify(mockSender).sendMessage(contains("World 'bad_world' not found."));
    }

    @Test
    void placeCommand_invalidCoords_sendsError() {
        resourceAdminCommand.onCommand(mockSender, new String[]{"place", "test_stone", "test_world", "ten", "20", "30"}); // Changed to onCommand
        verify(mockSender).sendMessage(contains("Invalid coordinates."));
    }

    @Test
    void placeCommand_consoleTargetsBlock_sendsError() {
        resourceAdminCommand.onCommand(mockSender, new String[]{"place", "test_stone"}); // Changed to onCommand
        verify(mockSender).sendMessage(contains("Console must specify world and coordinates."));
    }

    @Test
    void placeCommand_playerNoTarget_sendsError() {
        when(mockPlayer.getTargetBlockExact(5)).thenReturn(null);
        resourceAdminCommand.onCommand(mockPlayer, new String[]{"place", "test_stone"}); // Changed to onCommand
        verify(mockPlayer).sendMessage(contains("You are not looking at a block"));
    }

    @Test
    void placeCommand_notEnoughArgs_sendsError() {
        resourceAdminCommand.onCommand(mockSender, new String[]{"place"}); // Changed to onCommand
        verify(mockSender).sendMessage(contains("Usage: /mmocadm resource place <nodeTypeId>"));
    }

    // --- Remove Command Tests ---
    @Test
    void removeCommand_playerTargetsNode_callsRemoveNode() {
        ActiveResourceNode activeNode = new ActiveResourceNode(mockBlock.getLocation(), "test_stone");
        when(mockActiveNodeManager.getActiveNode(mockBlock.getLocation())).thenReturn(Optional.of(activeNode));
        when(mockActiveNodeManager.removeNode(mockBlock.getLocation())).thenReturn(true);

        resourceAdminCommand.onCommand(mockPlayer, new String[]{"remove"}); // Changed to onCommand
        verify(mockActiveNodeManager).removeNode(mockBlock.getLocation());
        verify(mockPlayer).sendMessage(contains("Successfully removed resource node"));
    }

    @Test
    void removeCommand_playerTargetsNonNode_sendsError() {
        when(mockActiveNodeManager.getActiveNode(mockBlock.getLocation())).thenReturn(Optional.empty());
        resourceAdminCommand.onCommand(mockPlayer, new String[]{"remove"}); // Changed to onCommand
        verify(mockPlayer).sendMessage(contains("No active resource node found"));
        verify(mockActiveNodeManager, never()).removeNode(any());
    }

    @Test
    void removeCommand_console_sendsError() {
        resourceAdminCommand.onCommand(mockSender, new String[]{"remove"}); // Changed to onCommand
        verify(mockSender).sendMessage(contains("This command must be run by a player"));
    }

    // --- Info Command Tests ---
    @Test
    void infoCommand_playerTargetsNode_sendsInfo() {
        ActiveResourceNode activeNode = new ActiveResourceNode(mockBlock.getLocation(), "test_stone");
        activeNode.setDepleted(false);
        when(mockActiveNodeManager.getActiveNode(mockBlock.getLocation())).thenReturn(Optional.of(activeNode));
        when(mockNodeRegistryService.getNodeType("test_stone")).thenReturn(Optional.of(testNodeType));

        resourceAdminCommand.onCommand(mockPlayer, new String[]{"info"}); // Changed to onCommand
        verify(mockPlayer, atLeast(1)).sendMessage(anyString()); // Corrected Mockito verification
        verify(mockPlayer).sendMessage(contains("Node Type ID: &ftest_stone"));
        verify(mockPlayer).sendMessage(contains("Is Depleted: &ffalse"));
    }

    @Test
    void infoCommand_playerTargetsDepletedNode_sendsInfoWithRespawn() {
        ActiveResourceNode activeNode = new ActiveResourceNode(mockBlock.getLocation(), "test_stone");
        activeNode.setDepleted(true);
        activeNode.setRespawnAtMillis(System.currentTimeMillis() + 50000); // 50s
        when(mockActiveNodeManager.getActiveNode(mockBlock.getLocation())).thenReturn(Optional.of(activeNode));
        when(mockNodeRegistryService.getNodeType("test_stone")).thenReturn(Optional.of(testNodeType));

        resourceAdminCommand.onCommand(mockPlayer, new String[]{"info"}); // Changed to onCommand
        verify(mockPlayer).sendMessage(contains("Is Depleted: &ftrue"));
        verify(mockPlayer).sendMessage(contains("Respawn In: &f")); // Check if it attempts to show respawn time
    }


    // --- Tab Completion Tests ---
    @Test
    void tabComplete_firstArg_suggestsSubCommands() {
        List<String> completions = resourceAdminCommand.onTabComplete(mockSender, new String[]{""}); // Changed to onTabComplete
        assertTrue(completions.containsAll(List.of("place", "remove", "info")));

        completions = resourceAdminCommand.onTabComplete(mockSender, new String[]{"pl"}); // Changed to onTabComplete
        assertTrue(completions.contains("place"));
        assertEquals(1, completions.size());
    }

    @Test
    void tabComplete_placeSecondArg_suggestsNodeTypeIds() {
        List<String> completions = resourceAdminCommand.onTabComplete(mockSender, new String[]{"place", ""}); // Changed to onTabComplete
        assertTrue(completions.contains("test_stone"));

        completions = resourceAdminCommand.onTabComplete(mockSender, new String[]{"place", "te"}); // Changed to onTabComplete
        assertTrue(completions.contains("test_stone"));
    }

    @Test
    void tabComplete_placeThirdArg_suggestsWorlds() {
        mockBukkitStatic(); // For Bukkit.getWorlds()
        when(mockServer.getWorlds()).thenReturn(List.of(mockWorld)); // Mock Bukkit.getWorlds()

        List<String> completions = resourceAdminCommand.onTabComplete(mockSender, new String[]{"place", "test_stone", ""}); // Changed to onTabComplete
        assertTrue(completions.contains("test_world"));

        completions = resourceAdminCommand.onTabComplete(mockSender, new String[]{"place", "test_stone", "te"}); // Changed to onTabComplete
        assertTrue(completions.contains("test_world"));
    }

    @Test
    void tabComplete_otherSubCommands_returnsEmpty() {
        List<String> completions = resourceAdminCommand.onTabComplete(mockSender, new String[]{"remove", ""}); // Changed to onTabComplete
        assertTrue(completions.isEmpty());

        completions = resourceAdminCommand.onTabComplete(mockSender, new String[]{"info", ""}); // Changed to onTabComplete
        assertTrue(completions.isEmpty());
    }

}
