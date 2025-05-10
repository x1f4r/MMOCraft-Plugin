package io.github.x1f4r.mmocraft.items;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ItemManager {

    private final MMOPlugin plugin;
    private final Logger log;
    private final Map<String, ItemStack> customItemTemplates = new HashMap<>();

    public ItemManager(MMOCore core) {
        this.plugin = core.getPlugin();
        this.log = MMOPlugin.getMMOLogger();
    }

    public void loadItems() {
        customItemTemplates.clear();
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        // saveResource is already called in MMOCore, so itemsFile should exist or be created by Bukkit
        if (!itemsFile.exists()) {
             log.severe("items.yml not found even after MMOCore tried to save it. Cannot load items.");
             return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(itemsFile);
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) {
            log.warning("Could not find 'items' section in items.yml or it's empty!");
            return;
        }

        for (String itemId : itemsSection.getKeys(false)) {
            ConfigurationSection itemConfig = itemsSection.getConfigurationSection(itemId);
            if (itemConfig != null) {
                try {
                    ItemStack parsedItem = parseItemTemplate(itemId, itemConfig);
                    if (parsedItem != null) {
                        customItemTemplates.put(itemId.toLowerCase(), parsedItem);
                    }
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Failed to parse custom item: " + itemId, e);
                }
            }
        }
        log.info("Loaded " + customItemTemplates.size() + " custom item templates.");
    }

    public ItemStack getItem(String itemId) {
        ItemStack template = customItemTemplates.get(itemId.toLowerCase());
        return (template != null) ? template.clone() : null;
    }

    public Set<String> getAllItemIds() {
        return Collections.unmodifiableSet(customItemTemplates.keySet());
    }

    private ItemStack parseItemTemplate(String itemId, ConfigurationSection itemConfig) {
        String matName = itemConfig.getString("material");
        if (matName == null) {
            log.warning("Missing 'material' for item '" + itemId + "'. Skipping.");
            return null;
        }
        Material material = Material.matchMaterial(matName.toUpperCase());
        if (material == null) {
            log.warning("Invalid material '" + matName + "' for item '" + itemId + "'. Skipping.");
            return null;
        }

        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            log.severe("Could not get ItemMeta for material '" + matName + "' (item " + itemId + "). Skipping.");
            return null;
        }

        meta.getPersistentDataContainer().set(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING, itemId.toLowerCase());

        String name = itemConfig.getString("name");
        if (name != null) {
            meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(name));
        }

        List<String> loreLines = itemConfig.getStringList("lore");
        if (!loreLines.isEmpty()) {
            List<Component> componentLore = new ArrayList<>();
            loreLines.forEach(line -> componentLore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line)));
            meta.lore(componentLore);
        }

        meta.setUnbreakable(itemConfig.getBoolean("unbreakable", false));

        ConfigurationSection enchantsSection = itemConfig.getConfigurationSection("enchants");
        if (enchantsSection != null) {
            for (String enchantKey : enchantsSection.getKeys(false)) {
                org.bukkit.NamespacedKey key;
                String keyString = enchantKey.toLowerCase();
                if (keyString.contains(":")) {
                    // Already a full NamespacedKey string, e.g., "minecraft:sharpness"
                    try {
                        key = org.bukkit.NamespacedKey.fromString(keyString);
                    } catch (IllegalArgumentException e) {
                        log.warning("Invalid namespaced key format '" + enchantKey + "' for item '" + itemId + "'. Skipping enchantment.");
                        continue;
                    }
                } else {
                    // Simple key, assume minecraft namespace, e.g., "sharpness"
                    key = org.bukkit.NamespacedKey.minecraft(keyString);
                }

                Enchantment enchantment = Enchantment.getByKey(key);
                if (enchantment != null) {
                    meta.addEnchant(enchantment, enchantsSection.getInt(enchantKey, 1), true);
                } else {
                    log.warning("Invalid enchantment key '" + enchantKey + "' (resolved to NamespacedKey: '" + key.toString() + "') for item '" + itemId + "'. Please use Minecraft's namespaced key (e.g., 'sharpness') or a valid full key (e.g., 'minecraft:sharpness').");
                }
            }
        }

        ConfigurationSection attributesSection = itemConfig.getConfigurationSection("attributes");
        if (attributesSection != null) {
            for (String attributeKey : attributesSection.getKeys(false)) {
                try {
                    Attribute attribute = Attribute.valueOf(attributeKey.toUpperCase());
                    String valueStr = attributesSection.getString(attributeKey);
                    if (valueStr == null) continue;

                    String[] parts = valueStr.split(":");
                    double attrAmount = Double.parseDouble(parts[0]);
                    AttributeModifier.Operation operation = AttributeModifier.Operation.valueOf(parts[1].toUpperCase());
                    EquipmentSlot slot = (parts.length > 2) ? EquipmentSlot.valueOf(parts[2].toUpperCase()) : null;

                    AttributeModifier modifier = new AttributeModifier(UUID.randomUUID(), "mmocraft." + itemId + "." + attributeKey.toLowerCase(), attrAmount, operation, slot);
                    meta.addAttributeModifier(attribute, modifier);
                } catch (Exception e) {
                    log.log(Level.WARNING, "Failed to parse attribute '" + attributeKey + "' for item '" + itemId + "': " + e.getMessage());
                }
            }
        }

        List<String> flags = itemConfig.getStringList("item_flags");
        flags.forEach(flagName -> {
            try { meta.addItemFlags(ItemFlag.valueOf(flagName.toUpperCase())); }
            catch (IllegalArgumentException e) { log.warning("Invalid ItemFlag '" + flagName + "' for item '" + itemId + "'."); }
        });

        ConfigurationSection customStatsSection = itemConfig.getConfigurationSection("custom_stats");
        if (customStatsSection != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (customStatsSection.contains("STRENGTH")) pdc.set(NBTKeys.STRENGTH_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("STRENGTH"));
            if (customStatsSection.contains("DEFENSE")) pdc.set(NBTKeys.DEFENSE_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("DEFENSE"));
            if (customStatsSection.contains("MAX_MANA")) pdc.set(NBTKeys.MANA_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("MAX_MANA"));
            if (customStatsSection.contains("MANA_COST")) pdc.set(NBTKeys.MANA_COST_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("MANA_COST"));
            if (customStatsSection.contains("CRIT_CHANCE")) pdc.set(NBTKeys.CRIT_CHANCE_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("CRIT_CHANCE"));
            if (customStatsSection.contains("CRIT_DAMAGE")) pdc.set(NBTKeys.CRIT_DAMAGE_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("CRIT_DAMAGE"));
            if (customStatsSection.contains("SPEED")) pdc.set(NBTKeys.SPEED_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("SPEED"));
            if (customStatsSection.contains("MINING_SPEED")) pdc.set(NBTKeys.MINING_SPEED_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("MINING_SPEED"));
            if (customStatsSection.contains("FORAGING_SPEED")) pdc.set(NBTKeys.FORAGING_SPEED_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("FORAGING_SPEED"));
            if (customStatsSection.contains("FISHING_SPEED")) pdc.set(NBTKeys.FISHING_SPEED_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("FISHING_SPEED"));
            if (customStatsSection.contains("SHOOTING_SPEED")) pdc.set(NBTKeys.SHOOTING_SPEED_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("SHOOTING_SPEED"));
            if (customStatsSection.contains("TREE_BOW_POWER")) pdc.set(NBTKeys.TREE_BOW_POWER_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("TREE_BOW_POWER"));
            if (customStatsSection.contains("TREE_BOW_MAGICAL_AMMO")) pdc.set(NBTKeys.TREE_BOW_MAGICAL_AMMO_KEY, PersistentDataType.BYTE, (byte)customStatsSection.getInt("TREE_BOW_MAGICAL_AMMO"));
            if (customStatsSection.contains("INSTANT_SHOOT_BOW")) pdc.set(NBTKeys.INSTANT_SHOOT_BOW_TAG, PersistentDataType.BYTE, (byte)customStatsSection.getInt("INSTANT_SHOOT_BOW"));
            if (customStatsSection.contains("ABILITY_ID")) pdc.set(NBTKeys.ABILITY_ID_KEY, PersistentDataType.STRING, customStatsSection.getString("ABILITY_ID"));
            if (customStatsSection.contains("ABILITY_COOLDOWN_SECONDS")) pdc.set(NBTKeys.ABILITY_COOLDOWN_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("ABILITY_COOLDOWN_SECONDS"));
            if (customStatsSection.contains("ABILITY_DAMAGE")) pdc.set(NBTKeys.ABILITY_DAMAGE_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("ABILITY_DAMAGE"));
            if (customStatsSection.contains("UTILITY_ID")) pdc.set(NBTKeys.UTILITY_ID_KEY, PersistentDataType.STRING, customStatsSection.getString("UTILITY_ID"));
        }

        // After all other parsing, if it's a compactor, try to parse slot count from lore
        if (itemId.startsWith("personal_compactor_")) {
            final String SLOTS_LORE_PREFIX_UNCOLORED = "Slots: "; // The part after color codes
            int compactorSlots = 1; // Default to 1 slot if not found or parse error
            if (meta.hasLore() && meta.lore() != null) {
                for (Component loreComponent : meta.lore()) {
                    if (loreComponent == null) continue;
                    // Strip color codes to reliably find the prefix
                    String uncoloredLoreLine = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(loreComponent);
                    if (uncoloredLoreLine.startsWith(SLOTS_LORE_PREFIX_UNCOLORED)) {
                        try {
                            String numberPart = uncoloredLoreLine.substring(SLOTS_LORE_PREFIX_UNCOLORED.length()).trim();
                            compactorSlots = Integer.parseInt(numberPart);
                            if (compactorSlots <= 0) compactorSlots = 1; // Ensure at least 1
                            // log.finer("Parsed " + compactorSlots + " slots for compactor " + itemId + " from lore: '" + plainLoreLine + "'");
                            break; 
                        } catch (NumberFormatException e) {
                            log.warning("Could not parse slot count for compactor " + itemId + " from lore line: '" + uncoloredLoreLine + "'. Defaulting to 1 slot.");
                        }
                    }
                }
            }
            meta.getPersistentDataContainer().set(NBTKeys.COMPACTOR_SLOT_COUNT_KEY, PersistentDataType.INTEGER, compactorSlots);
            // log.info("Set " + compactorSlots + " NBT slots for " + itemId);
        }

        item.setItemMeta(meta);
        return item;
    }
}

