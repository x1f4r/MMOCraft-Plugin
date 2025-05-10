package io.github.x1f4r.mmocraft.crafting.models;

import io.github.x1f4r.mmocraft.core.MMOPlugin;
import io.github.x1f4r.mmocraft.utils.NBTKeys; // If checking custom item ingredients later
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.logging.Logger;

public class RequiredItem {

    private final IngredientType type;
    private final Material material; // Used if type is MATERIAL
    private final Tag<Material> tag; // Used if type is TAG
    private final String customItemId; // Used if type is CUSTOM_ITEM
    private final int amount;
    private static final Logger log = MMOPlugin.getMMOLogger();

    private RequiredItem(IngredientType type, Material material, Tag<Material> tag, String customItemId, int amount) {
        this.type = type;
        this.material = material;
        this.tag = tag;
        this.customItemId = customItemId; // Can be null
        this.amount = amount;
    }

    public static RequiredItem loadFromConfig(ConfigurationSection config, Map<String, Tag<Material>> customMaterialTags) {
        if (config == null) {
            log.warning("RequiredItem config section is null.");
            return null;
        }

        String typeStr = config.getString("type", "MATERIAL").toUpperCase();
        String valueStr = config.getString("value");
        int amount = config.getInt("amount", 1);

        if (valueStr == null || valueStr.isEmpty()) {
            log.warning("Missing 'value' in required item config: " + config.getCurrentPath());
            return null;
        }
        if (amount < 1) {
            log.warning("Invalid 'amount' (" + amount + ") in required item config, must be at least 1: " + config.getCurrentPath());
            amount = 1; // Default to 1 if invalid
        }

        IngredientType ingredientType;
        Material material = null;
        Tag<Material> tag = null;
        String customItemId = null;

        try {
            ingredientType = IngredientType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            log.warning("Invalid ingredient type '" + typeStr + "' in config: " + config.getCurrentPath() + ". Defaulting to MATERIAL.");
            ingredientType = IngredientType.MATERIAL;
        }

        switch (ingredientType) {
            case MATERIAL:
                material = Material.matchMaterial(valueStr.toUpperCase());
                if (material == null) {
                    log.warning("Invalid material '" + valueStr + "' for required item in config: " + config.getCurrentPath());
                    return null;
                }
                break;
            case TAG:
                tag = customMaterialTags.get(valueStr.toUpperCase());
                if (tag == null) {
                     log.warning("Unknown material tag '" + valueStr + "' for required item in config: " + config.getCurrentPath() + ". Ensure it's defined in RecipeManager.");
                     return null;
                }
                break;
            case ITEM:
                // valueStr should be the custom item ID (e.g., "aspect_of_the_end")
                customItemId = valueStr.toLowerCase();
                // Validation that this item ID exists could happen here or in RecipeManager/ItemManager if needed
                break;
            default:
                log.warning("Unsupported ingredient type '" + ingredientType + "' after parsing for config: " + config.getCurrentPath());
                return null;
        }
        return new RequiredItem(ingredientType, material, tag, customItemId, amount);
    }

    public boolean matches(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false; // Requires something, but slot is empty
        }

        boolean typeMatch = false;
        switch (this.type) {
            case MATERIAL:
                typeMatch = (item.getType() == this.material);
                break;
            case TAG:
                typeMatch = (this.tag != null && this.tag.isTagged(item.getType()));
                break;
            case ITEM:
                ItemMeta meta = item.getItemMeta();
                if (meta != null && NBTKeys.ITEM_ID_KEY != null) {
                    String itemIdVal = meta.getPersistentDataContainer().get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);
                    typeMatch = this.customItemId.equalsIgnoreCase(itemIdVal);
                }
                break;
            default:
                return false;
        }

        if (!typeMatch) {
            return false;
        }

        // Amount check
        if (item.getAmount() < this.amount) {
            return false;
        }

        return true;
    }

    /**
     * Checks if the given ItemStack matches the material, tag, or custom item ID of this RequiredItem,
     * ignoring the amount. Used for finding candidates for consumption in shapeless recipes.
     * @param item The ItemStack to check.
     * @return True if the type (material/tag/customId) matches, false otherwise.
     */
    public boolean matchesTypeAndCustomId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        switch (this.type) {
            case MATERIAL:
                return (item.getType() == this.material);
            case TAG:
                return (this.tag != null && this.tag.isTagged(item.getType()));
            case ITEM:
                ItemMeta meta = item.getItemMeta();
                if (meta != null && NBTKeys.ITEM_ID_KEY != null) {
                    String itemIdVal = meta.getPersistentDataContainer().get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);
                    return this.customItemId.equalsIgnoreCase(itemIdVal);
                }
                return false; // No meta or ITEM_ID_KEY means it can't be this custom item
            default:
                return false;
        }
    }

    // Getters
    public int getAmount() { return amount; }
    public IngredientType getType() { return type; }
    public Material getMaterial() { return material; } // Can be null
    public Tag<Material> getTag() { return tag; }       // Can be null
    public String getCustomItemId() { return customItemId; } // Can be null

    public enum IngredientType { MATERIAL, TAG, ITEM }

    @Override
    public String toString() { // For logging
        String valueDetail = "";
        switch (type) {
            case MATERIAL: valueDetail = material.name(); break;
            case TAG: valueDetail = "#" + (tag != null ? tag.getKey().getKey() : "UNKNOWN_TAG"); break;
            case ITEM: valueDetail = "ITEM:" + customItemId; break;
        }
        return "RequiredItem{type=" + type + ", value=" + valueDetail + ", amount=" + amount + '}';
    }
}

