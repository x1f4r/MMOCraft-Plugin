package com.x1f4r.mmocraft.world.resourcegathering.service;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.item.service.CustomItemRegistry;
import com.x1f4r.mmocraft.loot.service.LootService;
import com.x1f4r.mmocraft.util.LoggingUtil;
import com.x1f4r.mmocraft.world.resourcegathering.model.ActiveResourceNode;
import com.x1f4r.mmocraft.world.resourcegathering.model.ResourceNodeType;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActiveNodeManagerTest {

    @Mock private MMOCraftPlugin mockPlugin;
    @Mock private LoggingUtil mockLogger;
    @Mock private ResourceNodeRegistryService mockRegistryService;
    @Mock private LootService mockLootService; // Not used directly by ActiveNodeManager but part of constructor
    @Mock private CustomItemRegistry mockCustomItemRegistry; // Same as above

    @Mock private Server mockServer;
    @Mock private BukkitScheduler mockScheduler;
    @Mock private World mockWorld;
    @Mock private Block mockBlock;
    @Mock private BukkitTask mockTask;

    @Captor private ArgumentCaptor<Runnable> runnableCaptor;

    private ActiveNodeManager activeNodeManager;
    private ResourceNodeType testNodeType;
    private Location testLocation;
    private Location blockSnappedLocation;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(mockPlugin.getServer()).thenReturn(mockServer);
        when(mockServer.getScheduler()).thenReturn(mockScheduler);
        when(mockPlugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("TestLogger")); // For Bukkit runTask

        testLocation = new Location(mockWorld, 10.5, 60.7, 20.2); // Non-snapped
        blockSnappedLocation = new Location(mockWorld, 10, 60, 20); // Snapped version

        when(mockWorld.getBlockAt(blockSnappedLocation)).thenReturn(mockBlock);
        when(mockWorld.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(mockBlock); // General case for block retrieval
        when(mockBlock.getLocation()).thenReturn(blockSnappedLocation); // Ensure block knows its snapped location


        testNodeType = new ResourceNodeType("test_stone", Material.STONE, 5.0, Set.of(), "stone_loot", 60);
        when(mockRegistryService.getNodeType("test_stone")).thenReturn(Optional.of(testNodeType));
        when(mockRegistryService.getNodeType("unknown_type")).thenReturn(Optional.empty());

        activeNodeManager = new ActiveNodeManager(mockPlugin, mockLogger, mockRegistryService, mockLootService, mockCustomItemRegistry);

        // Mock scheduler to run tasks immediately
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return mockTask;
        }).when(mockScheduler).runTask(eq(mockPlugin), runnableCaptor.capture());
         doAnswer(invocation -> { // also for the one with delay, for simplicity in test
            invocation.getArgument(1, Runnable.class).run();
            return mockTask;
        }).when(mockScheduler).runTaskLater(eq(mockPlugin), runnableCaptor.capture(), anyLong());


    }

    @Test
    void placeNewNode_validTypeAndLocation_shouldPlaceNodeAndSetBlock() {
        activeNodeManager.placeNewNode(testLocation, "test_stone");

        Optional<ActiveResourceNode> nodeOpt = activeNodeManager.getActiveNode(testLocation);
        assertTrue(nodeOpt.isPresent());
        assertEquals("test_stone", nodeOpt.get().getNodeTypeId());
        assertFalse(nodeOpt.get().isDepleted());
        assertEquals(blockSnappedLocation, nodeOpt.get().getLocation()); // Check if location was snapped

        verify(mockScheduler).runTask(eq(mockPlugin), runnableCaptor.capture());
        runnableCaptor.getValue().run(); // Manually run if not auto-run by mock setup, ensures block is set
        verify(mockBlock).setType(Material.STONE);
        verify(mockLogger).info(contains("Placed new resource node 'test_stone'"));
    }

    @Test
    void placeNewNode_unknownType_shouldLogWarningAndNotPlace() {
        activeNodeManager.placeNewNode(testLocation, "unknown_type");
        assertTrue(activeNodeManager.getActiveNode(testLocation).isEmpty());
        verify(mockLogger).warning("Attempted to place new node with unknown typeId: unknown_type");
        verify(mockBlock, never()).setType(any());
    }

    @Test
    void placeNewNode_alreadyExists_shouldLogWarningAndNotPlace() {
        activeNodeManager.placeNewNode(testLocation, "test_stone"); // First placement
        reset(mockLogger, mockBlock); // Reset mocks for the second call check

        activeNodeManager.placeNewNode(testLocation, "test_stone"); // Attempt second placement

        verify(mockLogger).warning(contains("Node already exists at"));
        verify(mockBlock, never()).setType(any()); // Should not try to set block again
        assertEquals(1, activeNodeManager.getAllActiveNodesView().size()); // Should only be one node
    }

    @Test
    void placeNewNode_nullLocation_shouldLogWarning() {
        activeNodeManager.placeNewNode(null, "test_stone");
        verify(mockLogger).warning("Attempted to place new node at null location or world.");
        assertTrue(activeNodeManager.getAllActiveNodesView().isEmpty());
    }


    @Test
    void getActiveNode_existingNode_shouldReturnNode() {
        activeNodeManager.placeNewNode(testLocation, "test_stone");
        assertTrue(activeNodeManager.getActiveNode(testLocation).isPresent());
        // Check with snapped location too
        assertTrue(activeNodeManager.getActiveNode(blockSnappedLocation).isPresent());
    }

    @Test
    void getActiveNode_nonExistingNode_shouldReturnEmpty() {
        assertFalse(activeNodeManager.getActiveNode(testLocation).isPresent());
    }

    @Test
    void getActiveNode_nullLocation_shouldReturnEmpty() {
        assertFalse(activeNodeManager.getActiveNode(null).isPresent());
    }


    @Test
    void depleteNode_validNode_shouldSetDepletedAndChangeBlock() {
        activeNodeManager.placeNewNode(testLocation, "test_stone");
        ActiveResourceNode node = activeNodeManager.getActiveNode(testLocation).get();

        activeNodeManager.depleteNode(node);

        assertTrue(node.isDepleted());
        assertTrue(node.getRespawnAtMillis() > System.currentTimeMillis());
        verify(mockBlock).setType(Material.BEDROCK); // Default depletedMaterial
        verify(mockLogger).debug(contains("Depleted node 'test_stone'"));
    }

    @Test
    void depleteNode_alreadyDepleted_shouldDoNothing() {
        activeNodeManager.placeNewNode(testLocation, "test_stone");
        ActiveResourceNode node = activeNodeManager.getActiveNode(testLocation).get();
        node.setDepleted(true); // Manually deplete
        reset(mockBlock, mockLogger); // Reset mocks

        activeNodeManager.depleteNode(node);
        // No further changes should happen
        verify(mockBlock, never()).setType(any());
        verify(mockLogger, never()).debug(contains("Depleted node"));
    }

    @Test
    void depleteNode_unknownNodeTypeInNode_shouldLogError() {
        activeNodeManager.placeNewNode(testLocation, "test_stone");
        ActiveResourceNode node = activeNodeManager.getActiveNode(testLocation).get();
        // Simulate node type being removed from registry AFTER node was placed
        when(mockRegistryService.getNodeType("test_stone")).thenReturn(Optional.empty());

        activeNodeManager.depleteNode(node);
        verify(mockLogger).severe(contains("Cannot deplete node: Unknown NodeType ID 'test_stone'"));
        assertFalse(node.isDepleted()); // State should not change if type is unknown
    }


    @Test
    void respawnNode_depletedNode_shouldResetStateAndChangeBlock() {
        activeNodeManager.placeNewNode(testLocation, "test_stone");
        ActiveResourceNode node = activeNodeManager.getActiveNode(testLocation).get();
        activeNodeManager.depleteNode(node); // Deplete it first
        reset(mockBlock, mockLogger); // Reset mocks after deplete changes them

        activeNodeManager.respawnNode(node);

        assertFalse(node.isDepleted());
        assertEquals(0, node.getRespawnAtMillis());
        verify(mockBlock).setType(Material.STONE); // testNodeType.getDisplayMaterial()
        verify(mockLogger).debug(contains("Respawned node 'test_stone'"));
    }

    @Test
    void respawnNode_notDepleted_shouldDoNothing() {
        activeNodeManager.placeNewNode(testLocation, "test_stone");
        ActiveResourceNode node = activeNodeManager.getActiveNode(testLocation).get();
        // Node is not depleted
        reset(mockBlock, mockLogger);

        activeNodeManager.respawnNode(node);
        verify(mockBlock, never()).setType(any());
        verify(mockLogger, never()).debug(contains("Respawned node"));
    }


    @Test
    void tickNodes_shouldRespawnReadyNodes() {
        activeNodeManager.placeNewNode(testLocation, "test_stone");
        ActiveResourceNode node = activeNodeManager.getActiveNode(testLocation).get();

        // Manually set it as depleted and ready to respawn
        node.setDepleted(true);
        node.setRespawnAtMillis(System.currentTimeMillis() - 1000); // Set respawn time in the past

        activeNodeManager.tickNodes();

        assertFalse(node.isDepleted());
        verify(mockBlock).setType(Material.STONE);
        verify(mockLogger).debug(contains("Respawned node 'test_stone'"));
    }

    @Test
    void tickNodes_nodeNotReady_shouldNotRespawn() {
        activeNodeManager.placeNewNode(testLocation, "test_stone");
        ActiveResourceNode node = activeNodeManager.getActiveNode(testLocation).get();

        node.setDepleted(true);
        node.setRespawnAtMillis(System.currentTimeMillis() + 100000); // Far in future

        activeNodeManager.tickNodes();

        assertTrue(node.isDepleted());
        verify(mockBlock, never()).setType(Material.STONE); // Should not have changed back from BEDROCK (depletedMaterial)
    }

    @Test
    void removeNode_existingNode_shouldRemoveAndSetToAir() {
        activeNodeManager.placeNewNode(testLocation, "test_stone");
        assertTrue(activeNodeManager.getActiveNode(testLocation).isPresent());

        boolean removed = activeNodeManager.removeNode(testLocation);
        assertTrue(removed);
        assertFalse(activeNodeManager.getActiveNode(testLocation).isPresent());
        verify(mockBlock).setType(Material.AIR);
        verify(mockLogger).info(contains("Removed resource node 'test_stone'"));
    }

    @Test
    void removeNode_nonExistingNode_shouldReturnFalse() {
        boolean removed = activeNodeManager.removeNode(testLocation);
        assertFalse(removed);
        verify(mockBlock, never()).setType(Material.AIR);
    }

    @Test
    void shutdown_shouldLogMessage() {
        activeNodeManager.shutdown();
        verify(mockLogger).info("ActiveNodeManager shutting down. Persisting node states is not yet implemented.");
    }
}
