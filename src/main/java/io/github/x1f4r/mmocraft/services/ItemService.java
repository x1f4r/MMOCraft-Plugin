package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.abilities.ItemAbility; // Now used for lore
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import io.github.x1f4r.mmocraft.items.AttributeModifierData;
import io.github.x1f4r.mmocraft.items.CustomItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull; // Optional

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ItemService implements Service {
    private MMOCore core;
    private LoggingService logging;
    private ConfigService configService;
    // NBTService static keys are accessed via NBTService.KEY_NAME
    private AbilityService abilityService; // Now crucial for lore and NBT

    private final Map<String, CustomItem> itemTemplateRegistry = new ConcurrentHashMap<>();

    public ItemService(MMOCore core) {
        this.core = core;
    }

    @Override
    public void initialize(MMOCore core) {
        this.logging = core.getService(LoggingService.class);
        this.configService = core.getService(ConfigService.class);
        // NBTService.class is just for static keys, no instance needed by ItemService directly often.

        // AbilityService MUST be ready before ItemService fully initializes if lore depends on it.
        // MMOCore's registration order should handle this.
        try {
            this.abilityService = core.getService(AbilityService.class);
        } catch (IllegalStateException e) {
            logging.severe("ItemService FAILED to initialize: AbilityService not available. This is a critical order dependency. Ensure AbilityService is registered before ItemService in MMOCore.", e);
            throw e; // Prevent ItemService from loading incorrectly
        }

        FileConfiguration itemsConfig = configService.getConfig(ConfigService.ITEMS_CONFIG_FILENAME);
        if (itemsConfig.getKeys(false).isEmpty() && itemsConfig.getConfigurationSection("items") == null) {
            logging.warn("'" + ConfigService.ITEMS_CONFIG_FILENAME + "' appears to be empty or missing 'items' section. No custom items will be loaded initially.");
        } else {
            loadItemsFromConfig(itemsConfig);
        }

        configService.subscribeToReload(ConfigService.ITEMS_CONFIG_FILENAME, reloadedConfig -> {
            logging.info("Reloading item definitions from " + ConfigService.ITEMS_CONFIG_FILENAME + "...");
            // Re-fetch AbilityService in case it was somehow re-registered (unlikely but safe)
            try { this.abilityService = core.getService(AbilityService.class); }
            catch (IllegalStateException ignored) { logging.warn("AbilityService became unavailable during item config reload. Ability lore might be incomplete."); }
            loadItemsFromConfig(reloadedConfig);
        });

        logging.info(getServiceName() + " initialized. Loaded " + itemTemplateRegistry.size() + " item templates.");
    }

    // shutdown(), loadItemsFromConfig(), parseItemTemplate(), getCustomItemTemplate(),
    // getCustomItemTemplateFromItemStack(), getAllCustomItemIds() remain the same as in Part 2, Batch 1.
    // Ensure parseItemTemplate correctly populates all fields of CustomItem, including:
    // linkedAbilityId, overrideManaCost, overrideCooldownTicks, and genericCustomNbt.

    // --- Start: Methods from Part 2, Batch 1 (ensure they are present and correct) ---
    @Override
    public void shutdown() {
        itemTemplateRegistry.clear();
        logging.info(getServiceName() + " shutdown. Item templates cleared.");
    }

    private void loadItemsFromConfig(FileConfiguration config) {
        itemTemplateRegistry.clear(); // Clear previous templates on reload
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) {
            logging.info("No 'items' section found in " + ConfigService.ITEMS_CONFIG_FILENAME + ". No custom items loaded.");
            return;
        }

        for (String itemId : itemsSection.getKeys(false)) {
            ConfigurationSection itemConfig = itemsSection.getConfigurationSection(itemId);
            if (itemConfig == null) {
                logging.warn("Item configuration for '" + itemId + "' is malformed. Skipping.");
                continue;
            }

            try {
                CustomItem template = parseItemTemplate(itemId.toLowerCase(), itemConfig); // Ensure consistent lowercase ID
                if (template != null) {
                    itemTemplateRegistry.put(template.id(), template);
                }
            } catch (Exception e) {
                logging.severe("Failed to parse custom item template: '" + itemId + "'. Error: " + e.getMessage(), e);
            }
        }
        logging.info("Loaded/Reloaded " + itemTemplateRegistry.size() + " item templates from " + ConfigService.ITEMS_CONFIG_FILENAME);
    }

    private CustomItem parseItemTemplate(String itemId, ConfigurationSection itemConfig) {
        Material material = Material.matchMaterial(itemConfig.getString("material", "STONE"));
        if (material == null) {
            logging.warn("Invalid material '" + itemConfig.getString("material") + "' for item '" + itemId + "'. Defaulting to STONE.");
            material = Material.STONE;
        }

        String rawName = itemConfig.getString("name", "&f" + itemId);
        Component displayName = LegacyComponentSerializer.legacyAmpersand().deserialize(rawName)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);

        List<Component> lore = itemConfig.getStringList("lore").stream()
                .map(line -> LegacyComponentSerializer.legacyAmpersand().deserialize(line)
                        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                .collect(Collectors.toList());

        boolean unbreakable = itemConfig.getBoolean("unbreakable", false);

        Map<Enchantment, Integer> enchantments = new HashMap<>();
        ConfigurationSection enchantsSection = itemConfig.getConfigurationSection("enchants");
        if (enchantsSection != null) {
            for (String enchantKey : enchantsSection.getKeys(false)) {
                Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(enchantKey.toLowerCase()));
                if (ench != null) {
                    enchants.put(ench, enchantsSection.getInt(enchantKey, 1));
                } else {
                    logging.warn("Unknown enchantment key '" + enchantKey + "' for item '" + itemId + "'.");
                }
            }
        }

        Set<ItemFlag> itemFlags = itemConfig.getStringList("item_flags").stream()
                .map(flagName -> {
                    try { return ItemFlag.valueOf(flagName.toUpperCase()); }
                    catch (IllegalArgumentException e) {
                        logging.warn("Invalid ItemFlag '" + flagName + "' for item '" + itemId + "'."); return null;
                    }
                })
                .filter(Objects::nonNull).collect(Collectors.toSet());

        List<AttributeModifierData> vanillaAttributes = new ArrayList<>();
        ConfigurationSection attributesSection = itemConfig.getConfigurationSection("attributes");
        if (attributesSection != null) {
            for (String attrKey : attributesSection.getKeys(false)) {
                try {
                    Attribute attribute = Attribute.valueOf(attrKey.toUpperCase());
                    String valueString = attributesSection.getString(attrKey, "0:ADD_NUMBER");
                    String[] parts = valueString.split(":");
                    double amount = Double.parseDouble(parts[0]);
                    AttributeModifier.Operation operation = AttributeModifier.Operation.valueOf(parts[1].toUpperCase());
                    EquipmentSlot slot = (parts.length > 2) ? EquipmentSlot.valueOf(parts[2].toUpperCase()) : null;
                    String modifierName = "mmoc." + itemId + "." + attribute.getKey().getKey();
                    vanillaAttributes.add(new AttributeModifierData(attribute, modifierName, amount, operation, slot));
                } catch (Exception e) {
                    logging.warn("Failed to parse vanilla attribute '" + attrKey + "' for item '" + itemId + "': " + e.getMessage());
                }
            }
        }

        ConfigurationSection customStatsSection = itemConfig.getConfigurationSection("custom_stats");
        int mmoStr = 0, mmoDef = 0, mmoCritC = 0, mmoCritD = 0, mmoHp = 0, mmoMp = 0, mmoSpdPct = 0;
        int mmoMineSpd = 0, mmoForageSpd = 0, mmoFishSpd = 0, mmoShootSpd = 0;

        if (customStatsSection != null) {
            mmoStr = customStatsSection.getInt("STRENGTH", 0);
            mmoDef = customStatsSection.getInt("DEFENSE", 0);
            mmoCritC = customStatsSection.getInt("CRIT_CHANCE", 0);
            mmoCritD = customStatsSection.getInt("CRIT_DAMAGE", 0);
            mmoHp = customStatsSection.getInt("MAX_HEALTH_BONUS", 0);
            mmoMp = customStatsSection.getInt("MAX_MANA_BONUS", 0);
            mmoSpdPct = customStatsSection.getInt("SPEED_BONUS_PERCENT", 0);
            mmoMineSpd = customStatsSection.getInt("MINING_SPEED", 0);
            mmoForageSpd = customStatsSection.getInt("FORAGING_SPEED",0);
            mmoFishSpd = customStatsSection.getInt("FISHING_SPEED",0);
            mmoShootSpd = customStatsSection.getInt("SHOOTING_SPEED",0);
        }

        String linkedAbilityId = null; Integer overrideMana = null; Integer overrideCooldown = null;
        ConfigurationSection abilityConfig = itemConfig.getConfigurationSection("ability");
        if (abilityConfig != null) {
            linkedAbilityId = abilityConfig.getString("id");
            if (abilityConfig.contains("mana_cost")) overrideMana = abilityConfig.getInt("mana_cost");
            if (abilityConfig.contains("cooldown_ticks")) overrideCooldown = abilityConfig.getInt("cooldown_ticks");
        }

        Map<String, Object> genericNbt = new HashMap<>();
        ConfigurationSection genericNbtConfig = itemConfig.getConfigurationSection("custom_ability_nbt");
        if (genericNbtConfig != null) {
            for (String key : genericNbtConfig.getKeys(false)) {
                genericNbt.put(key.toUpperCase(), genericNbtConfig.get(key)); // Store keys uppercase for consistency with NBTService string constants
            }
        }
        // Special handling for compactor NBT, ensuring these generic NBT keys are consistent if defined in items.yml
        // (NBTService defines the NamespacedKeys, CustomItem.getGenericNbtValue uses string keys)
        if (itemId.startsWith("personal_compactor_")) {
            genericNbt.putIfAbsent(NBTService.COMPACTOR_UTILITY_ID_KEY.getKey().replace("mmoc_","").toUpperCase(), itemId);
            int defaultSlots = 1; // Default to 1 if not specified in custom_ability_nbt
            if(itemId.equals("personal_compactor_4000")) defaultSlots = 3;
            if(itemId.equals("personal_compactor_5000")) defaultSlots = 5;
            genericNbt.putIfAbsent(NBTService.COMPACTOR_SLOT_COUNT_KEY.getKey().replace("mmoc_","").toUpperCase(), defaultSlots);
        }


        return new CustomItem(itemId, material, displayName, lore, unbreakable, enchantments, itemFlags,
                vanillaAttributes, mmoStr, mmoDef, mmoCritC, mmoCritD, mmoHp, mmoMp, mmoSpdPct,
                mmoMineSpd, mmoForageSpd, mmoFishSpd, mmoShootSpd,
                linkedAbilityId, overrideMana, overrideCooldown, genericNbt);
    }

    @Nullable
    public CustomItem getCustomItemTemplate(@Nullable String itemId) {
        if (itemId == null) return null;
        return itemTemplateRegistry.get(itemId.toLowerCase());
    }

    @Nullable
    public CustomItem getCustomItemTemplateFromItemStack(@Nullable ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) return null;
        ItemMeta meta = itemStack.getItemMeta();
        // meta cannot be null if hasItemMeta is true, but check anyway for safety with older MC versions if ever ported
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemId = NBTService.get(pdc, NBTService.ITEM_ID_KEY, PersistentDataType.STRING, null);
        return (itemId != null) ? getCustomItemTemplate(itemId) : null;
    }

    @NotNull
    public Set<String> getAllCustomItemIds() {
        return Collections.unmodifiableSet(itemTemplateRegistry.keySet());
    }

    @NotNull
    public ItemStack createItemStack(@NotNull String itemId, int amount) {
        CustomItem template = getCustomItemTemplate(itemId);
        if (template == null) {
            logging.warn("Attempted to create ItemStack for unknown CustomItem ID: '" + itemId + "'. Returning BARRIER item.");
            ItemStack errorItem = new ItemStack(Material.BARRIER, Math.max(1, amount));
            ItemMeta errorMeta = errorItem.getItemMeta();
            if (errorMeta != null) { // Should not be null for BARRIER
                errorMeta.displayName(Component.text("Unknown Item: " + itemId, NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
                errorItem.setItemMeta(errorMeta);
            }
            return errorItem;
        }
        return createItemStack(template, amount);
    }
    // --- End: Methods from Part 2, Batch 1 ---

    // --- MODIFIED createItemStack for Part 5 (Lore and Ability NBT) ---
    @NotNull
    public ItemStack createItemStack(@NotNull CustomItem template, int amount) {
        Objects.requireNonNull(template, "CustomItem template cannot be null for ItemStack creation.");
        if (amount <= 0) amount = 1;

        ItemStack itemStack = new ItemStack(template.material(), amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            logging.severe("Failed to get ItemMeta for material: " + template.material() + " (Item ID: " + template.id() + "). Returning basic ItemStack.");
            return itemStack;
        }

        // Apply Display Name & Base Lore (No Italics by default)
        meta.displayName(template.displayName().decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        List<Component> finalLore = new ArrayList<>();
        template.lore().forEach(line -> finalLore.add(line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)));

        // Add MMO Stats to Lore
        addStatToLore(finalLore, "Strength", template.mmoStrength(), NamedTextColor.RED);
        addStatToLore(finalLore, "Defense", template.mmoDefense(), NamedTextColor.GREEN);
        addStatToLore(finalLore, "Crit Chance", template.mmoCritChance(), NamedTextColor.GOLD, "%");
        addStatToLore(finalLore, "Crit Damage", template.mmoCritDamage(), NamedTextColor.GOLD, "%");
        addStatToLore(finalLore, "Max Health", template.mmoMaxHealthBonus(), NamedTextColor.RED);
        addStatToLore(finalLore, "Max Mana", template.mmoMaxManaBonus(), NamedTextColor.AQUA);
        addStatToLore(finalLore, "Speed", template.mmoSpeedBonusPercent(), NamedTextColor.WHITE, "%");
        addStatToLore(finalLore, "Mining Speed", template.mmoMiningSpeedBonus(), NamedTextColor.YELLOW);
        addStatToLore(finalLore, "Foraging Speed", template.mmoForagingSpeedBonus(), NamedTextColor.DARK_GREEN);
        addStatToLore(finalLore, "Fishing Speed", template.mmoFishingSpeedBonus(), NamedTextColor.BLUE);
        addStatToLore(finalLore, "Shooting Speed", template.mmoShootingSpeedBonus(), NamedTextColor.LIGHT_PURPLE);


        // --- Add Ability Info to Lore ---
        if (template.linkedAbilityId() != null && this.abilityService != null) {
            ItemAbility ability = abilityService.getAbility(template.linkedAbilityId());
            if (ability != null) {
                if (!finalLore.isEmpty() && finalLore.get(finalLore.size()-1) != Component.empty()) { // Check last line isn't already empty
                    finalLore.add(Component.empty()); // Spacer line before ability info
                }

                String activationActionsString = ability.getActivationActions().stream()
                        .map(action -> action.name().replace("_AIR", "").replace("_BLOCK", "").replace("_", " "))
                        .distinct().collect(Collectors.joining("/"));


                finalLore.add(Component.text("Ability: ", NamedTextColor.GOLD)
                        .append(ability.getDisplayName().colorIfAbsent(NamedTextColor.YELLOW))
                        .append(Component.text(" [", NamedTextColor.DARK_GRAY))
                        .append(Component.text(activationActionsString, NamedTextColor.YELLOW, TextDecoration.BOLD))
                        .append(Component.text("]", NamedTextColor.DARK_GRAY))
                        .decoration(TextDecoration.ITALIC, false)
                );
                // Add ability's own description lines
                ability.getDescription(template).forEach(descLine ->
                        finalLore.add(descLine.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                );

                int manaCost = abilityService.getActualManaCost(template, ability);
                int cooldownTicks = abilityService.getActualCooldownTicks(template, ability);
                if (manaCost > 0) {
                    finalLore.add(Component.text("Mana Cost: ", NamedTextColor.DARK_AQUA).append(Component.text(manaCost, NamedTextColor.AQUA)).decoration(TextDecoration.ITALIC, false));
                }
                if (cooldownTicks > 0) {
                    finalLore.add(Component.text("Cooldown: ", NamedTextColor.DARK_GRAY).append(Component.text(String.format("%.1fs", cooldownTicks / 20.0), NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
                }
            } else {
                logging.warnOnce("LoreGen_UnkAbil_" + template.id(), "Item template '" + template.id() + "' links to unknown ability ID: '" + template.linkedAbilityId() + "' for lore generation.");
            }
        }
        meta.lore(finalLore);

        // Apply other item properties
        meta.setUnbreakable(template.unbreakable());
        template.enchantments().forEach((ench, lvl) -> meta.addEnchant(ench, lvl, true));
        template.itemFlags().forEach(meta::addItemFlags);

        // Apply Vanilla Attribute Modifiers
        template.vanillaAttributeModifiers().forEach(modData -> {
            AttributeModifier modifier = new AttributeModifier(
                    UUID.randomUUID(), modData.name(), modData.amount(), modData.operation(), modData.slot()
            );
            meta.addAttributeModifier(modData.attribute(), modifier);
        });

        // --- Apply NBT Data ---
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NBTService.set(pdc, NBTService.ITEM_ID_KEY, PersistentDataType.STRING, template.id());

        // Core MMOCraft Stats to NBT
        if (template.mmoStrength() != 0) NBTService.set(pdc, NBTService.ITEM_STAT_STRENGTH, PersistentDataType.INTEGER, template.mmoStrength());
        if (template.mmoDefense() != 0) NBTService.set(pdc, NBTService.ITEM_STAT_DEFENSE, PersistentDataType.INTEGER, template.mmoDefense());
        if (template.mmoCritChance() != 0) NBTService.set(pdc, NBTService.ITEM_STAT_CRIT_CHANCE, PersistentDataType.INTEGER, template.mmoCritChance());
        if (template.mmoCritDamage() != 0) NBTService.set(pdc, NBTService.ITEM_STAT_CRIT_DAMAGE, PersistentDataType.INTEGER, template.mmoCritDamage());
        if (template.mmoMaxHealthBonus() != 0) NBTService.set(pdc, NBTService.ITEM_STAT_MAX_HEALTH_BONUS, PersistentDataType.INTEGER, template.mmoMaxHealthBonus());
        if (template.mmoMaxManaBonus() != 0) NBTService.set(pdc, NBTService.ITEM_STAT_MAX_MANA_BONUS, PersistentDataType.INTEGER, template.mmoMaxManaBonus());
        if (template.mmoSpeedBonusPercent() != 0) NBTService.set(pdc, NBTService.ITEM_STAT_SPEED_BONUS_PERCENT, PersistentDataType.INTEGER, template.mmoSpeedBonusPercent());
        if (template.mmoMiningSpeedBonus() != 0) NBTService.set(pdc, NBTService.ITEM_STAT_MINING_SPEED_BONUS, PersistentDataType.INTEGER, template.mmoMiningSpeedBonus());
        if (template.mmoForagingSpeedBonus() != 0) NBTService.set(pdc, NBTService.ITEM_STAT_FORAGING_SPEED_BONUS, PersistentDataType.INTEGER, template.mmoForagingSpeedBonus());
        if (template.mmoFishingSpeedBonus() != 0) NBTService.set(pdc, NBTService.ITEM_STAT_FISHING_SPEED_BONUS, PersistentDataType.INTEGER, template.mmoFishingSpeedBonus());
        if (template.mmoShootingSpeedBonus() != 0) NBTService.set(pdc, NBTService.ITEM_STAT_SHOOTING_SPEED_BONUS, PersistentDataType.INTEGER, template.mmoShootingSpeedBonus());

        // Ability Linkage NBT
        if (template.linkedAbilityId() != null) {
            NBTService.set(pdc, NBTService.ABILITY_ID_KEY, PersistentDataType.STRING, template.linkedAbilityId());
        }
        if (template.overrideManaCost() != null) {
            NBTService.set(pdc, NBTService.ABILITY_MANA_COST_OVERRIDE_KEY, PersistentDataType.INTEGER, template.overrideManaCost());
        }
        if (template.overrideCooldownTicks() != null) {
            NBTService.set(pdc, NBTService.ABILITY_COOLDOWN_TICKS_OVERRIDE_KEY, PersistentDataType.INTEGER, template.overrideCooldownTicks());
        }

        // Generic Custom NBT (for ability parameters, compactor info, tool abilities etc.)
        template.genericCustomNbt().forEach((keyString, value) -> {
            // Key string from items.yml should be simple, e.g., "TELEPORT_RANGE"
            // NBTService.getGenericItemNBTKey creates the full "mmoc_item_param_teleport_range"
            NamespacedKey dynamicKey = NBTService.getGenericItemNBTKey(keyString); // Uses uppercase key from template map
            if (value instanceof Integer val) NBTService.set(pdc, dynamicKey, PersistentDataType.INTEGER, val);
            else if (value instanceof Double val) NBTService.set(pdc, dynamicKey, PersistentDataType.DOUBLE, val);
            else if (value instanceof String val) NBTService.set(pdc, dynamicKey, PersistentDataType.STRING, val);
            else if (value instanceof Boolean val) NBTService.set(pdc, dynamicKey, PersistentDataType.BYTE, (byte)(val ? 1:0));
            else if (value instanceof Long val) NBTService.set(pdc, dynamicKey, PersistentDataType.LONG, val);
            else if (value instanceof Float val) NBTService.set(pdc, dynamicKey, PersistentDataType.FLOAT, val);
            else logging.warn("Unsupported NBT value type for generic key '" + keyString + "' in item '" + template.id() + "': " + value.getClass().getSimpleName());
        });

        // Specific NBT for known utility items like Compactors or Treecapitator Axes
        if (template.id().startsWith("personal_compactor_")) {
            NBTService.set(pdc, NBTService.COMPACTOR_UTILITY_ID_KEY, PersistentDataType.STRING, template.id());
            // Slot count for compactor is now expected to be in genericCustomNbt map parsed from items.yml
            int slotCount = template.getGenericNbtValue(NBTService.COMPACTOR_SLOT_COUNT_KEY.getKey().replace("mmoc_","").toUpperCase(), 1, Integer.class);
            NBTService.set(pdc, NBTService.COMPACTOR_SLOT_COUNT_KEY, PersistentDataType.INTEGER, slotCount);
        }
        if (template.genericCustomNbt().containsKey(NBTService.TOOL_ABILITY_ID.getKey().replace("mmoc_","").toUpperCase())) {
            String toolAbilityId = template.getGenericNbtValue(NBTService.TOOL_ABILITY_ID.getKey().replace("mmoc_","").toUpperCase(), "", String.class);
            if (!toolAbilityId.isBlank()) NBTService.set(pdc, NBTService.TOOL_ABILITY_ID, PersistentDataType.STRING, toolAbilityId);
        }
        if (template.genericCustomNbt().containsKey(NBTService.TOOL_ABILITY_COOLDOWN_TICKS.getKey().replace("mmoc_","").toUpperCase())) {
            Integer toolAbilityCD = template.getGenericNbtValue(NBTService.TOOL_ABILITY_COOLDOWN_TICKS.getKey().replace("mmoc_","").toUpperCase(), null, Integer.class);
            if (toolAbilityCD != null) NBTService.set(pdc, NBTService.TOOL_ABILITY_COOLDOWN_TICKS, PersistentDataType.INTEGER, toolAbilityCD);
        }

        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private void addStatToLore(List<Component> lore, String label, int value, TextColor valueColor) {
        addStatToLore(lore, label, value, valueColor, "");
    }

    private void addStatToLore(List<Component> lore, String label, int value, TextColor valueColor, String suffix) {
        if (value == 0) {
            // Only show speed if it's explicitly 0% and not just default 0 for other stats
            if (label.equalsIgnoreCase("Speed") && suffix.equals("%")) {
                // Show "Speed: 0%"
            } else {
                return; // Don't show most zero stats
            }
        }
        String sign = value > 0 ? "+" : ""; // Negative values will have their own sign
        lore.add(Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(sign + value + suffix, valueColor))
                .decoration(TextDecoration.ITALIC, false)); // Ensure no italics
    }
}