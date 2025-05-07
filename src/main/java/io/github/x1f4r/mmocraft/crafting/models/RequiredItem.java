package io.github.x1f4r.mmocraft.crafting.models; // Updated package

import java.util.Map;
import io.github.x1f4r.mmocraft.MMOCraft;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
// import java.util.Map; // Keep if used, commented if not
import java.util.logging.Logger; // Keep if used

public class RequiredItem {

    private final IngredientType type;
    private final Material material; // Used if type is MATERIAL
    private final Tag<Material> tag; // Used if type is TAG
    private final int amount;
    // private final String customItemId; // For future use: if an ingredient must be a specific custom item

    private static final Logger logger = MMOCraft.getPlugin(MMOCraft.class).getLogger();

    private RequiredItem(IngredientType type, Material material, Tag<Material> tag, int amount) {
        this.type = type;
        this.material = material;
        this.tag = tag;
        this.amount = amount;
    }

    public static RequiredItem loadFromConfig(MMOCraft plugin, ConfigurationSection config, Map<String, Tag<Material>> customMaterialTags) {
        if (config == null) {
            logger.warning("RequiredItem config section is null.");
            return null;
        }

        String typeStr = config.getString("type", "MATERIAL").toUpperCase();
        String valueStr = config.getString("value");
        int amount = config.getInt("amount", 1);

        if (valueStr == null || valueStr.isEmpty()) {
            logger.warning("Missing 'value' in required item config: " + config.getCurrentPath());
            return null;
        }
        if (amount < 1) {
            logger.warning("Invalid 'amount' (" + amount + ") in required item config, must be at least 1: " + config.getCurrentPath());
            return null;
        }

        IngredientType ingredientType;
        Material material = null;
        Tag<Material> tag = null;

        try {
            ingredientType = IngredientType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid ingredient type '" + typeStr + "' in config: " + config.getCurrentPath() + ". Defaulting to MATERIAL.");
            ingredientType = IngredientType.MATERIAL;
        }

        switch (ingredientType) {
            case MATERIAL:
                material = Material.matchMaterial(valueStr.toUpperCase());
                if (material == null) {
                    logger.warning("Invalid material '" + valueStr + "' for required item in config: " + config.getCurrentPath());
                    return null;
                }
                break;
            case TAG:
                tag = customMaterialTags.get(valueStr.toUpperCase()); // Use the passed custom tags
                if (tag == null) {
                    // Check Bukkit tags as a fallback if you want, but usually custom tags are specific
                    // tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft(valueStr.toLowerCase()), Material.class);
                    // if (tag == null) {
                        logger.warning("Unknown material tag '" + valueStr + "' for required item in config: " + config.getCurrentPath() + ". Ensure it's defined in RecipeManager or is a valid Bukkit tag if supported.");
                        return null;
                    // }
                }
                break;
            // case CUSTOM_ITEM: // Future
            //     customItemId = valueStr;
            //     break;
            default:
                logger.warning("Unsupported ingredient type '" + ingredientType + "' after parsing for config: " + config.getCurrentPath());
                return null;
        }
        return new RequiredItem(ingredientType, material, tag, amount);
    }

    public boolean matches(ItemStack item) {
        // String requiredInfo = (this.type == IngredientType.MATERIAL) ? (this.material != null ? this.material.name() : "NULL_MATERIAL") : (this.tag != null ? "Tag:" + this.tag.getKey().getKey() : "NULL_TAG");
        // logger.finer("    [ReqCheck] Item: " + (item != null ? item.getType() : "NULL_ITEM") + " vs Req: " + requiredInfo + " Amount: " + this.amount);

        if (item == null || item.getType() == Material.AIR) {
            // logger.finer("    [ReqCheck] -> Fail: Item slot is empty or AIR.");
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
            // case CUSTOM_ITEM: // Future
            //     if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING)) {
            //         String itemIdVal = item.getItemMeta().getPersistentDataContainer().get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);
            //         typeMatch = this.customItemId.equalsIgnoreCase(itemIdVal);
            //     }
            //     break;
            default:
                // logger.finer("    [ReqCheck] -> Fail: Unknown requirement type during match.");
                return false;
        }

        if (!typeMatch) {
            // logger.finer("    [ReqCheck] -> Fail: Type mismatch (Required: " + requiredInfo + ", Found: " + item.getType() + ")");
            return false;
        }

        if (item.getAmount() < this.amount) {
            // logger.finer("    [ReqCheck] -> Fail: Amount mismatch for " + item.getType() + " (Required: " + this.amount + ", Found: " + item.getAmount() + ")");
            return false;
        }

        // TODO: Add NBT/meta check later if ingredients need specific custom item properties beyond type/tag
        // logger.finer("    [ReqCheck] -> Success: Item " + item.getType() + " matches requirement.");
        return true;
    }

    // Getters
    public int getAmount() { return amount; }
    public IngredientType getType() { return type; }
    public Material getMaterial() { return material; } // Can be null if type is TAG
    public Tag<Material> getTag() { return tag; }       // Can be null if type is MATERIAL

    public enum IngredientType { MATERIAL, TAG /*, CUSTOM_ITEM */ } // CUSTOM_ITEM for future
}
