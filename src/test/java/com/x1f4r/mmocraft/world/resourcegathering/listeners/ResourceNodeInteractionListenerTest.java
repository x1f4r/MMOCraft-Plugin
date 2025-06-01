package com.x1f4r.mmocraft.world.resourcegathering.listeners;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.item.service.CustomItemRegistry;
import com.x1f4r.mmocraft.loot.model.LootTable;
import com.x1f4r.mmocraft.loot.service.LootService;
import com.x1f4r.mmocraft.playerdata.PlayerDataService;
import com.x1f4r.mmocraft.util.LoggingUtil;
import com.x1f4r.mmocraft.util.StringUtil;
import com.x1f4r.mmocraft.world.resourcegathering.model.ActiveResourceNode;
import com.x1f4r.mmocraft.world.resourcegathering.model.ResourceNodeType;
import com.x1f4r.mmocraft.world.resourcegathering.service.ActiveNodeManager;
import com.x1f4r.mmocraft.world.resourcegathering.service.ResourceNodeRegistryService;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;


class ResourceNodeInteractionListenerTest {

    @Mock private MMOCraftPlugin mockPlugin;
    @Mock private ActiveNodeManager mockActiveNodeManager;
    @Mock private ResourceNodeRegistryService mockNodeRegistryService;
    @Mock private LootService mockLootService;
    @Mock private CustomItemRegistry mockCustomItemRegistry; // Unused for now
    @Mock private PlayerDataService mockPlayerDataService;   // Unused for now
    @Mock private LoggingUtil mockLogger;

    @Mock private Player mockPlayer;
    @Mock private PlayerInventory mockPlayerInventory;
    @Mock private Block mockBlock;
    @Mock private World mockWorld;
    @Mock private BlockBreakEvent mockEvent;

    private ResourceNodeInteractionListener listener;
    private Location testLocation;
    private ResourceNodeType testNodeType;
    private ActiveResourceNode testActiveNode;
    private LootTable testLootTable;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testLocation = new Location(mockWorld, 10, 60, 20);

        when(mockEvent.getPlayer()).thenReturn(mockPlayer);
        when(mockEvent.getBlock()).thenReturn(mockBlock);
        when(mockBlock.getLocation()).thenReturn(testLocation);
        when(mockPlayer.getInventory()).thenReturn(mockPlayerInventory);
        when(mockPlayer.getLocation()).thenReturn(testLocation); // For dropping items
        when(mockPlayer.getWorld()).thenReturn(mockWorld);


        testNodeType = new ResourceNodeType(
                "test_stone", Material.STONE, 5.0,
                Set.of(Material.IRON_PICKAXE), "stone_loot_table", 60
        );
        testActiveNode = new ActiveResourceNode(testLocation, "test_stone");
        testLootTable = new LootTable("stone_loot_table", List.of(
            // Using Material names as placeholder item IDs for simplicity, actual LootTableEntry might differ
            new com.x1f4r.mmocraft.loot.model.LootTableEntry("COBBLESTONE", 1.0, 1, 1)
        ));
        // Mock the generateLoot to return a concrete ItemStack for testing drops
        ItemStack cobbleDrop = new ItemStack(Material.COBBLESTONE, 1);
        when(testLootTable.generateLoot()).thenReturn(List.of(cobbleDrop));


