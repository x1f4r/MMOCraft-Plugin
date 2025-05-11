package io.github.x1f4r.mmocraft.entities;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable; // If using annotations

/**
 * Represents the collection of custom and core combat statistics for a non-player entity.
 * This object is immutable once created and serves as a data carrier for an entity's stats.
 */
public record EntityStats(
        // Core Combat Stats influenced by MMOCraft
        double maxHealth,        // Directly sets Bukkit's GENERIC_MAX_HEALTH base value
        int defense,             // Custom MMOCraft defense, used in CombatService
        int strength,            // Directly sets Bukkit's GENERIC_ATTACK_DAMAGE base value
        int critChance,          // Percentage (0-100), used in CombatService
        int critDamage,          // Bonus percentage (e.g., 50 for +50% damage on crit), used in CombatService

        // Utility/Movement Stats
        int speedPercent,        // Percentage modifier for Bukkit's GENERIC_MOVEMENT_SPEED base value (e.g., 20 for +20%)

        // Resource Stats (if applicable for mobs with abilities)
        int maxMana              // Custom MMOCraft max mana
        // Future Considerations: elemental resistances, specific ability power modifiers, etc.
) {
    /**
     * Canonical constructor for EntityStats.
     * Basic validation is performed.
     */
    public EntityStats {
        if (maxHealth < 1.0) maxHealth = 1.0; // Mobs must have at least 1 max health
        if (defense < 0) defense = 0;
        // strength can be 0 or negative if desired (e.g., for a non-attacking mob or healing touch)
        if (critChance < 0) critChance = 0;
        if (critChance > 1000) critChance = 1000; // Allow > 100 for guaranteed multiple crit "tiers" if ever needed, but usually 0-100
        if (critDamage < 0) critDamage = 0; // Bonus damage should be non-negative
        // speedPercent can be negative
        if (maxMana < 0) maxMana = 0;
    }

    /**
     * Creates a default EntityStats instance, potentially deriving some values
     * from a base LivingEntity's default Bukkit attributes.
     *
     * @param baseEntity The entity to optionally derive vanilla default stats from.
     *                   Can be null if hardcoded defaults are preferred.
     * @return A new EntityStats instance with default values.
     */
    public static EntityStats createDefault(@Nullable LivingEntity baseEntity) {
        double defaultBukkitHealth = 20.0;
        double defaultBukkitAttack = 0.0; // Most mobs have specific default attack
        double defaultBukkitSpeed = 0.2;  // Approximate generic default, varies by mob

        if (baseEntity != null) {
            if (baseEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                defaultBukkitHealth = baseEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getDefaultValue();
            }
            if (baseEntity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
                defaultBukkitAttack = baseEntity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getDefaultValue();
            }
            if (baseEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                defaultBukkitSpeed = baseEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getDefaultValue();
            }
        }

        // For EntityStats, 'strength' becomes the base attack, 'speedPercent' modifies base speed.
        return new EntityStats(
                defaultBukkitHealth, // maxHealth
                0,                   // defense
                (int) Math.round(defaultBukkitAttack), // strength (as int)
                0,                   // critChance
                0,                   // critDamage
                0,                   // speedPercent (0% means vanilla base speed derived from defaultBukkitSpeed)
                0                    // maxMana
        );
    }

    /**
     * Creates an EntityStats instance with all values effectively zeroed out,
     * but with a minimal maxHealth to prevent instant death issues.
     * Useful for entities that should not have any custom stats (e.g., ArmorStands, Players via this system).
     * @return A new EntityStats instance with minimal/zeroed values.
     */
    public static EntityStats createZeroed() {
        return new EntityStats(
                1.0, // Min 1 health to avoid issues with Bukkit's damage/death system
                0, 0, 0, 0, 0, 0
        );
    }
}