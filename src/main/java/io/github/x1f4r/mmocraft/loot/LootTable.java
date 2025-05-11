package io.github.x1f4r.mmocraft.loot;

import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Objects;

/**
 * Represents a collection of possible loot entries, identified by an ID.
 * Defines how many items might drop from this table. This is an immutable data carrier.
 */
public record LootTable(
        @NotNull String lootTableId,
        @NotNull List<LootEntry> entries,  // All possible items that *could* be chosen in a roll
        int minGuaranteedRolls,             // Minimum number of successful "rolls" from entries (each roll picks one weighted item among those passing dropChance)
        int maxGuaranteedRolls,             // Maximum number of successful "rolls"
        int minBonusRolls,                  // Minimum number of additional bonus rolls
        int maxBonusRolls,                  // Maximum number of additional bonus rolls
        double chanceForAnyBonusRolls       // Chance (0.0 to 1.0) that the bonus roll segment is even attempted
        // Future idea: List<LootPool> where each pool has its own roll count and entries, allowing for more complex structures.
) {
    /**
     * Canonical constructor with validation.
     */
    public LootTable {
        Objects.requireNonNull(lootTableId, "LootTable ID cannot be null.");
        entries = (entries != null) ? List.copyOf(entries) : List.of(); // Ensure unmodifiable and not null

        if (minGuaranteedRolls < 0) minGuaranteedRolls = 0;
        if (maxGuaranteedRolls < minGuaranteedRolls) maxGuaranteedRolls = minGuaranteedRolls;
        if (minBonusRolls < 0) minBonusRolls = 0;
        if (maxBonusRolls < minBonusRolls) maxBonusRolls = minBonusRolls;
        if (chanceForAnyBonusRolls < 0.0) chanceForAnyBonusRolls = 0.0;
        if (chanceForAnyBonusRolls > 1.0) chanceForAnyBonusRolls = 1.0;
    }
}