        listener = new ResourceNodeInteractionListener(mockPlugin, mockActiveNodeManager, mockNodeRegistryService,
                mockLootService, mockCustomItemRegistry, mockPlayerDataService, mockLogger);
    }

    @Test
    void onBlockBreak_notCustomNode_shouldDoNothing() {
        when(mockActiveNodeManager.getActiveNode(testLocation)).thenReturn(Optional.empty());
        listener.onBlockBreak(mockEvent);

        verify(mockEvent, never()).setCancelled(true);
        verify(mockEvent, never()).setDropItems(false);
        verify(mockLootService, never()).getLootTableById(anyString());
    }

    @Test
    void onBlockBreak_nodeDepleted_shouldSendMessageAndCancel() {
        testActiveNode.setDepleted(true);
        when(mockActiveNodeManager.getActiveNode(testLocation)).thenReturn(Optional.of(testActiveNode));
        listener.onBlockBreak(mockEvent);

        verify(mockEvent).setCancelled(true);
        verify(mockEvent).setDropItems(false);
        verify(mockPlayer).sendMessage(StringUtil.colorize("&cThis resource node is currently depleted."));
        verify(mockActiveNodeManager, never()).depleteNode(any()); // Should not try to deplete again
    }

    @Test
    void onBlockBreak_nodeTypeNotFound_shouldSendMessageAndCancel() {
        when(mockActiveNodeManager.getActiveNode(testLocation)).thenReturn(Optional.of(testActiveNode));
        when(mockNodeRegistryService.getNodeType("test_stone")).thenReturn(Optional.empty());
        listener.onBlockBreak(mockEvent);

        verify(mockEvent).setCancelled(true);
        verify(mockEvent).setDropItems(false);
        verify(mockPlayer).sendMessage(StringUtil.colorize("&cError: Resource node type unknown. Please report to an admin."));
        verify(mockLogger).severe(contains("ActiveResourceNode at"));
    }

    @Test
    void onBlockBreak_toolRequired_incorrectTool_shouldSendMessageAndCancel() {
        when(mockActiveNodeManager.getActiveNode(testLocation)).thenReturn(Optional.of(testActiveNode));
        when(mockNodeRegistryService.getNodeType("test_stone")).thenReturn(Optional.of(testNodeType)); // Requires IRON_PICKAXE
        when(mockPlayerInventory.getItemInMainHand()).thenReturn(new ItemStack(Material.WOODEN_PICKAXE));

        listener.onBlockBreak(mockEvent);

        verify(mockEvent).setCancelled(true);
        verify(mockEvent).setDropItems(false);
        verify(mockPlayer).sendMessage(StringUtil.colorize(contains("&cYou do not have the required tool")));
        verify(mockActiveNodeManager, never()).depleteNode(any());
    }

    @Test
    void onBlockBreak_toolRequired_noToolInHand_shouldSendMessageAndCancel() {
        when(mockActiveNodeManager.getActiveNode(testLocation)).thenReturn(Optional.of(testActiveNode));
        when(mockNodeRegistryService.getNodeType("test_stone")).thenReturn(Optional.of(testNodeType)); // Requires IRON_PICKAXE
        when(mockPlayerInventory.getItemInMainHand()).thenReturn(null); // No tool

        listener.onBlockBreak(mockEvent);
        verify(mockPlayer).sendMessage(StringUtil.colorize(contains("&cYou do not have the required tool")));
    }


    @Test
    void onBlockBreak_toolNotRequired_anyToolOrHand_shouldProceed() {
        ResourceNodeType handBreakableType = new ResourceNodeType("hand_break", Material.SAND, 1.0, Collections.emptySet(), "sand_loot", 10);
        ActiveResourceNode handBreakableNode = new ActiveResourceNode(testLocation, "hand_break");
        LootTable sandLootTable = new LootTable("sand_loot", List.of(new com.x1f4r.mmocraft.loot.model.LootTableEntry("SAND", 1.0, 1, 1)));
        when(sandLootTable.generateLoot()).thenReturn(List.of(new ItemStack(Material.SAND)));


        when(mockActiveNodeManager.getActiveNode(testLocation)).thenReturn(Optional.of(handBreakableNode));
        when(mockNodeRegistryService.getNodeType("hand_break")).thenReturn(Optional.of(handBreakableType));
        when(mockLootService.getLootTableById("sand_loot")).thenReturn(Optional.of(sandLootTable));
        when(mockPlayerInventory.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR)); // Bare hand

        listener.onBlockBreak(mockEvent);

        verify(mockEvent).setCancelled(true);
        verify(mockEvent).setDropItems(false);
        verify(mockActiveNodeManager).depleteNode(handBreakableNode);
        verify(mockWorld).dropItemNaturally(eq(testLocation), any(ItemStack.class)); // SAND
        verify(mockPlayer).sendMessage(StringUtil.colorize(contains("&aYou successfully gathered resources")));
    }

    @Test
    void onBlockBreak_correctTool_lootTableFound_shouldDropLootAndDeplete() {
        when(mockActiveNodeManager.getActiveNode(testLocation)).thenReturn(Optional.of(testActiveNode));
        when(mockNodeRegistryService.getNodeType("test_stone")).thenReturn(Optional.of(testNodeType));
        when(mockPlayerInventory.getItemInMainHand()).thenReturn(new ItemStack(Material.IRON_PICKAXE));
        when(mockLootService.getLootTableById("stone_loot_table")).thenReturn(Optional.of(testLootTable));

        listener.onBlockBreak(mockEvent);

        verify(mockEvent).setCancelled(true);
        verify(mockEvent).setDropItems(false);
        verify(mockActiveNodeManager).depleteNode(testActiveNode);
        verify(mockWorld).dropItemNaturally(eq(testLocation), any(ItemStack.class)); // COBBLESTONE
        verify(mockPlayer).sendMessage(StringUtil.colorize(contains("&aYou successfully gathered resources")));
        verify(mockLogger).debug(contains("Player TestPlayer gathered from node test_stone"));
    }

    @Test
    void onBlockBreak_lootTableNotFound_shouldSendMessageAndDeplete() {
        when(mockActiveNodeManager.getActiveNode(testLocation)).thenReturn(Optional.of(testActiveNode));
        when(mockNodeRegistryService.getNodeType("test_stone")).thenReturn(Optional.of(testNodeType));
        when(mockPlayerInventory.getItemInMainHand()).thenReturn(new ItemStack(Material.IRON_PICKAXE));
        when(mockLootService.getLootTableById("stone_loot_table")).thenReturn(Optional.empty()); // Loot table missing

        listener.onBlockBreak(mockEvent);

        verify(mockEvent).setCancelled(true);
        verify(mockEvent).setDropItems(false);
        verify(mockActiveNodeManager).depleteNode(testActiveNode); // Still depletes
        verify(mockWorld, never()).dropItemNaturally(any(), any()); // No loot
        verify(mockPlayer).sendMessage(StringUtil.colorize("&cNo loot configured for this node. Please report to an admin."));
        verify(mockLogger).warning(contains("LootTableId 'stone_loot_table' not found"));
    }
}
