package io.github.x1f4r.mmocraft.items;

import io.github.x1f4r.mmocraft.services.ItemService;
import io.github.x1f4r.mmocraft.services.NBTService; // For ITEM_ID_KEY
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a required ingredient for a custom recipe, including its type, value, and amount.
 * This is an immutable data carrier.
 */
public record RequiredIngredient(
        @NotNull RequirementType type,
        @NotNull String value, // Material name (e.g., "IRON_INGOT"), Tag name (e.g., "LOGS"), or CustomItem ID (e.g., "common_sword")
        int amount,
        @Nullable Tag<Material> resolvedBukkitTag // Pre-resolved by RecipeService if type is TAG and it's a known Bukkit tag
) {
    /**
     * Primary constructor.
     * @param type The type of requirement.
     * @param value The identifier string for the requirement.
     * @param amount The quantity required.
     * @param resolvedBukkitTag The pre-resolved Bukkit Tag if applicable, otherwise null.
     */
    public RequiredIngredient {
        Objects.requireNonNull(type, "RequirementType cannot be null.");
        Objects.requireNonNull(value, "Requirement value string cannot be null.");
        if (amount < 1) {
            throw new IllegalArgumentException("Required ingredient amount must be at least 1.");
        }
        // resolvedBukkitTag can be null
    }

    /**
     * Convenience constructor used during parsing when the BukkitTag is not yet resolved.
     * @param type The type of requirement.
     * @param value The identifier string.
     * @param amount The quantity required.
     */
    public RequiredIngredient(RequirementType type, String value, int amount) {
        this(type, value, amount, null); // BukkitTag will be resolved by RecipeService
    }

    /**
     * Checks if the given ItemStack satisfies this ingredient's type and custom ID/material,
     * IGNORING the amount. Used for checking if an item *could* be part of a shapeless recipe.
     *
     * @param itemStack The ItemStack to check.
     * @param itemService To resolve CustomItem IDs.
     * @param tagForMatching If this ingredient is a TAG, this is the resolved Tag<Material> to use for matching.
     *                       It could be the pre-resolved 'this.resolvedBukkitTag' or one resolved dynamically.
     * @return true if the item's type matches, false otherwise.
     */
    public boolean matchesTypeAndCustomId(@Nullable ItemStack itemStack, @NotNull ItemService itemService, @Nullable Tag<Material> tagForMatching) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }

        return switch (this.type) {
            case MATERIAL -> {
                Material requiredMat = Material.matchMaterial(this.value.toUpperCase());
                yield requiredMat != null && itemStack.getType() == requiredMat;
            }
            case TAG -> {
                // If tagForMatching is null (e.g., custom tag not yet supported or resolving failed), it won't match.
                yield tagForMatching != null && tagForMatching.isTagged(itemStack.getType());
            }
            case ITEM -> {
                if (!itemStack.hasItemMeta()) yield false;
                ItemMeta meta = itemStack.getItemMeta();
                if (meta == null) yield false; // Should not happen if hasItemMeta
                String customItemIdOnStack = NBTService.get(meta.getPersistentDataContainer(), NBTService.ITEM_ID_KEY, PersistentDataType.STRING, null);
                yield Objects.equals(this.value, customItemIdOnStack);
            }
            // default -> false; // Unreachable if enum is exhaustive
        };
    }

    /**
     * Checks if the given ItemStack fully satisfies this ingredient requirement (type, custom ID, AND amount).
     *
     * @param itemStack The ItemStack to check.
     * @param itemService To resolve CustomItem IDs.
     * @param tagForMatching If this ingredient is a TAG, this is the resolved Tag<Material> to use for matching.
     * @return true if the item matches type and has sufficient amount.
     */
    public boolean matchesFully(@Nullable ItemStack itemStack, @NotNull ItemService itemService, @Nullable Tag<Material> tagForMatching) {
        if (!matchesTypeAndCustomId(itemStack, itemService, tagForMatching)) {
            return false;
        }
        // itemStack cannot be null here if matchesTypeAndCustomId passed
        return itemStack.getAmount() >= this.amount;
    }

    @Override
    public String toString() {
        return "RequiredIngredient{" +
                "type=" + type +
                ", value='" + value + '\'' +
                ", amount=" + amount +
                (resolvedBukkitTag != null ? ", bukkitTag=" + resolvedBukkitTag.getKey() : "") +
                '}';
    }
}