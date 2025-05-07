package io.github.x1f4r.mmocraft.items;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.ChatColor;
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

public class ItemManager {

    private final MMOCraft plugin;
    private final Map<String, ItemStack> customItems = new HashMap<>();

    public ItemManager(MMOCraft plugin) {
        this.plugin = plugin;
        loadItems();
    }

    public void loadItems() {
        customItems.clear();
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            plugin.getLogger().info("items.yml not found, saving default.");
            plugin.saveResource("items.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(itemsFile);
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) {
            plugin.getLogger().warning("Could not find 'items' section in items.yml!");
            return;
        }

        for (String itemId : itemsSection.getKeys(false)) {
            ConfigurationSection itemConfig = itemsSection.getConfigurationSection(itemId);
            if (itemConfig != null) {
                ItemStack parsedItem = parseItem(itemId, itemConfig);
                if (parsedItem != null) {
                    customItems.put(itemId.toLowerCase(), parsedItem);
                    plugin.getLogger().info("Loaded custom item: " + itemId);
                }
            }
        }
        plugin.getLogger().info("Finished loading " + customItems.size() + " custom items.");
    }

    public ItemStack getItem(String itemId) {
        ItemStack template = customItems.get(itemId.toLowerCase());
        return (template != null) ? template.clone() : null;
    }

    public Set<String> getAllItemIds() {
        return Collections.unmodifiableSet(customItems.keySet());
    }

    private ItemStack parseItem(String itemId, ConfigurationSection itemConfig) {
        String matName = itemConfig.getString("material");
        if (matName == null) {
            plugin.getLogger().warning("Missing 'material' for item '" + itemId + "' in items.yml.");
            return null;
        }
        Material material = Material.matchMaterial(matName.toUpperCase());
        if (material == null) {
            plugin.getLogger().warning("Invalid material name '" + matName + "' for item '" + itemId + "'.");
            return null;
        }

        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            plugin.getLogger().warning("Could not get ItemMeta for material '" + matName + "' (item " + itemId + "). Item will be basic.");
            try {
                meta = item.getItemMeta(); // Retry getting meta
                if (meta != null && NBTKeys.ITEM_ID_KEY != null) {
                    meta.getPersistentDataContainer().set(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING, itemId.toLowerCase());
                    item.setItemMeta(meta);
                }
            } catch (Exception e) { /* ignore, failed to get meta */ }
            return item;
        }

