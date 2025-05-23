package io.github.x1f4r.mmocraft.loot;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;

/**
 * Represents a single entry in a LootTable, defining an item that can drop,
 * its quantity, chance, and optional conditions. This is an immutable data carrier.
 */
public record LootEntry(
        @NotNull String itemId,      // ItemService ID (e.g., "common_sword") or "VANILLA:MATERIAL_NAME"
        int minAmount,
        int maxAmount,
        double dropChance,           // 0.0 to 1.0 (e.g., 0.75 for 75% chance)
        int weight,                  // For weighted random choice if multiple entries pass their dropChance within a single roll
        @Nullable String requiredPermission, // Optional: permission the killer needs to get this specific drop
        @Nullable String lootConditionGroupId // Optional: ID of a group of conditions to check (for future expansion)
) {
    /**
     * Canonical constructor with validation for amounts and chances.
     */
    public LootEntry {
        Objects.requireNonNull(itemId, "LootEntry itemId cannot be null.");
        if (minAmount < 1) {
            // Allow 0 minAmount if maxAmount is also 0, for "placeholder" entries or entries that only trigger effects.
            // For item drops, minAmount typically should be >= 1.
            if (maxAmount > 0) minAmount = 1;
            else minAmount = 0; // if maxAmount is also 0
        }
        if (maxAmount < minAmount) maxAmount = minAmount;
        if (dropChance < 0.0) dropChance = 0.0;
        if (dropChance > 1.0) dropChance = 1.0;
        if (weight < 0) weight = 1; // Default weight 1 if not specified or invalid
        // requiredPermission and lootConditionGroupId can be null
    }
}