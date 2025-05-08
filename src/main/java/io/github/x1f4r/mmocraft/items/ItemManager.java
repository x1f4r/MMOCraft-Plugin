package io.github.x1f4r.mmocraft.items;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
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
import java.util.logging.Logger;

public class ItemManager {

    private final MMOCore core;
    private final MMOPlugin plugin;
    private final Logger log;
    private final Map<String, ItemStack> customItemTemplates = new HashMap<>();

    public ItemManager(MMOCore core) {
        this.core = core;
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
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }

        List<String> lore = itemConfig.getStringList("lore");
        if (!lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>();
            lore.forEach(line -> coloredLore.add(ChatColor.translateAlternateColorCodes('&', line)));
            meta.setLore(coloredLore);
        }

        meta.setUnbreakable(itemConfig.getBoolean("unbreakable", false));

        ConfigurationSection enchantsSection = itemConfig.getConfigurationSection("enchants");
        if (enchantsSection != null) {
            for (String enchantKey : enchantsSection.getKeys(false)) {
                Enchantment enchantment = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(enchantKey.toLowerCase()));
                if (enchantment == null) enchantment = Enchantment.getByName(enchantKey.toUpperCase());
                if (enchantment != null) {
                    meta.addEnchant(enchantment, enchantsSection.getInt(enchantKey, 1), true);
                } else {
                    log.warning("Invalid enchantment '" + enchantKey + "' for item '" + itemId + "'.");
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
        }
        item.setItemMeta(meta);
        return item;
    }
}

