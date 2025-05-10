package io.github.x1f4r.mmocraft.listeners; // General listeners package

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import io.github.x1f4r.mmocraft.stats.PlayerStats; // Correct import
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayerToolListener implements Listener {

    private final PlayerStatsManager statsManager;
    private final Logger log;
    private final MMOPlugin plugin;

    // Player UUID -> Location of the block they are currently targeting by our system
    private final Map<UUID, Location> playerTargetBlock = new ConcurrentHashMap<>();
    // Player UUID -> Accumulated progress (0.0f to 1.0f+) on their target block
    private final Map<UUID, Float> playerBlockProgress = new ConcurrentHashMap<>();
    // Map to store active mining tasks for players
    private final Map<UUID, BukkitTask> activeMiningTasks = new ConcurrentHashMap<>();

    // Cooldown map for Treecapitator ability (Player UUID -> Timestamp)
    private final Map<UUID, Long> treecapitatorCooldowns = new HashMap<>();
    private static final String TREECAPITATOR_ABILITY_ID = "area_chop_axe";
    private static final int TREECAPITATOR_MAX_BREAK_COUNT = 100; // Safety limit

    // Material sets for tool effectiveness
    private static final Set<Material> MINING_MATERIALS_PICKAXE = new HashSet<>(Arrays.asList(
            Material.STONE, Material.COBBLESTONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE,
            Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF, Material.CALCITE,
            Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE,
            Material.LAPIS_ORE, Material.REDSTONE_ORE, Material.COPPER_ORE,
            Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.DEEPSLATE_EMERALD_ORE, Material.DEEPSLATE_LAPIS_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.GILDED_BLACKSTONE,
            Material.ANCIENT_DEBRIS, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.NETHERRACK,
            Material.END_STONE, Material.SANDSTONE, Material.RED_SANDSTONE, Material.TERRACOTTA, // Added missing stone types
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.BASALT, Material.SMOOTH_BASALT, Material.BLACKSTONE,
            Material.AMETHYST_BLOCK, Material.BUDDING_AMETHYST,
            // Concrete, Terracotta variants, Rails, Dispensers, Furnaces etc. if desired
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER, Material.DISPENSER, Material.DROPPER, Material.OBSERVER,
            Material.IRON_BLOCK, Material.GOLD_BLOCK, Material.DIAMOND_BLOCK, Material.EMERALD_BLOCK, Material.LAPIS_BLOCK, Material.REDSTONE_BLOCK, Material.COAL_BLOCK, Material.NETHERITE_BLOCK, Material.COPPER_BLOCK, Material.RAW_IRON_BLOCK, Material.RAW_GOLD_BLOCK, Material.RAW_COPPER_BLOCK,
            Material.RAIL, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.ACTIVATOR_RAIL,
            Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL, Material.HOPPER, Material.IRON_BARS, Material.CHAIN, Material.IRON_DOOR, Material.IRON_TRAPDOOR,
            Material.BREWING_STAND, Material.CAULDRON, Material.BELL, Material.GRINDSTONE, Material.STONECUTTER, Material.ENCHANTING_TABLE, Material.END_PORTAL_FRAME
    ));

    private static final Set<Material> FORAGING_MATERIALS_AXE = new HashSet<>(Arrays.asList(
            // Logs & Wood
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG, Material.CRIMSON_STEM, Material.WARPED_STEM,
            Material.OAK_WOOD, Material.SPRUCE_WOOD, Material.BIRCH_WOOD, Material.JUNGLE_WOOD, Material.ACACIA_WOOD, Material.DARK_OAK_WOOD, Material.MANGROVE_WOOD, Material.CHERRY_WOOD, Material.CRIMSON_HYPHAE, Material.WARPED_HYPHAE,
            Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG, Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG, Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_WARPED_STEM,
            Material.STRIPPED_OAK_WOOD, Material.STRIPPED_SPRUCE_WOOD, Material.STRIPPED_BIRCH_WOOD, Material.STRIPPED_JUNGLE_WOOD, Material.STRIPPED_ACACIA_WOOD, Material.STRIPPED_DARK_OAK_WOOD, Material.STRIPPED_MANGROVE_WOOD, Material.STRIPPED_CHERRY_WOOD, Material.STRIPPED_CRIMSON_HYPHAE, Material.STRIPPED_WARPED_HYPHAE,
            // Other axe-breakable
            Material.PUMPKIN, Material.CARVED_PUMPKIN, Material.JACK_O_LANTERN, Material.MELON, Material.BOOKSHELF, Material.CRAFTING_TABLE, Material.CHEST, Material.TRAPPED_CHEST, Material.NOTE_BLOCK, Material.JUKEBOX,
            Material.MUSHROOM_STEM, Material.RED_MUSHROOM_BLOCK, Material.BROWN_MUSHROOM_BLOCK,
            Material.BAMBOO_BLOCK, Material.STRIPPED_BAMBOO_BLOCK, // Added Bamboo blocks
            Material.COCOA, Material.VINE, Material.MANGROVE_ROOTS, Material.WARPED_WART_BLOCK, Material.NETHER_WART_BLOCK,
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS, Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS, Material.MANGROVE_PLANKS, Material.CHERRY_PLANKS, Material.CRIMSON_PLANKS, Material.WARPED_PLANKS, Material.BAMBOO_PLANKS, // Planks for completeness
            Material.OAK_FENCE, Material.SPRUCE_FENCE, Material.BIRCH_FENCE, Material.JUNGLE_FENCE, Material.ACACIA_FENCE, Material.DARK_OAK_FENCE, Material.MANGROVE_FENCE, Material.CHERRY_FENCE, Material.CRIMSON_FENCE, Material.WARPED_FENCE, Material.BAMBOO_FENCE, // Fences
            Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE, Material.JUNGLE_FENCE_GATE, Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE, Material.MANGROVE_FENCE_GATE, Material.CHERRY_FENCE_GATE, Material.CRIMSON_FENCE_GATE, Material.WARPED_FENCE_GATE, Material.BAMBOO_FENCE_GATE // Fence Gates
    ));
     private static final Set<Material> FORAGING_MATERIALS_SHOVEL = new HashSet<>(Arrays.asList(
            Material.DIRT, Material.GRASS_BLOCK, Material.SAND, Material.RED_SAND, Material.GRAVEL,
            Material.CLAY, Material.SOUL_SAND, Material.SOUL_SOIL, Material.SNOW_BLOCK, Material.SNOW,
            Material.MYCELIUM, Material.PODZOL, Material.FARMLAND, Material.DIRT_PATH, Material.ROOTED_DIRT, Material.MUD
    ));

    public PlayerToolListener(MMOCore core) {
        this.statsManager = core.getPlayerStatsManager();
        this.plugin = core.getPlugin();
        this.log = MMOPlugin.getMMOLogger();
        log.info("PlayerToolListener Initialized (BlockDamageEvent + BukkitRunnable Method).");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || isUnbreakableVanillaBlock(clickedBlock.getType())) {
            return;
        }

        UUID playerUUID = player.getUniqueId();
        Location newTargetLocation = clickedBlock.getLocation();

        // If player switches target block, clear old state
        Location oldTargetLocation = playerTargetBlock.put(playerUUID, newTargetLocation);
        if (oldTargetLocation != null && !oldTargetLocation.equals(newTargetLocation)) {
            clearMiningState(player, oldTargetLocation, true, true);
        }
        
        // Reset progress for the new (or same) target block
        playerBlockProgress.put(playerUUID, 0.0f);
        log.log(Level.FINEST, "[PTL Inter] Player " + player.getName() + " targeted block " + newTargetLocation);

        // Creative mode handles its own breaking
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            clearMiningState(player, newTargetLocation, true, true); // Cleanup if they were mining then switched
            return;
        }
        // At this point, onPlayerInteract has just set up the target.
        // BlockDamageEvent will handle the actual start of custom breaking if applicable.
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        UUID playerUUID = player.getUniqueId();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return; // Let creative/spectator break blocks normally
        }
        
        Location currentTargetLocByInteract = playerTargetBlock.get(playerUUID);
        // Ensure this BlockDamageEvent is for the block the player just interacted with
        if (currentTargetLocByInteract == null || !currentTargetLocByInteract.equals(block.getLocation())) {
            // Player might be damaging a different block than the one last left-clicked
            // Or, no left-click was registered by onPlayerInteract for this block.
            // Allow vanilla behavior for this damage tick.
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null) tool = new ItemStack(Material.AIR);

        PlayerStats stats = statsManager.getStats(player);
        int speedStatValue = getApplicableToolSpeedStat(tool, block.getType(), stats);

        if (speedStatValue == -1) { // Not an effective tool for this block or no custom speed
            log.log(Level.FINEST, "[PTL Damage] Ineffective tool " + tool.getType() + " or no speed stat for " + block.getType() + " by " + player.getName() + ". Vanilla handles.");
            clearMiningState(player, block.getLocation(), true, true); // Ensure our state is clean
            return; // Let vanilla handle breaking
        }
        
        // If we reach here, it's a custom break scenario
        event.setCancelled(true); // IMPORTANT: Cancel vanilla damage accumulation for this block
        log.log(Level.INFO, "[PTL Damage] Player " + player.getName() + " custom damaging " + block.getType() + " with " + tool.getType() + ". SpeedStat: " + speedStatValue);

        // Stop any previous mining task for this player
        BukkitTask existingTask = activeMiningTasks.remove(playerUUID);
        if (existingTask != null) {
            existingTask.cancel();
        }

        // Reset progress and start new mining task
        playerBlockProgress.put(playerUUID, 0.0f);

        MiningTask newTask = new MiningTask(player, block, tool, speedStatValue);
        BukkitTask task = newTask.runTaskTimer(plugin, 0L, 1L); // Run every tick
        activeMiningTasks.put(playerUUID, task);
    }
    
    private int getApplicableToolSpeedStat(ItemStack tool, Material blockMaterial, PlayerStats stats) {
        String toolName = tool.getType().name().toUpperCase();
        if (toolName.endsWith("_PICKAXE") && MINING_MATERIALS_PICKAXE.contains(blockMaterial)) {
            return stats.getMiningSpeed();
        } else if (toolName.endsWith("_AXE") && FORAGING_MATERIALS_AXE.contains(blockMaterial)) {
            return stats.getForagingSpeed();
        } else if (toolName.endsWith("_SHOVEL") && FORAGING_MATERIALS_SHOVEL.contains(blockMaterial)) {
            return stats.getMiningSpeed(); // Shovels use Mining Speed
        }
        return -1; // Indicates not a custom tool/block combo or no speed stat
    }


    private class MiningTask extends BukkitRunnable {
        private final UUID playerUUID;
        private final Location blockLocation;
        private final Material originalBlockType;
        private final ItemStack toolUsed; // Store a clone or be careful with modification
        private final int speedStat;
        private final Player player; // Store direct reference for convenience

        public MiningTask(Player player, Block block, ItemStack tool, int speedStat) {
            this.playerUUID = player.getUniqueId();
            this.blockLocation = block.getLocation();
            this.originalBlockType = block.getType();
            this.toolUsed = tool.clone(); // Clone to avoid issues if player swaps item
            this.speedStat = speedStat;
            this.player = player;
        }

        @Override
        public void run() {
            if (!player.isOnline() || player.isDead()) {
                log.log(Level.FINEST, "[MiningTask] Player " + player.getName() + " logged off/died. Cancelling task.");
                clearMiningStateAndCancel(true);
                return;
            }

            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                log.log(Level.FINEST, "[MiningTask] Player " + player.getName() + " switched to creative/spectator. Cancelling task.");
                clearMiningStateAndCancel(true);
                return;
            }

            Block currentBlock = blockLocation.getBlock();
            if (currentBlock.getType() != originalBlockType) {
                log.log(Level.FINEST, "[MiningTask] Block at " + blockLocation + " changed type. Cancelling task.");
                clearMiningStateAndCancel(true); // Clear animation for original location
                return;
            }

            // Check if player is still targeting the same block
            Location playerCurrentTarget = playerTargetBlock.get(playerUUID);
            if (playerCurrentTarget == null || !playerCurrentTarget.equals(blockLocation)) {
                 // Check using ray trace as a fallback or more precise check
                 Block actualTarget = player.getTargetBlockExact(5);
                 if (actualTarget == null || !actualTarget.getLocation().equals(blockLocation)) {
                    log.log(Level.FINEST, "[MiningTask] Player " + player.getName() + " is no longer targeting " + blockLocation + ". Cancelling task.");
                    clearMiningStateAndCancel(true);
                    return;
                 }
                 // If ray trace confirms, update our map (player might have "flickered" view)
                 playerTargetBlock.put(playerUUID, blockLocation);
            }
            
            // Check tool, but use the initially captured tool for stat calculation consistency.
            // However, if they swap to an invalid tool, we should stop.
            ItemStack currentToolInHand = player.getInventory().getItemInMainHand();
            if (getApplicableToolSpeedStat(currentToolInHand, originalBlockType, statsManager.getStats(player)) == -1) {
                log.log(Level.FINEST, "[MiningTask] Player " + player.getName() + " swapped to an ineffective tool. Cancelling task.");
                clearMiningStateAndCancel(true);
                return;
            }


            // Calculate progress for this tick
            // block.getBreakSpeed(player) is crucial: it incorporates tool material, efficiency, player effects (Haste/Fatigue)
            // It uses the player's current main hand item implicitly.
            float vanillaProgressPerVanillaTick = currentBlock.getBreakSpeed(player); // Pass the tool used to start mining

            if (vanillaProgressPerVanillaTick <= 0f && !isUnbreakableVanillaBlock(originalBlockType)) {
                log.log(Level.INFO, "[MiningTask] Vanilla progress is 0 for " + originalBlockType + ". Cancelling task.");
                clearMiningStateAndCancel(true);
                return;
            }
            
            double customMultiplier = 1.0 + (this.speedStat / 100.0); // Use the speedStat captured at task start
            if (customMultiplier <= 0) customMultiplier = 0.01; 

            // Triple block breaking speed for tree tools
            String toolItemId = toolUsed.getItemMeta().getPersistentDataContainer().get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);
            if (toolItemId != null && toolItemId.startsWith("tree_")) {
                customMultiplier *= 3;
            }

            // Assuming 1 BukkitRunnable tick = 1 "damage application tick" for simplicity.
            // Vanilla usually applies damage over several ticks for one "swing animation cycle".
            // block.getBreakSpeed is "damage per tick if player is swinging".
            // If getBreakSpeed already implies damage over 1 game tick, then this should be fine.
            float actualProgressThisTick = (float) (vanillaProgressPerVanillaTick * customMultiplier);
            
            float currentAccumulatedProgress = playerBlockProgress.getOrDefault(playerUUID, 0.0f);
            float newAccumulatedProgress = currentAccumulatedProgress + actualProgressThisTick;

            log.log(Level.INFO, String.format("[MiningTask] Player: %s, Block: %s, Tool: %s, SpeedStat: %d, VanillaProgTick: %.4f, Multiplier: %.2f, ActualProgTick: %.4f, OldAccum: %.4f, NewAccum: %.4f",
                    player.getName(), originalBlockType, toolUsed.getType(), this.speedStat, vanillaProgressPerVanillaTick, customMultiplier, actualProgressThisTick, currentAccumulatedProgress, newAccumulatedProgress));

            if (newAccumulatedProgress >= 1.0f) {
                playerBlockProgress.put(playerUUID, 1.0f); // Cap for animation
                breakBlockAndCleanupAndCancelTask(currentBlock, toolUsed); // Pass currentBlock instance
            } else {
                playerBlockProgress.put(playerUUID, newAccumulatedProgress);
            }
        }

        private void breakBlockAndCleanupAndCancelTask(Block blockToBreak, ItemStack toolForBreaking) {
            log.log(Level.INFO, "[MiningTask] Breaking block: " + blockToBreak.getType() + " for " + player.getName());
            
            boolean broken = blockToBreak.breakNaturally(toolForBreaking);
            if (broken) {
                handleToolDurability(player, toolForBreaking); // Use the tool snapshot
            } else {
                log.warning("[MiningTask] block.breakNaturally() returned false for " + blockToBreak.getType() + " by " + player.getName());
            }
            clearMiningStateAndCancel(true); // true to ensure animation is cleared
        }
        
        private void clearMiningStateAndCancel(boolean sendClearAnimation) {
            clearMiningState(player, blockLocation, sendClearAnimation, true); // true to force task cancel
            this.cancel(); // Cancel self
        }
    }
    
    private boolean isUnbreakableVanillaBlock(Material material) {
        return material == Material.BEDROCK || material == Material.BARRIER || material == Material.COMMAND_BLOCK ||
               material == Material.CHAIN_COMMAND_BLOCK || material == Material.REPEATING_COMMAND_BLOCK ||
               material == Material.STRUCTURE_BLOCK || material == Material.JIGSAW ||
               material == Material.END_PORTAL_FRAME || material == Material.END_GATEWAY || material == Material.LIGHT;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) // MONITOR to act after other plugins
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();

        // If the block break was due to our custom mining task, it's already handled.
        // This check prevents recursive calls or double processing if our task broke the block.
        if (isAnyTaskTargeting(block.getLocation())) {
            // Our task is handling it, or just handled it.
            // log.log(Level.FINEST, "[PTL BreakEvt] Block break at " + block.getLocation() + " was handled by MiningTask. Skipping.");
            // return; //  Letting it proceed to check for Treecapitator for now.
        }

        if (tool != null && tool.hasItemMeta()) {
            ItemMeta meta = tool.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                if (pdc.has(NBTKeys.ABILITY_ID_KEY, PersistentDataType.STRING)) {
                    String abilityId = pdc.get(NBTKeys.ABILITY_ID_KEY, PersistentDataType.STRING);
                    if (TREECAPITATOR_ABILITY_ID.equalsIgnoreCase(abilityId)) {
                        handleTreecapitator(player, block, tool, pdc);
                        // Note: Treecapitator effect happens *after* the initial block is broken by player/event.
                    }
                }
            }
        }

        // The rest of this method is for vanilla breaks or breaks by other plugins,
        // to ensure our mining state is cleared if the block is broken externally.
        clearMiningState(player, block.getLocation(), false, true);
        log.log(Level.FINEST, "[PTL BreakEvt] Block " + block.getType() + " at " + block.getLocation() + " broken by " + player.getName() + ". Cleared any mining state.");
    }

    private void handleTreecapitator(Player player, Block startBlock, ItemStack axe, PersistentDataContainer axePdc) {
        if (!Tag.LOGS.isTagged(startBlock.getType())) {
            return; // Not a log
        }

        UUID playerId = player.getUniqueId();
        long cooldownSeconds = axePdc.getOrDefault(NBTKeys.ABILITY_COOLDOWN_KEY, PersistentDataType.INTEGER, 2); // Default 2s if not set
        long cooldownMillis = cooldownSeconds * 1000L;

        if (treecapitatorCooldowns.containsKey(playerId)) {
            long lastUsed = treecapitatorCooldowns.get(playerId);
            if (System.currentTimeMillis() - lastUsed < cooldownMillis) {
                return;
            }
        }

        Material logType = startBlock.getType();
        Set<Block> toBreak = new HashSet<>();
        Queue<Block> toCheck = new LinkedList<>();

        toCheck.add(startBlock); // The initial block is already broken by the event, but add to check its neighbors
        Set<Block> visited = new HashSet<>(); // Prevent infinite loops with weird tree structures
        visited.add(startBlock);

        int brokenCount = 0;

        while (!toCheck.isEmpty() && brokenCount < TREECAPITATOR_MAX_BREAK_COUNT) {
            Block current = toCheck.poll();

            // Check if it's a log of the same type (it should be, if added correctly)
            if (Tag.LOGS.isTagged(current.getType()) && current.getType() == logType) {
                // Only add to `toBreak` if it's not the startBlock (which is already being broken by the event)
                if (!current.equals(startBlock)) {
                    toBreak.add(current);
                    brokenCount++;
                }

                // Add neighbors to check
                for (BlockFace face : BlockFace.values()) { // Check all 26 directions (incl. diagonals)
                    if (face == BlockFace.SELF) continue;
                    Block neighbor = current.getRelative(face);
                    if (neighbor.getType() == logType && !visited.contains(neighbor)) {
                        visited.add(neighbor);
                        toCheck.add(neighbor);
                    }
                }
            }
        }

        if (!toBreak.isEmpty()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
            // Break the additional blocks
            for (Block logBlock : toBreak) {
                if (logBlock.equals(startBlock)) continue; // Should not happen due to earlier check, but safety

                // Simulate player breaking for drops and events, but bypass protection plugins if needed (tricky)
                // For simplicity, just break the block and let default drops happen.
                // Check if game mode allows breaking, though Treecapitator implies ability to break.
                if (player.getGameMode() != GameMode.CREATIVE) {
                    logBlock.breakNaturally(axe); // Uses the axe for drops, respects enchantments like Silk Touch/Fortune
                } else {
                    logBlock.setType(Material.AIR); // Creative just removes it
                }
                logBlock.getWorld().spawnParticle(org.bukkit.Particle.BLOCK_DUST, logBlock.getLocation().add(0.5, 0.5, 0.5), 10, 0.5, 0.5, 0.5, logBlock.getType().createBlockData());
            }
            // Apply durability damage for the extra blocks broken if the axe is damageable
            if (axe.getItemMeta() instanceof Damageable && !axe.getItemMeta().isUnbreakable()) {
                Damageable damageable = (Damageable) axe.getItemMeta();
                int damageToApply = toBreak.size(); // Simple: 1 durability per extra block
                damageable.setDamage(damageable.getDamage() + damageToApply);
                axe.setItemMeta(damageable);
                if (damageable.getDamage() >= axe.getType().getMaxDurability()) {
                    player.getInventory().setItemInMainHand(null); // Or the slot it was in
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                }
            }
            treecapitatorCooldowns.put(playerId, System.currentTimeMillis());
            player.sendMessage(Component.text("Treecapitator felled " + (toBreak.size() +1)  + " logs!", NamedTextColor.GREEN));
        }
    }

    private boolean isAnyTaskTargeting(Location location) {
        for (UUID playerId : activeMiningTasks.keySet()) {
            Location target = playerTargetBlock.get(playerId); // Check against the main target map
            if (location.equals(target)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        Location targetLocation = playerTargetBlock.get(playerUUID);
        if (targetLocation != null) {
            clearMiningState(player, targetLocation, true, true); // Send clear anim, force cancel task
        }
        // Ensure all maps are cleaned up for the quitting player
        playerTargetBlock.remove(playerUUID);
        playerBlockProgress.remove(playerUUID);
        BukkitTask task = activeMiningTasks.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }
    }

    private void handleToolDurability(Player player, ItemStack tool) {
        if (player.getGameMode() == GameMode.SURVIVAL && tool != null && tool.getType() != Material.AIR && tool.getItemMeta() instanceof Damageable) {
            ItemMeta iMeta = tool.getItemMeta();
            Damageable dMeta = (Damageable) iMeta;
            
            // Check PersistentDataContainer for our custom unbreakable tag first
            // String customUnbreakable = iMeta.getPersistentDataContainer().get(NBTKeys.UNBREAKABLE_KEY, PersistentDataType.STRING);
            // if ("true".equalsIgnoreCase(customUnbreakable)) return;

            // Then check ItemMeta's unbreakable flag (from spigot api, set by item attribute or enchant)
            if (iMeta.isUnbreakable()) return;


            int unbreakingLevel = tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.DURABILITY);
            if (new Random().nextInt(unbreakingLevel + 1) == 0) { // Vanilla unbreaking logic
                dMeta.setDamage(dMeta.getDamage() + 1);
                tool.setItemMeta(iMeta); // Apply changes to the item's meta
                if (dMeta.getDamage() >= tool.getType().getMaxDurability()) {
                    log.log(Level.FINE, "[PTL Dura] Tool broke: " + tool.getType() + " for " + player.getName());
                    // Check both hands for the tool, as player might have swapped or it's an offhand tool
                    if (tool.equals(player.getInventory().getItemInMainHand())) {
                         player.getInventory().setItemInMainHand(null);
                    } else if (tool.equals(player.getInventory().getItemInOffHand())) {
                         player.getInventory().setItemInOffHand(null);
                    }
                    // If it's not in either hand but was the tool used (e.g. from a different slot due to prior swap)
                    // this logic might not remove it correctly. The cloned toolUsed in MiningTask helps keep a reference.
                    // For now, this covers main/off hand.
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                }
            }
        }
    }

    // --- Preserved Fishing Logic ---
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            Player player = event.getPlayer();
            FishHook hook = event.getHook();
            if (hook == null) return;

            PlayerStats stats = statsManager.getStats(player);
            int fishingSpeedStat = stats.getFishingSpeed();

            if (fishingSpeedStat != 0) {
                try {
                    // Paper API for modifying fish hook wait times
                    int minWaitTime = hook.getMinWaitTime(); // Vanilla default 100
                    int maxWaitTime = hook.getMaxWaitTime(); // Vanilla default 600
                    
                    // Positive fishingSpeedStat reduces wait time, negative increases it.
                    // Example: 100 fishing speed = 0.002 * 100 = 0.2 = 20% reduction. Factor = 0.8
                    // -100 fishing speed = -0.002 * -100 = -0.2 = 20% increase. Factor = 1.2
                    // Clamp speedStat effect to avoid extreme values (e.g., -200% to +80% modification of time)
                    // A fishingSpeedStat of 500 would make wait time 0. A stat of -250 would make it 1.5x.
                    // Let's cap the factor to prevent negative or excessively long/short times.
                    // Min factor 0.2 (80% reduction), Max factor 2.0 (100% increase)
                    double speedEffect = Math.max(-400, Math.min(400, fishingSpeedStat)); // Cap effective stat for factor calc
                    double speedFactor = 1.0 - (speedEffect * 0.002); // default 0.002 from hypixel
                    speedFactor = Math.max(0.2, Math.min(2.0, speedFactor)); // Clamp factor

                    int newMinWaitTime = (int) Math.round(minWaitTime * speedFactor);
                    int newMaxWaitTime = (int) Math.round(maxWaitTime * speedFactor);

                    // Ensure min is reasonably small and max is greater than min
                    newMinWaitTime = Math.max(20, newMinWaitTime); // Minimum 1 second wait
                    newMaxWaitTime = Math.max(newMinWaitTime + 40, newMaxWaitTime); // Ensure max is at least 2s more than min

                    hook.setMinWaitTime(newMinWaitTime);
                    hook.setMaxWaitTime(newMaxWaitTime);
                    log.log(Level.FINEST, String.format("[PTL Fish] Player %s - SpeedStat: %d. Factor: %.2f. OrigMin: %d, OrigMax: %d. NewMin: %d, NewMax: %d",
                            player.getName(), fishingSpeedStat, speedFactor, minWaitTime, maxWaitTime, hook.getMinWaitTime(), hook.getMaxWaitTime()));
                } catch (NoSuchMethodError e) {
                    log.warning("[PTL Fish] Paper API for FishHook wait time (get/setMinWaitTime, get/setMaxWaitTime) not found. Fishing Speed stat requires Paper server with this API.");
                } catch (Exception e) {
                    log.log(Level.SEVERE, "[PTL Fish] Error applying fishing speed for " + player.getName(), e);
                }
            }
        }
    }

    private void clearMiningState(Player player, Location blockLocToClear, boolean sendClearAnimationPacket, boolean forceCancelTask) {
        if (player == null || blockLocToClear == null) return;
        UUID playerUUID = player.getUniqueId();

        BukkitTask task = activeMiningTasks.get(playerUUID);
        if (task != null) {
            boolean taskWasForThisBlock = false;
            Location currentTargetForPlayer = playerTargetBlock.get(playerUUID);
            if (currentTargetForPlayer != null && currentTargetForPlayer.equals(blockLocToClear)) {
                taskWasForThisBlock = true;
            }

            if (forceCancelTask || taskWasForThisBlock) {
                task.cancel();
                activeMiningTasks.remove(playerUUID);
                log.log(Level.FINEST, "[PTL Clear] Cancelled mining task for player " + player.getName() + " (force=" + forceCancelTask + ", wasForThisBlock=" + taskWasForThisBlock + ")");
            }
        }

        Location currentEffectivelyTargetedBlock = playerTargetBlock.get(playerUUID);
        if (blockLocToClear.equals(currentEffectivelyTargetedBlock)) {
            playerTargetBlock.remove(playerUUID);
            playerBlockProgress.remove(playerUUID);
            log.log(Level.FINEST, "[PTL Clear] Cleared target & progress for player " + player.getName() + " on block " + blockLocToClear);
            
            if (sendClearAnimationPacket) {
                // Placeholder: send packet to clear block break animation if using ProtocolLib
                // player.sendBlockDamage(blockLocToClear, 0, -1); // Example, may need specific entity ID
            }
        }
        
        // If no other player is targeting this block, remove its general animation ID.
        // This logic might need adjustment if activeBlockAnimations map is used elsewhere.
        boolean stillReferenced = false;
        for (Location loc : playerTargetBlock.values()) {
            if (loc.equals(blockLocToClear)) {
                stillReferenced = true;
                break;
            }
        }
        if (!stillReferenced && !isAnyTaskTargeting(blockLocToClear)) {
            // e.g., activeBlockAnimations.remove(blockLocToClear);
            log.log(Level.FINEST, "[PTL Clear] Potentially removed general animation for " + blockLocToClear + " (if map was used)");
        }
    }
}
