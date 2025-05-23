package io.github.x1f4r.mmocraft.items;

/**
 * Defines the type of requirement for an ingredient in a custom recipe.
 */
public enum RequirementType {
    /** A specific org.bukkit.Material */
    MATERIAL,
    /**
     * A Bukkit Material Tag (e.g., "LOGS", "PLANKS" - case-insensitive)
     * or a custom defined tag (e.g., "#MMO_ORES" - prefix indicates custom).
     */
    TAG,
    /** Another CustomItem defined in ItemService (matched by its unique ID). */
    ITEM
}