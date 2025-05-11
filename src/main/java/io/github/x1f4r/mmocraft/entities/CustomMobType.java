package io.github.x1f4r.mmocraft.entities;

import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Defines an immutable template for a custom mob type, typically parsed from configuration.
 */
public record CustomMobType(
        @NotNull String typeId,
        @NotNull EntityType baseEntityType,
        @NotNull String displayName, // Raw string with color codes for Adventure Component parsing
        @NotNull EntityStats stats,
        @NotNull List<String> aiBehaviorIds,    // IDs for custom AI behaviors or Purpur Goals
        @Nullable String customLootTableId,   // ID linking to a loot table in MobDropService
        @NotNull Map<String, String> equipment, // Bukkit EquipmentSlot name -> ItemService ID or "VANILLA:MATERIAL_NAME"

        // Spawning Rules
        boolean replaceVanillaSpawns,
        double replaceChance, // 0.0 to 1.0
        int spawnWeight,
        @NotNull Set<String> spawnBiomes, // Uppercase Bukkit Biome enum names or NamespacedKeys
        @Nullable Integer minSpawnLightLevel,
        @Nullable Integer maxSpawnLightLevel,
        @Nullable Integer minSpawnY,
        @Nullable Integer maxSpawnY,
        @Nullable String requiredWorld, // Name of the world

        @NotNull Map<String, Object> genericNbtData // For additional raw NBT (e.g., {IsBaby: true, Silent: true})
) {
    /**
     * Canonical constructor with validation and default empty collections.
     */
    public CustomMobType {
        Objects.requireNonNull(typeId, "CustomMobType typeId cannot be null.");
        Objects.requireNonNull(baseEntityType, "CustomMobType baseEntityType cannot be null.");
        Objects.requireNonNull(displayName, "CustomMobType displayName cannot be null.");
        Objects.requireNonNull(stats, "CustomMobType stats cannot be null.");
        // Ensure collections are unmodifiable and not null
        aiBehaviorIds = (aiBehaviorIds != null) ? List.copyOf(aiBehaviorIds) : List.of();
        equipment = (equipment != null) ? Map.copyOf(equipment) : Map.of();
        spawnBiomes = (spawnBiomes != null) ? Set.copyOf(spawnBiomes) : Set.of();
        genericNbtData = (genericNbtData != null) ? Map.copyOf(genericNbtData) : Map.of();

        if (replaceChance < 0.0 || replaceChance > 1.0) {
            throw new IllegalArgumentException("Replace chance must be between 0.0 and 1.0 for " + typeId);
        }
        if (spawnWeight < 0) {
            throw new IllegalArgumentException("Spawn weight must be non-negative for " + typeId);
        }
        // customLootTableId, min/max light/Y, requiredWorld can be null
    }
}