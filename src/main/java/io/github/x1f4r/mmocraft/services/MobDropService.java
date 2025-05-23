package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import io.github.x1f4r.mmocraft.entities.CustomMobType;
import io.github.x1f4r.mmocraft.loot.LootEntry;
import io.github.x1f4r.mmocraft.loot.LootTable;
import io.github.x1f4r.mmocraft.entities.listeners.MobDropListener;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MobDropService implements Service {
    private MMOCore core;
    private LoggingService logging;
    private ConfigService configService;
    private ItemService itemService;
    // NBTService static keys
    private CustomMobService customMobService; // To get CustomMobType for its lootTableId

    private final Map<String, LootTable> lootTableRegistry = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public MobDropService(MMOCore core) {
        this.core = core;
    }

    @Override
    public void initialize(MMOCore core) {
        this.logging = core.getService(LoggingService.class);
        this.configService = core.getService(ConfigService.class);
        this.itemService = core.getService(ItemService.class);
        this.customMobService = core.getService(CustomMobService.class); // CustomMobService should be ready

        FileConfiguration lootConfig = configService.getConfig(ConfigService.LOOT_TABLES_CONFIG_FILENAME);
        if (lootConfig.getKeys(false).isEmpty() && lootConfig.getConfigurationSection("loot_tables") == null) {
            logging.warn("'" + ConfigService.LOOT_TABLES_CONFIG_FILENAME + "' is empty or missing 'loot_tables' section. No loot tables loaded.");
        } else {
            loadLootTablesFromConfig(lootConfig);
        }
        configService.subscribeToReload(ConfigService.LOOT_TABLES_CONFIG_FILENAME, this::loadLootTablesFromConfig);

        core.registerListener(new MobDropListener(this));
        logging.info(getServiceName() + " initialized. Loaded " + lootTableRegistry.size() + " loot tables.");
    }

    @Override
    public void shutdown() {
        lootTableRegistry.clear();
        logging.info(getServiceName() + " shutdown. Loot tables cleared.");
    }

    private void loadLootTablesFromConfig(FileConfiguration config) {
        lootTableRegistry.clear();
        ConfigurationSection tablesRootSection = config.getConfigurationSection("loot_tables");
        if (tablesRootSection == null) {
            logging.info("No 'loot_tables' section found in " + ConfigService.LOOT_TABLES_CONFIG_FILENAME + ". No loot tables loaded.");
            return;
        }

        for (String tableId : tablesRootSection.getKeys(false)) {
            ConfigurationSection tableConfig = tablesRootSection.getConfigurationSection(tableId);
            if (tableConfig == null) continue;

            List<LootEntry> entries = new ArrayList<>();
            ConfigurationSection entriesConfig = tableConfig.getConfigurationSection("entries");
            if (entriesConfig != null) {
                for (String entryKey : entriesConfig.getKeys(false)) {
                    ConfigurationSection entryConf = entriesConfig.getConfigurationSection(entryKey);
                    if (entryConf == null) continue;
                    try {
                        String itemId = entryConf.getString("item_id");
                        if (itemId == null || itemId.isBlank()) {
                            logging.warn("Loot entry '" + entryKey + "' in table '" + tableId + "' is missing 'item_id'. Skipping entry.");
                            continue;
                        }
                        entries.add(new LootEntry(
                                itemId,
                                entryConf.getInt("min_amount", 1),
                                entryConf.getInt("max_amount", 1),
                                entryConf.getDouble("drop_chance", 1.0), // Default 100% chance IF this entry is chosen by a roll
                                entryConf.getInt("weight", 1),
                                entryConf.getString("required_permission"),
                                entryConf.getString("loot_condition_group_id")
                        ));
                    } catch (Exception e) {
                        logging.warn("Error parsing loot entry '" + entryKey + "' in table '" + tableId + "': " + e.getMessage());
                    }
                }
            }
            LootTable table = new LootTable(
                    tableId.toLowerCase(), entries, // Standardize table ID to lowercase
                    tableConfig.getInt("min_guaranteed_rolls", 0), // Default to 0 if not specified
                    tableConfig.getInt("max_guaranteed_rolls", Math.max(0, tableConfig.getInt("min_guaranteed_rolls", 0))), // Ensure max >= min
                    tableConfig.getInt("min_bonus_rolls", 0),
                    tableConfig.getInt("max_bonus_rolls", Math.max(0, tableConfig.getInt("min_bonus_rolls", 0))),
                    tableConfig.getDouble("chance_for_any_bonus_rolls", 0.0)
            );
            lootTableRegistry.put(table.lootTableId(), table);
        }
        logging.info("Reloaded " + lootTableRegistry.size() + " loot tables.");
    }

    public boolean hasLootTableFor(@NotNull LivingEntity entity) {
        String lootTableId = determineLootTableId(entity);
        return lootTableRegistry.containsKey(lootTableId);
    }

    @NotNull
    private String determineLootTableId(@NotNull LivingEntity entity) {
        String customMobId = NBTService.get(entity.getPersistentDataContainer(), NBTService.CUSTOM_MOB_TYPE_ID_KEY, PersistentDataType.STRING, null);
        if (customMobId != null) {
            CustomMobType mobType = customMobService.getCustomMobType(customMobId);
            if (mobType != null && mobType.customLootTableId() != null && !mobType.customLootTableId().isBlank()) {
                return mobType.customLootTableId().toLowerCase();
            }
        }
        // Fallback to EntityType name if no specific custom loot table ID defined
        return entity.getType().name().toLowerCase();
    }

    @NotNull
    public List<ItemStack> generateLoot(@NotNull LivingEntity killedEntity, @Nullable Player killer) {
        List<ItemStack> generatedDrops = new ArrayList<>();
        String lootTableId = determineLootTableId(killedEntity);

        LootTable table = lootTableRegistry.get(lootTableId);
        if (table == null) {
            if (logging.isDebugMode()) logging.debug("No custom loot table found for ID: '" + lootTableId + "' (Entity: " + killedEntity.getType().name() + "). No custom drops generated.");
            return generatedDrops; // Empty list; vanilla drops will occur if MobDropListener doesn't clear them.
        }

        if (logging.isDebugMode()) logging.debug("Generating loot for " + killedEntity.getType().name() + " using loot table: " + table.lootTableId());

        // 1. Guaranteed Rolls
        int numGuaranteedRolls = table.minGuaranteedRolls() +
                (table.maxGuaranteedRolls() > table.minGuaranteedRolls() ?
                        random.nextInt(table.maxGuaranteedRolls() - table.minGuaranteedRolls() + 1) : 0);
        for (int i = 0; i < numGuaranteedRolls; i++) {
            processLootRoll(generatedDrops, table.entries(), killer, killedEntity);
        }

        // 2. Bonus Rolls
        if (table.maxBonusRolls() > 0 && random.nextDouble() < table.chanceForAnyBonusRolls()) {
            int numBonusRolls = table.minBonusRolls() +
                    (table.maxBonusRolls() > table.minBonusRolls() ?
                            random.nextInt(table.maxBonusRolls() - table.minBonusRolls() + 1) : 0);
            for (int i = 0; i < numBonusRolls; i++) {
                processLootRoll(generatedDrops, table.entries(), killer, killedEntity);
            }
        }

        if (logging.isDebugMode() && !generatedDrops.isEmpty()) {
            logging.debug("Generated " + generatedDrops.size() + " item stacks from table " + table.lootTableId() + ": " +
                    generatedDrops.stream().map(is -> is.getType() + "x" + is.getAmount()).collect(Collectors.joining(", ")));
        }
        return generatedDrops;
    }

    private void processLootRoll(List<ItemStack> currentDrops, @NotNull List<LootEntry> allPossibleEntries,
                                 @Nullable Player killer, @NotNull LivingEntity killedEntity) {
        if (allPossibleEntries.isEmpty()) return;

        // Step 1: Filter entries by their individual dropChance and other conditions (permission, custom conditions)
        List<LootEntry> eligibleEntriesForThisRoll = new ArrayList<>();
        for (LootEntry entry : allPossibleEntries) {
            if (random.nextDouble() < entry.dropChance()) { // Check individual drop chance
                // Check permission
                if (killer != null && entry.requiredPermission() != null && !entry.requiredPermission().isBlank()) {
                    if (!killer.hasPermission(entry.requiredPermission())) {
                        continue; // Killer lacks permission for this specific entry
                    }
                }
                // Future: Check LootConditionGroupId using a LootConditionService
                // if (entry.lootConditionGroupId() != null) {
                //    if (!core.getService(LootConditionService.class).checkConditions(entry.lootConditionGroupId(), killer, killedEntity)) continue;
                // }
                eligibleEntriesForThisRoll.add(entry);
            }
        }

        if (eligibleEntriesForThisRoll.isEmpty()) return; // No entry passed its chance for this roll

        // Step 2: From the eligible entries, pick one based on weight
        int totalWeight = eligibleEntriesForThisRoll.stream().mapToInt(LootEntry::weight).sum();
        if (totalWeight <= 0) { // Should only happen if all eligible entries have weight 0
            if (!eligibleEntriesForThisRoll.isEmpty()) { // If still some entries (e.g. all weight 0), pick one uniformly
                addItemStackFromEntry(currentDrops, eligibleEntriesForThisRoll.get(random.nextInt(eligibleEntriesForThisRoll.size())));
            }
            return;
        }

        int randomNumber = random.nextInt(totalWeight);
        int cumulativeWeight = 0;
        for (LootEntry entry : eligibleEntriesForThisRoll) {
            cumulativeWeight += entry.weight();
            if (randomNumber < cumulativeWeight) {
                addItemStackFromEntry(currentDrops, entry);
                return; // One item successfully chosen and added for this roll
            }
        }
        // Fallback if something went wrong with weighted selection (should be rare with positive totalWeight)
        if (!eligibleEntriesForThisRoll.isEmpty()) {
            logging.warn("Weighted loot roll fallback triggered for table. This indicates an issue with weight calculation or random number generation if totalWeight > 0.");
            addItemStackFromEntry(currentDrops, eligibleEntriesForThisRoll.get(eligibleEntriesForThisRoll.size() - 1));
        }
    }

    private void addItemStackFromEntry(List<ItemStack> currentDrops, @NotNull LootEntry entry) {
        int amount = entry.minAmount() +
                (entry.maxAmount() > entry.minAmount() ?
                        random.nextInt(entry.maxAmount() - entry.minAmount() + 1) : 0);
        if (amount <= 0) return; // Don't add if amount is zero

        ItemStack itemStack;
        String itemId = entry.itemId();
        if (itemId.toUpperCase().startsWith("VANILLA:")) {
            try {
                Material mat = Material.matchMaterial(itemId.substring("VANILLA:".length()));
                if (mat != null) {
                    itemStack = new ItemStack(mat, amount);
                } else {
                    logging.warn("Invalid VANILLA material ID in loot entry: '" + itemId + "' from table. Skipping item.");
                    return;
                }
            } catch (Exception e) {
                logging.warn("Error parsing VANILLA material ID '" + itemId + "' in loot entry. Error: " + e.getMessage() + ". Skipping item.");
                return;
            }
        } else {
            itemStack = itemService.createItemStack(itemId, amount); // ItemService handles error item (BARRIER)
            if (itemStack.getType() == Material.BARRIER) {
                logging.warn("Failed to create custom item stack for loot entry (ID: '" + itemId + "'). ItemService returned BARRIER. Skipping item.");
                return;
            }
        }
        currentDrops.add(itemStack);
        if (logging.isDebugMode()) logging.debug("Added to drops: " + itemStack.getType() + "x" + itemStack.getAmount() + " from entry for " + itemId);
    }
}