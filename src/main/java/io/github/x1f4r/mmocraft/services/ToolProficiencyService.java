package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import io.github.x1f4r.mmocraft.items.CustomItem; // For checking tool properties if needed
import io.github.x1f4r.mmocraft.player.stats.CalculatedPlayerStats;
import io.github.x1f4r.mmocraft.tools.MiningProgressRunnable;
import io.github.x1f4r.mmocraft.tools.MiningTaskData;
import io.github.x1f4r.mmocraft.tools.listeners.ToolInteractionListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ToolProficiencyService implements Service {
    private MMOCore core;
    private LoggingService logging;
    private PlayerStatsService playerStatsService;
    private ItemService itemService;
    // NBTService used via static NBTService.KEY
    private ConfigService configService;
    private MMOCraft plugin;

    private final Map<UUID, MiningTaskData> activeMiningTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> treecapitatorAbilityCooldowns = new ConcurrentHashMap<>();

    private static final String TREECAPITATOR_NBT_ABILITY_ID_VALUE = "treecapitator";
    private int treecapitatorMaxLogs;
    private long treecapitatorBaseCooldownTicks;

    private double baseHardnessToTicksMultiplier;
    private Map<String, Double> toolTierMultipliers = new HashMap<>(); // String key like "WOOD", "STONE"
    private double efficiencyBonusPerLevel;
    private double hasteStrengthPerLevel;
    private double notOnGroundPenaltyMultiplier;
    private double aquaAffinityInWaterMultiplier;
    private final Random random = new Random();


    public ToolProficiencyService(MMOCore core) {
        this.core = core;
    }

    @Override
    public void initialize(MMOCore core) {
        this.plugin = core.getPlugin();
        this.logging = core.getService(LoggingService.class);
        this.playerStatsService = core.getService(PlayerStatsService.class);
        this.itemService = core.getService(ItemService.class);
        this.configService = core.getService(ConfigService.class);

        loadConfigSettings();
        configService.subscribeToReload(ConfigService.MAIN_CONFIG_FILENAME, reloadedConfig -> {
            logging.info("Reloading ToolProficiencyService configuration settings...");
            loadConfigSettings();
        });

        core.registerListener(new ToolInteractionListener(this));
        logging.info(getServiceName() + " initialized.");
    }

    private void loadConfigSettings() {
        ConfigurationSection toolSection = configService.getMainConfigSection("tools");
        if (toolSection == null) {
            logging.warn("'tools' section missing in config.yml. Using hardcoded defaults for ToolProficiencyService.");
            setHardcodedToolDefaults();
            return;
        }

        treecapitatorMaxLogs = toolSection.getInt("treecapitator.max_logs", 150);
        treecapitatorBaseCooldownTicks = toolSection.getLong("treecapitator.base_cooldown_ticks", 40L);

        ConfigurationSection breakSpeedSection = toolSection.getConfigurationSection("block_breaking");
        if (breakSpeedSection != null) {
            baseHardnessToTicksMultiplier = breakSpeedSection.getDouble("base_hardness_to_ticks_multiplier", 20.0);
            efficiencyBonusPerLevel = breakSpeedSection.getDouble("efficiency_bonus_per_level", 0.30);
            hasteStrengthPerLevel = breakSpeedSection.getDouble("haste_effect_strength_per_level", 0.20);
            notOnGroundPenaltyMultiplier = breakSpeedSection.getDouble("not_on_ground_penalty_multiplier", 5.0);
            aquaAffinityInWaterMultiplier = breakSpeedSection.getDouble("aqua_affinity_in_water_multiplier", 5.0);

            ConfigurationSection tierMultipliersConf = breakSpeedSection.getConfigurationSection("tool_tier_multipliers");
            toolTierMultipliers.clear();
            if (tierMultipliersConf != null) {
                for (String key : tierMultipliersConf.getKeys(false)) {
                    toolTierMultipliers.put(key.toUpperCase(), tierMultipliersConf.getDouble(key));
                }
            }
            if (toolTierMultipliers.isEmpty()) loadDefaultToolTierMultipliers();
        } else {
            logging.warn("'tools.block_breaking' section missing. Using hardcoded defaults.");
            setHardcodedBlockBreakingDefaults();
        }
    }

    private void setHardcodedToolDefaults(){
        treecapitatorMaxLogs = 150;
        treecapitatorBaseCooldownTicks = 40L;
        setHardcodedBlockBreakingDefaults();
    }
    private void setHardcodedBlockBreakingDefaults() {
        baseHardnessToTicksMultiplier = 20.0;
        efficiencyBonusPerLevel = 0.30;
        hasteStrengthPerLevel = 0.20;
        notOnGroundPenaltyMultiplier = 5.0;
        aquaAffinityInWaterMultiplier = 5.0;
        loadDefaultToolTierMultipliers();
    }

    private void loadDefaultToolTierMultipliers() {
        toolTierMultipliers.clear();
        toolTierMultipliers.put("HAND", 1.0);
        toolTierMultipliers.put("WOOD", 2.0);
        toolTierMultipliers.put("STONE", 4.0);
        toolTierMultipliers.put("IRON", 6.0);
        toolTierMultipliers.put("DIAMOND", 8.0);
        toolTierMultipliers.put("NETHERITE", 9.0);
        toolTierMultipliers.put("GOLD", 12.0);
    }


    @Override
    public void shutdown() {
        activeMiningTasks.values().forEach(taskData -> {
            if (taskData.bukkitTaskInstance != null && !taskData.bukkitTaskInstance.isCancelled()) {
                taskData.bukkitTaskInstance.cancel();
            }
        });
        activeMiningTasks.clear();
        treecapitatorAbilityCooldowns.clear();
        logging.info(getServiceName() + " shutdown complete.");
    }

    // --- Block Breaking Logic ---
    public void handleBlockLeftClick(Player player, Block block, ItemStack toolInHand) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR ||
                isUnbreakableByPlayers(block.getType())) {
            clearMiningTask(player.getUniqueId(), true);
            return;
        }

        UUID playerId = player.getUniqueId();
        Location blockLoc = block.getLocation().toBlockLocation();
        MiningTaskData currentTask = activeMiningTasks.get(playerId);

        if (currentTask != null && !currentTask.blockLocation.equals(blockLoc)) {
            clearMiningTask(playerId, true);
            currentTask = null;
        }

        ItemStack toolSnapshot = (toolInHand != null && !toolInHand.getType().isAir()) ? toolInHand.clone() : new ItemStack(Material.AIR);

        if (currentTask == null) {
            BukkitTask bukkitTask = new MiningProgressRunnable(this, player).runTaskTimer(plugin, 0L, 1L);
            MiningTaskData newTaskData = new MiningTaskData(blockLoc, block.getType(), toolSnapshot, bukkitTask);
            activeMiningTasks.put(playerId, newTaskData);
            if (logging.isDebugMode()) logging.debug("Started new mining task for " + player.getName() + " on " + block.getType());
        } else {
            currentTask.toolUsedSnapshot.setType(toolSnapshot.getType());
            currentTask.toolUsedSnapshot.setItemMeta(toolSnapshot.hasItemMeta() ? toolSnapshot.getItemMeta().clone() : null);
            currentTask.lastProgressApplicationTime = System.currentTimeMillis();
            if (logging.isDebugMode()) logging.debug("Player " + player.getName() + " re-interacted with mining target " + block.getType());
        }
    }

    public void interceptBlockDamage(Player player, Block block, ItemStack toolInHand, BlockDamageEvent event) {
        MiningTaskData taskData = activeMiningTasks.get(player.getUniqueId());
        if (taskData != null && taskData.blockLocation.equals(block.getLocation().toBlockLocation())) {
            event.setCancelled(true);
            if (event.getInstaBreak()) {
                CalculatedPlayerStats stats = playerStatsService.getCalculatedStats(player);
                float ticksToBreak = calculateTicksToBreak(player, block, taskData.toolUsedSnapshot, stats);
                if (ticksToBreak <= 1.0f) {
                    if (logging.isDebugMode()) logging.debug("MMOCraft Insta-break confirmed for " + player.getName() + " on " + block.getType());
                    breakBlockAndCleanup(player, block, taskData.toolUsedSnapshot, true);
                    clearMiningTask(player.getUniqueId(), false);
                }
            }
        }
    }

    private boolean isUnbreakableByPlayers(Material material) {
        return material == Material.BEDROCK || material == Material.BARRIER ||
                material == Material.COMMAND_BLOCK || material == Material.CHAIN_COMMAND_BLOCK ||
                material == Material.REPEATING_COMMAND_BLOCK || material == Material.STRUCTURE_BLOCK ||
                material == Material.JIGSAW || material == Material.END_PORTAL_FRAME ||
                material == Material.END_PORTAL || Material.END_GATEWAY == material ||
                material == Material.LIGHT || material == Material.REINFORCED_DEEPSLATE;
    }

    public void processMiningProgressTick(UUID playerId, Player player) {
        MiningTaskData taskData = activeMiningTasks.get(playerId);
        if (taskData == null || taskData.bukkitTaskInstance.isCancelled()) {
            activeMiningTasks.remove(playerId);
            return;
        }

        Block block = taskData.blockLocation.getBlock();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR ||
                block.getType() != taskData.originalBlockType ||
                player.getLocation().distanceSquared(taskData.blockLocation.clone().add(0.5, 0.5, 0.5)) > 49 ||
                !isLookingAt(player, taskData.blockLocation, 6.0)) {
            clearMiningTask(playerId, true);
            return;
        }

        CalculatedPlayerStats stats = playerStatsService.getCalculatedStats(player);
        if (getEffectiveToolSpeedStat(taskData.toolUsedSnapshot, taskData.originalBlockType, stats) == -Integer.MAX_VALUE) {
            if (logging.isDebugMode()) logging.debug("Tool ("+taskData.toolUsedSnapshot.getType()+") ineffective for " + taskData.originalBlockType + ". Stopping mine for " + player.getName());
            clearMiningTask(playerId, true);
            return;
        }

        float ticksToBreak = calculateTicksToBreak(player, block, taskData.toolUsedSnapshot, stats);
        if (ticksToBreak <= 0.05f) {
            breakBlockAndCleanup(player, block, taskData.toolUsedSnapshot, true);
            clearMiningTask(playerId, false);
            return;
        }

        taskData.accumulatedProgress += (1.0f / ticksToBreak);

        if (taskData.accumulatedProgress >= 1.0f) {
            breakBlockAndCleanup(player, block, taskData.toolUsedSnapshot, false);
            clearMiningTask(playerId, false);
        } else {
            player.sendBlockDamage(taskData.blockLocation, taskData.accumulatedProgress, player.getEntityId()); // Use player's entity ID for animation source
        }
    }

    private boolean isLookingAt(Player player, Location blockLocation, double maxDistance) {
        RayTraceResult result = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), maxDistance, FluidCollisionMode.NEVER, true);
        return result != null && result.getHitBlock() != null && result.getHitBlock().getLocation().equals(blockLocation);
    }

    private float getVanillaBlockHardness(Material material) {
        // Simplified - A comprehensive map from Minecraft Wiki would be better
        if (Tag.LOGS.isTagged(material) || Tag.PLANKS.isTagged(material)) return 2.0f;
        if (Tag.STONE_BRICKS.isTagged(material) || material == Material.STONE || material == Material.COBBLESTONE || material == Material.ANDESITE || material == Material.DIORITE || material == Material.GRANITE) return 1.5f;
        if (Tag.DIRT.isTagged(material) || material == Material.GRASS_BLOCK || Tag.SAND.isTagged(material) || material == Material.GRAVEL) return 0.5f;
        if (material == Material.OBSIDIAN) return 50.0f;
        if (Tag.IRON_ORES.isTagged(material) || Tag.GOLD_ORES.isTagged(material) || Tag.DIAMOND_ORES.isTagged(material) || Tag.EMERALD_ORES.isTagged(material) || Tag.LAPIS_ORES.isTagged(material) || Tag.REDSTONE_ORES.isTagged(material) || Tag.COPPER_ORES.isTagged(material)) return 3.0f;
        if (Tag.DEEPSLATE_ORE_REPLACEABLES.isTagged(material) && material.toString().contains("DEEPSLATE")) return 4.5f; // Deepslate ores are harder
        return material.getHardness(); // Bukkit API, may not be perfectly vanilla aligned for all blocks
    }

    private double getToolTierStrengthMultiplier(ItemStack tool) {
        if (tool == null || tool.getType().isAir()) return toolTierMultipliers.getOrDefault("HAND", 1.0);
        String typeName = tool.getType().name().toUpperCase();
        if (typeName.startsWith("WOODEN_")) return toolTierMultipliers.getOrDefault("WOOD", 2.0);
        if (typeName.startsWith("STONE_")) return toolTierMultipliers.getOrDefault("STONE", 4.0);
        if (typeName.startsWith("IRON_")) return toolTierMultipliers.getOrDefault("IRON", 6.0);
        if (typeName.startsWith("DIAMOND_")) return toolTierMultipliers.getOrDefault("DIAMOND", 8.0);
        if (typeName.startsWith("NETHERITE_")) return toolTierMultipliers.getOrDefault("NETHERITE", 9.0);
        if (typeName.startsWith("GOLDEN_")) return toolTierMultipliers.getOrDefault("GOLD", 12.0);
        // Add custom tool tiers from config here if they exist
        return 1.0; // Default for unknown tools
    }

    /** Returns relevant player stat (mining/foraging) or -Integer.MAX_VALUE if tool is ineffective. */
    private int getEffectiveToolSpeedStat(ItemStack tool, Material blockType, CalculatedPlayerStats stats) {
        if (tool == null) return -Integer.MAX_VALUE;
        String toolName = tool.getType().name().toUpperCase();
        boolean isPickaxe = toolName.endsWith("_PICKAXE");
        boolean isAxe = toolName.endsWith("_AXE");
        boolean isShovel = toolName.endsWith("_SHOVEL");
        boolean isHoe = toolName.endsWith("_HOE"); // Foraging for leaves/crops?

        if (isPickaxe && (Tag.MINEABLE_PICKAXE.isTagged(blockType))) return stats.miningSpeed();
        if (isAxe && (Tag.MINEABLE_AXE.isTagged(blockType))) return stats.foragingSpeed(); // Axes typically for wood-like
        if (isShovel && (Tag.MINEABLE_SHOVEL.isTagged(blockType))) return stats.miningSpeed(); // Shovels for dirt-like
        // Hoes for leaves, crops, etc. (Tag.MINEABLE_HOE)
        if (isHoe && (Tag.LEAVES.isTagged(blockType) || Tag.CROPS.isTagged(blockType) || Tag.WART_BLOCKS.isTagged(blockType) || materialIsForageableByHoe(blockType) )) return stats.foragingSpeed();

        // If no specific tool matches but block is breakable by hand (or any tool, slowly)
        // we might return a base stat or 0, but for "effective tool speed stat", -MAX_VALUE signifies wrong tool.
        return -Integer.MAX_VALUE;
    }

    private boolean materialIsForageableByHoe(Material mat) {
        String name = mat.name();
        return name.endsWith("_LEAVES") || name.equals("VINE") || name.equals("NETHER_WART_BLOCK") || name.equals("WARPED_WART_BLOCK") || name.equals("HAY_BLOCK") || name.equals("MOSS_BLOCK") || name.equals("SCULK_VEIN") || name.equals("SCULK_CATALYST") || name.equals("SCULK_SHRIEKER") || name.equals("SCULK_SENSOR") || name.equals("TARGET") || name.equals("DRIED_KELP_BLOCK") || name.equals("SPONGE") || name.equals("WET_SPONGE") || name.equals("SHROOMLIGHT");
    }


    private float calculateTicksToBreak(Player player, Block block, ItemStack tool, CalculatedPlayerStats stats) {
        Material blockMat = block.getType();
        float blockHardness = getVanillaBlockHardness(blockMat);
        if (blockHardness <= 0.001f) return 0.05f; // Effectively instamine

        double toolBaseSpeed = getToolTierStrengthMultiplier(tool);
        boolean canHarvestProperly = tool != null && !tool.getType().isAir() && tool.getType().isCorrectToolForDrops(block.getBlockData());

        float breakingPower;
        if (canHarvestProperly) {
            breakingPower = (float) (toolBaseSpeed / blockHardness / baseHardnessToTicksMultiplier);
        } else {
            // Penalty for wrong tool (but still breakable by hand)
            breakingPower = (float) (1.0 / blockHardness / (baseHardnessToTicksMultiplier * 3.33)); // Hand is ~1/3rd effective on stone than pick.
        }
        if (breakingPower <= 0) return Float.MAX_VALUE; // Cannot break

        // Apply Player's Tool Speed Stat
        int relevantPlayerToolSpeedStat = getEffectiveToolSpeedStat(tool, blockMat, stats);
        if(relevantPlayerToolSpeedStat == -Integer.MAX_VALUE && canHarvestProperly) relevantPlayerToolSpeedStat = 0; // If correct tool but no specific stat, assume 0 bonus.
        else if (relevantPlayerToolSpeedStat == -Integer.MAX_VALUE && !canHarvestProperly) { /* Keep it as ineffective if tool type is wrong */ }

        float playerStatMultiplier = 1.0f;
        if (relevantPlayerToolSpeedStat != -Integer.MAX_VALUE) {
            playerStatMultiplier = 1.0f + (relevantPlayerToolSpeedStat / 100.0f);
        }


        // Apply Enchantments (Efficiency)
        float efficiencyMultiplier = 1.0f;
        if (tool != null && tool.containsEnchantment(Enchantment.EFFICIENCY)) {
            int efficiencyLevel = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
            // Vanilla efficiency adds level*level + 1 to "damage" dealt to block.
            // For speed, it's more like a direct multiplier.
            efficiencyMultiplier += (efficiencyLevel * efficiencyBonusPerLevel);
        }

        // Apply Potion Effects (Haste/Mining Fatigue)
        float potionMultiplier = 1.0f;
        if (player.hasPotionEffect(PotionEffectType.HASTE)) {
            int hasteLevel = Objects.requireNonNull(player.getPotionEffect(PotionEffectType.HASTE)).getAmplifier() + 1;
            potionMultiplier += (hasteLevel * hasteStrengthPerLevel);
        }
        if (player.hasPotionEffect(PotionEffectType.MINING_FATIGUE)) {
            int fatigueLevel = Objects.requireNonNull(player.getPotionEffect(PotionEffectType.MINING_FATIGUE)).getAmplifier() + 1;
            potionMultiplier *= Math.pow(0.3, fatigueLevel); // Vanilla fatigue formula
        }

        // Apply Environmental Modifiers (Ground, Water, Aqua Affinity)
        float environmentMultiplier = 1.0f;
        if (!player.isOnGround() && !player.isInWater()) { // In air penalty
            environmentMultiplier /= notOnGroundPenaltyMultiplier;
        }
        if (player.isInWater()) {
            boolean hasAquaAffinity = player.getInventory().getHelmet() != null &&
                    player.getInventory().getHelmet().containsEnchantment(Enchantment.AQUA_AFFINITY);
            environmentMultiplier /= (hasAquaAffinity ? 1.0f : notOnGroundPenaltyMultiplier); // Water penalty if no aqua, no penalty if aqua
            if (hasAquaAffinity) environmentMultiplier *= aquaAffinityInWaterMultiplier; // Aqua affinity bonus
        }

        // Final progress per game tick
        float progressPerTick = breakingPower * playerStatMultiplier * efficiencyMultiplier * potionMultiplier * environmentMultiplier;
        if (progressPerTick <= 0.00001f) return Float.MAX_VALUE; // Effectively unbreakable

        return Math.max(0.05f, 1.0f / progressPerTick); // Ticks to break; min 1 game tick (0.05s)
    }

    private void breakBlockAndCleanup(Player player, Block block, ItemStack toolUsed, boolean instaBreakContext) {
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        event.setInstaBreak(instaBreakContext); // Inform event if it was an instabreak
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            if (logging.isDebugMode()) logging.debug("BlockBreakEvent cancelled by another plugin for " + block.getType() + " at " + block.getLocation() + " by " + player.getName());
            // Send animation clear if player was mining it
            clearMiningTask(player.getUniqueId(), true);
            return;
        }

        // Use the tool that was snapshotted at the start of the mining task for consistency in drops/durability
        ItemStack effectiveTool = taskData.toolUsedSnapshot != null ? taskData.toolUsedSnapshot : toolUsed;

        if (player.getGameMode() != GameMode.CREATIVE) {
            // If the event provided exp, use it. Otherwise, vanilla breakNaturally handles it.
            if (event.isDropItems()) { // Check if event still allows drops
                block.breakNaturally(effectiveTool, true); // true to apply fortune/silk
            } else {
                block.setType(Material.AIR); // Don't drop items if event cancelled drops
            }
            if (event.getExpToDrop() > 0) { // Purpur/Paper can set custom exp
                block.getWorld().spawn(block.getLocation().add(0.5,0.5,0.5), org.bukkit.entity.ExperienceOrb.class, orb -> orb.setExperience(event.getExpToDrop()));
            }
            handleToolDurability(player, effectiveTool, 1); // 1 block broken
        } else {
            block.setType(Material.AIR);
        }

        // Treecapitator check is handled by ToolInteractionListener on BlockBreakEvent
        // to ensure it happens *after* the primary block is confirmed broken.
    }

    public void clearMiningTask(UUID playerId, boolean sendClearAnimationPacket) {
        MiningTaskData taskData = activeMiningTasks.remove(playerId);
        if (taskData != null) {
            if (taskData.bukkitTaskInstance != null && !taskData.bukkitTaskInstance.isCancelled()) {
                taskData.bukkitTaskInstance.cancel();
            }
            if (sendClearAnimationPacket) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    // Stage -1 or 10 typically clears animation. Paper's sendBlockDamage with high progress also works.
                    player.sendBlockDamage(taskData.blockLocation, 1.0f); // Sending full progress implies broken or cleared
                }
            }
            if (logging.isDebugMode()) logging.debug("Cleared mining task for player " + playerId + " on block " + taskData.originalBlockType);
        }
    }

    private void handleToolDurability(Player player, ItemStack tool, int blocksBroken) {
        if (player.getGameMode() == GameMode.CREATIVE || tool == null || tool.getType().isAir() || blocksBroken <= 0) return;
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable dMeta)) return;
        if (dMeta.isUnbreakable()) return; // Bukkit unbreakable

        CustomItem customTool = itemService.getCustomItemTemplateFromItemStack(tool);
        if (customTool != null && customTool.unbreakable()) return; // Our custom item definition unbreakable

        int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        int damageToApply = 0;
        for (int i = 0; i < blocksBroken; i++) {
            if (unbreakingLevel == 0 || random.nextDouble() < (1.0 / (unbreakingLevel + 1.0))) { // Chance to take damage
                damageToApply++;
            }
        }

        if (damageToApply > 0) {
            int currentDamage = dMeta.getDamage();
            int maxDurability = tool.getType().getMaxDurability();
            dMeta.setDamage(currentDamage + damageToApply);
            tool.setItemMeta(dMeta);

            if (dMeta.getDamage() >= maxDurability) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                // Find and remove the item
                if (tool.equals(player.getInventory().getItemInMainHand())) player.getInventory().setItemInMainHand(null);
                else if (tool.equals(player.getInventory().getItemInOffHand())) player.getInventory().setItemInOffHand(null);
                else player.getInventory().removeItem(tool); // Fallback removal
                player.updateInventory(); // Ensure client sees change
            }
        }
    }


    // --- Fishing Speed Logic ---
    public void applyFishingSpeed(Player player, FishHook hook) {
        CalculatedPlayerStats stats = playerStatsService.getCalculatedStats(player);
        int fishingSpeedStat = stats.fishingSpeed();
        if (fishingSpeedStat == 0 && hook.getMinWaitTime() == 100 && hook.getMaxWaitTime() == 600) return; // No change from vanilla defaults

        try {
            // Vanilla defaults: minWaitTime=100 (5s), maxWaitTime=600 (30s)
            // These are just initial values, actual wait time can be lower if luck affects it.
            int baseMinWait = 100;
            int baseMaxWait = 600;

            // Positive fishingSpeedStat reduces wait time.
            // Example formula: each point of fishing speed reduces wait times by 0.25%
            // Factor = 1.0 - (fishingSpeedStat * 0.0025)
            // Max reduction: e.g., 90% (factor 0.1 at 360 fishing speed)
            // Max increase: e.g., 200% (factor 3.0 at -800 fishing speed, effectively *very* slow)
            double factor = 1.0 - (fishingSpeedStat * 0.0025); // Adjust multiplier as needed
            factor = Math.max(0.1, Math.min(3.0, factor)); // Clamp factor

            int newMin = (int) Math.round(baseMinWait * factor);
            int newMax = (int) Math.round(baseMaxWait * factor);

            newMin = Math.max(20, newMin); // Min 1 second wait
            newMax = Math.max(newMin + 40, newMax); // Ensure max is reasonably larger than min

            hook.setWaitTimeRange(newMin, newMax); // Paper/Purpur API
            if (logging.isDebugMode()) logging.debug(String.format("Player %s - FishingSpeed: %d. Factor: %.3f. New Wait Range: %d-%d ticks.",
                    player.getName(), fishingSpeedStat, factor, newMin, newMax));
        } catch (NoSuchMethodError e) {
            logging.warnOnce("FishHookWaitTimeAPI", "Paper/Purpur API for FishHook#setWaitTimeRange not found. Fishing Speed stat will not function.");
        } catch (Exception e) {
            logging.severe("Error applying fishing speed for " + player.getName(), e);
        }
    }

    // --- Shooting Speed Logic (for DRAWN bows) ---
    public void applyShootingSpeedToDrawnProjectile(Player player, ItemStack bow, Arrow arrow) {
        if (bow == null || arrow == null) return;

        // Check if this bow is an "Instant Shot" type - if so, AbilityService would handle its velocity.
        // CustomItem customBow = itemService.getCustomItemTemplateFromItemStack(bow);
        // if (customBow != null && customBow.getGenericNbtValue(NBTService.YOUR_INSTANT_SHOT_FLAG_KEY_STRING, false, Boolean.class)) {
        //     return; // Handled by ability
        // }

        CalculatedPlayerStats stats = playerStatsService.getCalculatedStats(player);
        int shootingSpeedStat = stats.shootingSpeed();
        if (shootingSpeedStat == 0) return; // No custom speed adjustment

        Vector currentVelocity = arrow.getVelocity();
        // Shooting speed affects projectile velocity (magnitude).
        // Example formula: Each point of shooting speed increases/decreases velocity by 0.1%
        // Factor = 1.0 + (shootingSpeedStat * 0.001)
        double speedFactor = 1.0 + (shootingSpeedStat / 1000.0); // Adjust divisor for sensitivity
        speedFactor = Math.max(0.1, Math.min(3.0, speedFactor)); // Clamp factor (10% to 300% velocity)

        arrow.setVelocity(currentVelocity.multiply(speedFactor));
        if (logging.isDebugMode()) logging.debug("Applied shooting speed (" + shootingSpeedStat + " -> factor " + String.format("%.3f", speedFactor) + ") to drawn arrow from " + player.getName());

        // NBT tagging of the arrow (source item, damage multipliers, true damage)
        // is handled by the ToolInteractionListener.
    }

    // --- Treecapitator Logic ---
    public void attemptTreecapitator(Player player, Block initiallyBrokenLog, ItemStack axe) {
        if (player.getGameMode() == GameMode.CREATIVE) return; // Creative mode doesn't consume durability or use abilities typically
        if (axe == null || axe.getType().isAir() || !axe.hasItemMeta() || !Tag.LOGS.isTagged(initiallyBrokenLog.getType())) return;

        ItemMeta meta = axe.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String abilityIdNBT = NBTService.get(pdc, NBTService.TOOL_ABILITY_ID, PersistentDataType.STRING, "");
        if (!TREECAPITATOR_NBT_ABILITY_ID_VALUE.equalsIgnoreCase(abilityIdNBT)) return;

        UUID playerId = player.getUniqueId();
        long itemSpecificCooldownTicks = NBTService.get(pdc, NBTService.TOOL_ABILITY_COOLDOWN_TICKS, PersistentDataType.INTEGER, (int)treecapitatorBaseCooldownTicks);
        long cooldownEndTime = treecapitatorAbilityCooldowns.getOrDefault(playerId, 0L);

        if (System.currentTimeMillis() < cooldownEndTime) {
            long remainingMillis = cooldownEndTime - System.currentTimeMillis();
            player.sendActionBar(Component.text("Treecapitator on cooldown! (" + String.format("%.1f", remainingMillis / 1000.0) + "s)", NamedTextColor.RED));
            return;
        }

        Material logType = initiallyBrokenLog.getType(); // Type of the initially broken log
        Set<Block> logsToBreak = new HashSet<>();
        Queue<Block> toExplore = new LinkedList<>();
        Set<Location> visitedLocations = new HashSet<>(); // Avoid re-processing same block location

        toExplore.add(initiallyBrokenLog); // Start with the block that was just broken
        visitedLocations.add(initiallyBrokenLog.getLocation().toBlockLocation());
        // The initiallyBrokenLog itself is already handled by the BlockBreakEvent. We are finding *additional* logs.

        int additionalLogsFound = 0;
        while (!toExplore.isEmpty() && additionalLogsFound < treecapitatorMaxLogs) {
            Block currentLog = toExplore.poll();
            // Explore in a 3x3x3 cube around the current log
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue; // Skip self

                        Block neighbor = currentLog.getRelative(x, y, z);
                        Location neighborLoc = neighbor.getLocation().toBlockLocation();

                        if (neighbor.getType() == logType && !visitedLocations.contains(neighborLoc)) {
                            visitedLocations.add(neighborLoc);
                            toExplore.add(neighbor);
                            logsToBreak.add(neighbor); // Add to set of blocks to break by Treecapitator
                            additionalLogsFound++;
                            if (additionalLogsFound >= treecapitatorMaxLogs) break;
                        }
                    }
                    if (additionalLogsFound >= treecapitatorMaxLogs) break;
                }
                if (additionalLogsFound >= treecapitatorMaxLogs) break;
            }
        }

        if (!logsToBreak.isEmpty()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
            int actualExtraBrokenByAbility = 0;
            for (Block logToBreak : logsToBreak) {
                if (player.getGameMode() != GameMode.CREATIVE) {
                    BlockBreakEvent subEvent = new BlockBreakEvent(logToBreak, player);
                    // We don't need to set instabreak here, as breakNaturally respects tool
                    Bukkit.getPluginManager().callEvent(subEvent);
                    if (!subEvent.isCancelled()) {
                        if (subEvent.isDropItems()) {
                            logToBreak.breakNaturally(axe, true); // true: apply fortune/silk
                        } else {
                            logToBreak.setType(Material.AIR);
                        }
                        if(subEvent.getExpToDrop() > 0) {
                            logToBreak.getWorld().spawn(logToBreak.getLocation().add(0.5,0.5,0.5), org.bukkit.entity.ExperienceOrb.class, orb -> orb.setExperience(subEvent.getExpToDrop()));
                        }
                        actualExtraBrokenByAbility++;
                    }
                } else { // Creative mode
                    logToBreak.setType(Material.AIR);
                    actualExtraBrokenByAbility++;
                }
                logToBreak.getWorld().spawnParticle(Particle.BLOCK_DUST, logToBreak.getLocation().add(0.5,0.5,0.5), 10, 0.4,0.4,0.4, logToBreak.getBlockData());
            }

            if (actualExtraBrokenByAbility > 0) {
                // Apply durability for the ability use (e.g., 1 hit per N logs, or just 1 for the activation)
                // For simplicity, one durability hit for activating the ability successfully on extra logs.
                handleToolDurability(player, axe, 1);
                treecapitatorAbilityCooldowns.put(playerId, System.currentTimeMillis() + (itemSpecificCooldownTicks * 50L));
                player.sendMessage(Component.text("Treecapitator felled an additional " + actualExtraBrokenByAbility + " logs!", NamedTextColor.GREEN));
            }
        }
    }
}