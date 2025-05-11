package io.github.x1f4r.mmocraft.items;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents an immutable template for a custom item, parsed from configuration (e.g., items.yml).
 * This is NOT the ItemStack itself, but the definition used by ItemService to create ItemStacks.
 */
public record CustomItem(
        @NotNull String id,             // Unique identifier (e.g., "aspect_of_the_end")
        @NotNull Material material,
        @NotNull Component displayName, // Adventure Component for the item's name
        @NotNull List<Component> lore,  // Adventure Components for the item's lore lines
        boolean unbreakable,
        @NotNull Map<Enchantment, Integer> enchantments, // Enchantment -> Level
        @NotNull Set<ItemFlag> itemFlags,
        @NotNull List<AttributeModifierData> vanillaAttributeModifiers, // For GENERIC_ATTACK_DAMAGE etc.

        // Core MMOCraft Stats (directly mapped from items.yml -> custom_stats)
        // These values are added to the player's calculated stats.
        int mmoStrength,
        int mmoDefense,
        int mmoCritChance,
        int mmoCritDamage,
        int mmoMaxHealthBonus,
        int mmoMaxManaBonus,
        int mmoSpeedBonusPercent, // Renamed for clarity from mmoSpeedBonus
        int mmoMiningSpeedBonus,
        int mmoForagingSpeedBonus,
        int mmoFishingSpeedBonus,
        int mmoShootingSpeedBonus,

        // Ability Linkage (for Part 5)
        @Nullable String linkedAbilityId,      // ID of the ItemAbility this item grants
        @Nullable Integer overrideManaCost,    // Optional: overrides ability's default mana cost
        @Nullable Integer overrideCooldownTicks, // Optional: overrides ability's default cooldown

        // Generic NBT data for ability-specific parameters or other custom NBT tags.
        // Key is the simple string key from items.yml (e.g., "TELEPORT_RANGE").
        // Value can be Integer, Double, String, Boolean (parsing in ItemService handles types).
        @NotNull Map<String, Object> genericCustomNbt
) {
    /**
     * Canonical constructor.
     * Ensures collections are not null, providing empty ones if input is null.
     */
    public CustomItem {
        Objects.requireNonNull(id, "CustomItem ID cannot be null.");
        Objects.requireNonNull(material, "CustomItem Material cannot be null.");
        Objects.requireNonNull(displayName, "CustomItem DisplayName cannot be null.");
        // Ensure collections are not null, even if empty
        lore = (lore != null) ? List.copyOf(lore) : List.of();
        enchantments = (enchantments != null) ? Map.copyOf(enchantments) : Map.of();
        itemFlags = (itemFlags != null) ? Set.copyOf(itemFlags) : Set.of();
        vanillaAttributeModifiers = (vanillaAttributeModifiers != null) ? List.copyOf(vanillaAttributeModifiers) : List.of();
        genericCustomNbt = (genericCustomNbt != null) ? Map.copyOf(genericCustomNbt) : Map.of();
    }

    /**
     * Helper method to safely get a typed value from the genericCustomNbt map.
     *
     * @param key          The key for the NBT value.
     * @param defaultValue The value to return if the key is not found or type mismatches.
     * @param type         The expected Class type of the value.
     * @param <T>          The generic type.
     * @return The NBT value cast to the expected type, or the defaultValue.
     */
    @SuppressWarnings("unchecked")
    public <T> T getGenericNbtValue(String key, T defaultValue, @NotNull Class<T> type) {
        Objects.requireNonNull(key, "NBT key cannot be null.");
        Objects.requireNonNull(type, "Expected NBT type class cannot be null.");

        Object value = genericCustomNbt.get(key);

        if (value == null) {
            return defaultValue;
        }

        if (type.isInstance(value)) {
            return (T) value;
        }

        // Attempt common type conversions (e.g., config might load numbers as Integer/Long/Double)
        if (value instanceof Number numValue) {
            if (type == Integer.class) {
                return (T) Integer.valueOf(numValue.intValue());
            } else if (type == Double.class) {
                return (T) Double.valueOf(numValue.doubleValue());
            } else if (type == Long.class) {
                return (T) Long.valueOf(numValue.longValue());
            } else if (type == Float.class) {
                return (T) Float.valueOf(numValue.floatValue());
            } else if (type == Byte.class) {
                return (T) Byte.valueOf(numValue.byteValue());
            } else if (type == Short.class) {
                return (T) Short.valueOf(numValue.shortValue());
            }
        }

        // If type is String, try to convert value.toString()
        if (type == String.class) {
            return (T) value.toString();
        }

        // If type is Boolean and value is String "true" or "false"
        if (type == Boolean.class && value instanceof String strValue) {
            if ("true".equalsIgnoreCase(strValue)) return (T) Boolean.TRUE;
            if ("false".equalsIgnoreCase(strValue)) return (T) Boolean.FALSE;
        }


        // If direct cast or common conversions fail, return default
        // Logging a warning here might be useful for debugging items.yml
        // MMOCraft.getPluginLogger().warning("NBT type mismatch for key '" + key + "' in item '" + id +
        //                                   "'. Expected " + type.getSimpleName() + ", got " + value.getClass().getSimpleName() +
        //                                   ". Returning default value.");
        return defaultValue;
    }

    // Getters for linkedAbilityId, overrideManaCost, overrideCooldownTicks are automatically generated by the record.
}