        String name = itemConfig.getString("name");
        if (name != null) { meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name)); }

        List<String> lore = itemConfig.getStringList("lore");
        if (!lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>();
            lore.forEach(line -> coloredLore.add(ChatColor.translateAlternateColorCodes('&', line)));
            meta.setLore(coloredLore);
        }

        boolean unbreakable = itemConfig.getBoolean("unbreakable", false);
        meta.setUnbreakable(unbreakable);

        ConfigurationSection enchantsSection = itemConfig.getConfigurationSection("enchants");
        if (enchantsSection != null) {
            for (String enchantKey : enchantsSection.getKeys(false)) {
                Enchantment enchantment = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(enchantKey.toLowerCase()));
                if (enchantment == null) enchantment = Enchantment.getByName(enchantKey.toUpperCase()); // Fallback for older names
                if (enchantment != null) {
                    int level = enchantsSection.getInt(enchantKey, 1);
                    meta.addEnchant(enchantment, level, true);
                } else { plugin.getLogger().warning("Invalid enchantment identifier '" + enchantKey + "' for item '" + itemId + "'."); }
            }
        }

        ConfigurationSection attributesSection = itemConfig.getConfigurationSection("attributes");
        if (attributesSection != null) {
            for (String attributeKey : attributesSection.getKeys(false)) {
                try {
                    Attribute attribute = Attribute.valueOf(attributeKey.toUpperCase());
                    String valueStr = attributesSection.getString(attributeKey);
                    if (valueStr != null) {
                        String[] parts = valueStr.split(":");
                        if (parts.length < 2) throw new IllegalArgumentException("Attribute format must be amount:operation[:slot]");

                        double attrAmount = Double.parseDouble(parts[0]);
                        AttributeModifier.Operation operation = AttributeModifier.Operation.valueOf(parts[1].toUpperCase());
                        EquipmentSlot slot = null;
                        if (parts.length > 2) {
                            try { slot = EquipmentSlot.valueOf(parts[2].toUpperCase()); }
                            catch (IllegalArgumentException e) { plugin.getLogger().warning("Invalid equipment slot '" + parts[2] + "' for attribute '" + attributeKey + "' on item '" + itemId + "'. Modifier will apply broadly.");}
                        }
                        String modifierName = "mmocraft." + itemId + "." + attributeKey.toLowerCase();
                        AttributeModifier modifier = new AttributeModifier(UUID.randomUUID(), modifierName, attrAmount, operation, slot);
                        meta.addAttributeModifier(attribute, modifier);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to parse attribute '" + attributeKey + "' for item '" + itemId + "': " + e.getMessage());
                }
            }
        }

        List<String> flags = itemConfig.getStringList("item_flags");
        if (!flags.isEmpty()) {
            for (String flagName : flags) {
                try { meta.addItemFlags(ItemFlag.valueOf(flagName.toUpperCase())); }
                catch (IllegalArgumentException e) { plugin.getLogger().warning("Invalid ItemFlag '" + flagName + "' for item '" + itemId + "'."); }
            }
        }

        ConfigurationSection customStatsSection = itemConfig.getConfigurationSection("custom_stats");
        if (customStatsSection != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (NBTKeys.STRENGTH_KEY != null && customStatsSection.contains("STRENGTH")) {
                pdc.set(NBTKeys.STRENGTH_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("STRENGTH"));
            }
            if (NBTKeys.CRIT_CHANCE_KEY != null && customStatsSection.contains("CRIT_CHANCE")) {
                pdc.set(NBTKeys.CRIT_CHANCE_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("CRIT_CHANCE"));
            }
            if (NBTKeys.CRIT_DAMAGE_KEY != null && customStatsSection.contains("CRIT_DAMAGE")) {
                pdc.set(NBTKeys.CRIT_DAMAGE_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("CRIT_DAMAGE"));
            }
            if (NBTKeys.MANA_KEY != null) {
                if (customStatsSection.contains("MANA")) {
                    pdc.set(NBTKeys.MANA_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("MANA"));
                } else if (customStatsSection.contains("MANA_COST")) {
                    pdc.set(NBTKeys.MANA_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("MANA_COST"));
                } else if (customStatsSection.contains("MAX_MANA")) {
                    pdc.set(NBTKeys.MANA_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("MAX_MANA"));
                }
            }
            if (NBTKeys.SPEED_KEY != null && customStatsSection.contains("SPEED")) {
                pdc.set(NBTKeys.SPEED_KEY, PersistentDataType.INTEGER, customStatsSection.getInt("SPEED"));
            }
            // Defense Key Handling with Debugging
            if (NBTKeys.DEFENSE_KEY != null) { // Check if NBTKeys.DEFENSE_KEY itself is initialized
                if (customStatsSection.contains("DEFENSE")) {
                    int defenseVal = customStatsSection.getInt("DEFENSE");
                    pdc.set(NBTKeys.DEFENSE_KEY, PersistentDataType.INTEGER, defenseVal);
                    // ---- START DEBUG LOGGING ----
                    plugin.getLogger().info("[ItemManager DEBUG] Item: " + itemId + ", Set NBT DEFENSE_KEY to: " + defenseVal);
                    // ---- END DEBUG LOGGING ----
                } else {
                    // ---- START DEBUG LOGGING ----
                    plugin.getLogger().info("[ItemManager DEBUG] Item: " + itemId + ", custom_stats section does NOT contain 'DEFENSE'. PDC will not have DEFENSE_KEY set by ItemManager for this stat.");
                    // ---- END DEBUG LOGGING ----
                }
            }
        }

        if (NBTKeys.ITEM_ID_KEY != null) {
            meta.getPersistentDataContainer().set(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING, itemId.toLowerCase());
        } else {
            plugin.getLogger().severe("NBTKeys.ITEM_ID_KEY is null! Cannot tag item: " + itemId);
        }

        item.setItemMeta(meta);
        return item;
    }
}