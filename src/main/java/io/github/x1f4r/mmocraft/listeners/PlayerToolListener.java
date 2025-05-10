package io.github.x1f4r.mmocraft.listeners; // General listeners package

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
import io.github.x1f4r.mmocraft.stats.PlayerStats; // Correct import
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayerToolListener implements Listener {

    // private final MMOCore core; // Removed
    private final PlayerStatsManager statsManager;
    private final Logger log;

    // Track custom block damage progress (Player UUID -> Block -> Damage Accumulated)
    private final Map<UUID, Map<Block, Float>> playerBlockDamage = new HashMap<>();
    // Track blocks being broken by this listener to prevent re-entry issues
    private final Set<Block> customBreakingBlocks = new HashSet<>();

    // Define material sets for different tool types (Consider loading from config?)
    private static final Set<Material> MINING_MATERIALS = new HashSet<>(Arrays.asList(
            Material.STONE, Material.COBBLESTONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE,
            Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF, Material.CALCITE,
            Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE,
            Material.LAPIS_ORE, Material.REDSTONE_ORE, Material.COPPER_ORE,
            Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.DEEPSLATE_EMERALD_ORE, Material.DEEPSLATE_LAPIS_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.GILDED_BLACKSTONE,
            Material.ANCIENT_DEBRIS, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.NETHERRACK,
            Material.END_STONE, Material.SANDSTONE, Material.RED_SANDSTONE, Material.TERRACOTTA,
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.BASALT, Material.SMOOTH_BASALT, Material.BLACKSTONE,
            Material.AMETHYST_BLOCK, Material.BUDDING_AMETHYST
    ));

    private static final Set<Material> FORAGING_MATERIALS_AXE = new HashSet<>(Arrays.asList(
            // Logs, Wood, Stems, Hyphae (Stripped and Non-Stripped)
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG, Material.CRIMSON_STEM, Material.WARPED_STEM,
            Material.OAK_WOOD, Material.SPRUCE_WOOD, Material.BIRCH_WOOD, Material.JUNGLE_WOOD, Material.ACACIA_WOOD, Material.DARK_OAK_WOOD, Material.MANGROVE_WOOD, Material.CHERRY_WOOD, Material.CRIMSON_HYPHAE, Material.WARPED_HYPHAE,
            Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG, Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG, Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_WARPED_STEM,
            Material.STRIPPED_OAK_WOOD, Material.STRIPPED_SPRUCE_WOOD, Material.STRIPPED_BIRCH_WOOD, Material.STRIPPED_JUNGLE_WOOD, Material.STRIPPED_ACACIA_WOOD, Material.STRIPPED_DARK_OAK_WOOD, Material.STRIPPED_MANGROVE_WOOD, Material.STRIPPED_CHERRY_WOOD, Material.STRIPPED_CRIMSON_HYPHAE, Material.STRIPPED_WARPED_HYPHAE,
            // Other Axe-breakable blocks
            Material.PUMPKIN, Material.CARVED_PUMPKIN, Material.JACK_O_LANTERN, Material.MELON, Material.BOOKSHELF, Material.CRAFTING_TABLE, Material.CHEST, Material.TRAPPED_CHEST, Material.NOTE_BLOCK, Material.MUSHROOM_STEM, Material.RED_MUSHROOM_BLOCK, Material.BROWN_MUSHROOM_BLOCK,
            Material.BAMBOO, Material.COCOA, Material.VINE, Material.MANGROVE_ROOTS, Material.WARPED_WART_BLOCK, Material.NETHER_WART_BLOCK
    ));
    private static final Set<Material> FORAGING_MATERIALS_SHOVEL = new HashSet<>(Arrays.asList(
            Material.DIRT, Material.GRASS_BLOCK, Material.SAND, Material.RED_SAND, Material.GRAVEL,
            Material.CLAY, Material.SOUL_SAND, Material.SOUL_SOIL, Material.SNOW_BLOCK, Material.SNOW,
            Material.MYCELIUM, Material.PODZOL, Material.FARMLAND, Material.DIRT_PATH, Material.ROOTED_DIRT, Material.MUD // Added Mud
    ));


    public PlayerToolListener(MMOCore core) {
        // this.core = core; // Removed assignment
        this.statsManager = core.getPlayerStatsManager();
        this.log = MMOPlugin.getMMOLogger();
    }

    // --- Custom Block Breaking Speed ---
    // Requires Paper API for BlockDamageEvent to fire reliably each tick.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        ItemStack tool = event.getItemInHand(); // Tool used

        // Ignore creative/spectator, no tool, or if already being custom broken
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (tool == null || tool.getType() == Material.AIR) return;
        if (customBreakingBlocks.contains(block)) return;

        PlayerStats stats = statsManager.getStats(player);
        int speedStat = 0; // Mining Speed, Foraging Speed, etc.
        boolean correctToolType = false;
        String toolName = tool.getType().name().toUpperCase();

        // Determine the relevant speed stat based on tool and block
        if (toolName.endsWith("_PICKAXE") && MINING_MATERIALS.contains(block.getType())) {
            speedStat = stats.getMiningSpeed();
            correctToolType = true;
        } else if (toolName.endsWith("_SHOVEL") && FORAGING_MATERIALS_SHOVEL.contains(block.getType())) {
            // Shovel speed often tied to Mining Speed in concepts like Hypixel Skyblock
            speedStat = stats.getMiningSpeed(); // Or use Foraging Speed if preferred
            correctToolType = true;
        } else if (toolName.endsWith("_AXE") && FORAGING_MATERIALS_AXE.contains(block.getType())) {
            speedStat = stats.getForagingSpeed();
            correctToolType = true;
        }
        // Add HOE logic if needed for Farming Speed

        // Only proceed if using the correct tool type AND the relevant speed stat is > 0
        if (correctToolType && speedStat > 0) {
            event.setCancelled(true); // Cancel vanilla damaging

            float blockHardness = block.getType().getHardness();
            // Handle blocks with 0 hardness (instantly breakable vanilla)
            if (blockHardness <= 0) blockHardness = 0.01f; // Assign a tiny hardness

            // --- Damage Calculation ---
            // This needs significant tuning for balance. Based loosely on breaking power concepts.
            // Higher speed stat should drastically reduce time. Hardness increases time.
            // Base ticks needed = Hardness * SomeConstant (e.g., 20 for 1 second base)
            // Speed factor = (1 + SpeedStat / 100) ? Or something more exponential?
            // Ticks = (Base Ticks Needed) / Speed Factor
            // Damage per tick = 1.0f / Ticks

            // Simplified Approach: Damage = (Base Rate * Speed Factor) / Hardness
            float baseRate = 0.05f; // Adjust this: Controls overall speed
            float speedFactor = 1.0f + (speedStat / 50.0f); // Adjust divisor: Controls how impactful speed stat is
            float damagePerTick = (baseRate * speedFactor) / blockHardness;

            // Ensure minimum damage per tick to prevent extremely slow breaking
            damagePerTick = Math.max(0.005f, damagePerTick); // Minimum 0.5% damage per tick

            Map<Block, Float> damages = playerBlockDamage.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
            float currentDamage = damages.getOrDefault(block, 0.0f) + damagePerTick;

            log.finest("Block: " + block.getType() + " Hardness: " + blockHardness + " SpeedStat: " + speedStat + " DamageTick: " + damagePerTick + " CurrentDamage: " + currentDamage);

            if (currentDamage >= 1.0f) {
                // Block should break
                customBreakingBlocks.add(block); // Mark as being broken by us
                boolean brokenNaturally = block.breakNaturally(tool); // Try to break naturally first
                if (!brokenNaturally) {
                    // Fallback if breakNaturally fails (e.g., cancelled by another plugin)
                    block.setType(Material.AIR);
                }
                // Play break sound
                player.playSound(block.getLocation(), block.getBlockData().getSoundGroup().getBreakSound(), 1.0f, 0.8f);

                // Handle Tool Durability
                handleToolDurability(player, tool);

                // Clean up tracking maps
                damages.remove(block);
                customBreakingBlocks.remove(block);

                // Reset damage map for player if empty (minor optimization)
                if (damages.isEmpty()) {
                    playerBlockDamage.remove(player.getUniqueId());
                }
            } else {
                // Store progress and potentially send block crack packets (requires NMS/ProtocolLib)
                damages.put(block, currentDamage);
                // sendBlockCrackPacket(player, block, currentDamage); // Placeholder
            }
        } else {
            // If not using the right tool or speed stat is 0, clear any tracked damage for this block
            playerBlockDamage.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).remove(block);
        }
    }

    // Cleanup map if block breaks via other means (explosion, piston, etc.)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakCleanup(BlockBreakEvent event) {
        // Check if the player associated with the event exists in the map
         Map<Block, Float> damages = playerBlockDamage.get(event.getPlayer().getUniqueId());
         if (damages != null) {
             damages.remove(event.getBlock());
              // Optional: Remove player entry if their map is now empty
              if (damages.isEmpty()) {
                  playerBlockDamage.remove(event.getPlayer().getUniqueId());
              }
         }
        customBreakingBlocks.remove(event.getBlock());
    }

    private void handleToolDurability(Player player, ItemStack tool) {
        if (player.getGameMode() == GameMode.SURVIVAL && tool != null && tool.getItemMeta() instanceof Damageable) {
            ItemMeta iMeta = tool.getItemMeta();
            Damageable dMeta = (Damageable) iMeta;
            if (!dMeta.isUnbreakable()) {
                int unbreakingLevel = tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.DURABILITY);
                // Chance to IGNORE durability loss is unbreakingLevel / (unbreakingLevel + 1)
                // So, chance to TAKE damage is 1 - (unbreakingLevel / (unbreakingLevel + 1)) = 1 / (unbreakingLevel + 1)
                if (Math.random() < (1.0 / (unbreakingLevel + 1.0))) { // Apply Unbreaking
                    dMeta.setDamage(dMeta.getDamage() + 1);
                    tool.setItemMeta(dMeta); // Apply updated meta
                    if (dMeta.getDamage() >= tool.getType().getMaxDurability()) {
                        // Item broke
                        player.getInventory().setItemInMainHand(null); // Or offhand if applicable
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                    }
                }
            }
        }
    }


    // --- Fishing Speed ---
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        // Primarily affects the time until a bite
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            Player player = event.getPlayer();
            FishHook hook = event.getHook();
            if (hook == null) return;

            PlayerStats stats = statsManager.getStats(player);
            int fishingSpeedStat = stats.getFishingSpeed();

            if (fishingSpeedStat > 0) {
                // Paper API allows direct modification of wait times
                try {
                    // Reduce Min Wait Time (Ticks) - higher fishing speed = lower min wait
                    // Example: 100 speed reduces min wait by 50 ticks (2.5s), capped at 20 ticks min
                    int minWaitTime = hook.getMinWaitTime(); // Get current value
                    int minWaitReduction = Math.min(minWaitTime - 20, fishingSpeedStat / 2);
                    hook.setMinWaitTime(Math.max(20, minWaitTime - minWaitReduction));

                    // Reduce Max Wait Time (Ticks) - higher fishing speed = lower max wait
                    // Example: 100 speed reduces max wait by 100 ticks (5s), capped relative to min wait
                    int maxWaitTime = hook.getMaxWaitTime(); // Get current value
                    int maxWaitReduction = Math.min(maxWaitTime - hook.getMinWaitTime() - 40, fishingSpeedStat); // Ensure max is always > min + buffer
                    hook.setMaxWaitTime(Math.max(hook.getMinWaitTime() + 40, maxWaitTime - maxWaitReduction));

                     log.finest(String.format("Player %s Fishing - Speed: %d, MinWait: %d, MaxWait: %d",
                             player.getName(), fishingSpeedStat, hook.getMinWaitTime(), hook.getMaxWaitTime()));

                } catch (NoSuchMethodError e) {
                    // This occurs if server is not Paper or compatible fork
                    log.warning("Paper API for FishHook modification not found. Fishing Speed stat requires Paper server.");
                    // Optionally apply Luck effect as a fallback (less direct)
                    // int luckAmplifier = Math.min(4, (fishingSpeedStat / 25) - 1);
                    // if (luckAmplifier >= 0 && !player.hasPotionEffect(PotionEffectType.LUCK)) {
                    //    player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, hook.getMaxWaitTime() + 20, luckAmplifier, true, false));
                    // }
                } catch (Exception e) {
                     log.log(Level.SEVERE, "Error applying fishing speed for " + player.getName(), e);
                }
            }
        }
        // Could add logic for CAUGHT_FISH state to modify loot based on fishing speed?
    }
}
