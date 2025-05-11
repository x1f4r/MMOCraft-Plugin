package io.github.x1f4r.mmocraft.items;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable; // If using Jetbrains annotations

import java.util.Objects;

/**
 * Represents the data parsed from configuration for a Bukkit AttributeModifier
 * to be applied to a custom item. This is an immutable data carrier.
 */
public record AttributeModifierData(
        @NotNull Attribute attribute,
        @NotNull String name, // Name for the modifier (e.g., "mmoc.item_id.attribute_name")
        double amount,
        @NotNull AttributeModifier.Operation operation,
        @Nullable EquipmentSlot slot // Can be null, meaning it applies when in main hand by default, or as defined by Attribute
) {
    /**
     * Canonical constructor provided by the record.
     * Additional validation can be done here if needed, or in the parsing logic.
     */
    public AttributeModifierData {
        Objects.requireNonNull(attribute, "Attribute cannot be null for AttributeModifierData");
        Objects.requireNonNull(name, "Modifier name cannot be null for AttributeModifierData");
        Objects.requireNonNull(operation, "Operation cannot be null for AttributeModifierData");
        // Slot can be null
    }
}