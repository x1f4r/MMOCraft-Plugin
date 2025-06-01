package com.x1f4r.mmocraft.world.resourcegathering.model;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Collections; // Added
import java.util.Set;

class ResourceNodeTypeTest {

    @Test
    void constructor_validArgs_shouldCreateInstance() {
        ResourceNodeType nodeType = new ResourceNodeType(
                "test_node", Material.STONE, 5.0,
                Set.of(Material.WOODEN_PICKAXE), "test_loot", 60, "Test Node"
        );
        assertEquals("test_node", nodeType.getTypeId());
        assertEquals(Material.STONE, nodeType.getDisplayMaterial());
        assertEquals(5.0, nodeType.getBreakTimeSeconds());
        assertTrue(nodeType.getRequiredToolTypes().contains(Material.WOODEN_PICKAXE));
        assertEquals("test_loot", nodeType.getLootTableId());
        assertEquals(60, nodeType.getRespawnTimeSeconds());
        assertEquals("Test Node", nodeType.getCustomName());
        assertEquals("Test Node", nodeType.getEffectiveName());
    }

    @Test
    void constructor_nullCustomName_shouldUseTypeIdAsEffectiveName() {
        ResourceNodeType nodeType = new ResourceNodeType(
                "id_only_node", Material.COAL_ORE, 3.0,
                Collections.emptySet(), "coal_loot", 30
        );
        assertNull(nodeType.getCustomName());
        assertEquals("id_only_node", nodeType.getEffectiveName());
    }

    @Test
    void constructor_emptyToolSet_shouldBeAllowed() {
        ResourceNodeType nodeType = new ResourceNodeType(
                "hand_breakable", Material.SAND, 1.0,
                Set.of(), "sand_loot", 10
        );
        assertTrue(nodeType.getRequiredToolTypes().isEmpty());
    }

    @Test
    void constructor_nullToolSet_shouldResultInEmptySet() {
        ResourceNodeType nodeType = new ResourceNodeType(
                "null_tools_node", Material.DIRT, 1.0,
                null, "dirt_loot", 10
        );
        assertNotNull(nodeType.getRequiredToolTypes());
        assertTrue(nodeType.getRequiredToolTypes().isEmpty());
    }


    @Test
    void constructor_invalidArgs_shouldThrowException() {
        // Null typeId
        assertThrows(NullPointerException.class, () -> new ResourceNodeType(
                null, Material.STONE, 5.0, Set.of(Material.WOODEN_PICKAXE), "test_loot", 60, "Test Node"
        ));
        // Empty typeId
        assertThrows(IllegalArgumentException.class, () -> new ResourceNodeType(
                "", Material.STONE, 5.0, Set.of(Material.WOODEN_PICKAXE), "test_loot", 60, "Test Node"
        ));
        // Null displayMaterial
        assertThrows(NullPointerException.class, () -> new ResourceNodeType(
                "test_node", null, 5.0, Set.of(Material.WOODEN_PICKAXE), "test_loot", 60, "Test Node"
        ));
        // Zero breakTimeSeconds
        assertThrows(IllegalArgumentException.class, () -> new ResourceNodeType(
                "test_node", Material.STONE, 0.0, Set.of(Material.WOODEN_PICKAXE), "test_loot", 60, "Test Node"
        ));
        // Negative breakTimeSeconds
        assertThrows(IllegalArgumentException.class, () -> new ResourceNodeType(
                "test_node", Material.STONE, -1.0, Set.of(Material.WOODEN_PICKAXE), "test_loot", 60, "Test Node"
        ));
        // Null lootTableId
        assertThrows(NullPointerException.class, () -> new ResourceNodeType(
                "test_node", Material.STONE, 5.0, Set.of(Material.WOODEN_PICKAXE), null, 60, "Test Node"
        ));
        // Zero respawnTimeSeconds
        assertThrows(IllegalArgumentException.class, () -> new ResourceNodeType(
                "test_node", Material.STONE, 5.0, Set.of(Material.WOODEN_PICKAXE), "test_loot", 0, "Test Node"
        ));
        // Negative respawnTimeSeconds
        assertThrows(IllegalArgumentException.class, () -> new ResourceNodeType(
                "test_node", Material.STONE, 5.0, Set.of(Material.WOODEN_PICKAXE), "test_loot", -1, "Test Node"
        ));
    }

    @Test
    void equalsAndHashCode_shouldBeBasedOnTypeId() {
        ResourceNodeType nodeType1a = new ResourceNodeType("node1", Material.STONE, 5.0, Set.of(), "loot1", 60);
        ResourceNodeType nodeType1b = new ResourceNodeType("node1", Material.COAL_ORE, 10.0, Set.of(Material.WOODEN_PICKAXE), "loot1b", 120, "Custom Name");
        ResourceNodeType nodeType2 = new ResourceNodeType("node2", Material.STONE, 5.0, Set.of(), "loot1", 60);

        assertEquals(nodeType1a, nodeType1b, "Instances with same typeId should be equal.");
        assertNotEquals(nodeType1a, nodeType2, "Instances with different typeId should not be equal.");
        assertEquals(nodeType1a.hashCode(), nodeType1b.hashCode(), "Hashcodes for instances with same typeId should be equal.");
        assertNotEquals(nodeType1a.hashCode(), nodeType2.hashCode(), "Hashcodes for instances with different typeId should ideally not be equal.");
    }
}
