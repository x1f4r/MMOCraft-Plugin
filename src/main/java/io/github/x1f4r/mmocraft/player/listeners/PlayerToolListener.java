package io.github.x1f4r.mmocraft.listeners;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.player.PlayerStats;
import io.github.x1f4r.mmocraft.player.PlayerStatsManager;
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
// Removed PotionEffect imports as Haste is no longer used
// import org.bukkit.potion.PotionEffect;
// import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger; // Added Logger import

public class PlayerToolListener implements Listener {

    private final MMOCraft plugin;
    private final PlayerStatsManager statsManager;
    private static final Logger logger = MMOCraft.getPlugin(MMOCraft.class).getLogger(); // Added logger instance

    // Map to track custom block damage progress
    private final Map<UUID, Map<Block, Float>> playerBlockDamage = new HashMap<>();
    // Map to track blocks being custom broken to prevent event re-entry
    private final Set<Block> customBreakingBlocks = new HashSet<>();


    private static final Set<Material> MINING_MATERIALS = new HashSet<>(Arrays.asList(
            Material.STONE, Material.COBBLESTONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE,
            Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF,
            Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE,
            Material.LAPIS_ORE, Material.REDSTONE_ORE, Material.COPPER_ORE,
            Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.DEEPSLATE_EMERALD_ORE, Material.DEEPSLATE_LAPIS_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.GILDED_BLACKSTONE,
            Material.ANCIENT_DEBRIS, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.NETHERRACK,
            Material.END_STONE, Material.SANDSTONE, Material.RED_SANDSTONE, Material.TERRACOTTA,
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.BASALT, Material.SMOOTH_BASALT, Material.BLACKSTONE
    ));

    private static final Set<Material> FORAGING_MATERIALS_AXE = new HashSet<>(Arrays.asList(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG, Material.CRIMSON_STEM, Material.WARPED_STEM,
            Material.OAK_WOOD, Material.SPRUCE_WOOD, Material.BIRCH_WOOD, Material.JUNGLE_WOOD, Material.ACACIA_WOOD, Material.DARK_OAK_WOOD, Material.MANGROVE_WOOD, Material.CHERRY_WOOD, Material.CRIMSON_HYPHAE, Material.WARPED_HYPHAE,
            Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG, Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG, Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_WARPED_STEM,
            Material.STRIPPED_OAK_WOOD, Material.STRIPPED_SPRUCE_WOOD, Material.STRIPPED_BIRCH_WOOD, Material.STRIPPED_JUNGLE_WOOD, Material.STRIPPED_ACACIA_WOOD, Material.STRIPPED_DARK_OAK_WOOD, Material.STRIPPED_MANGROVE_WOOD, Material.STRIPPED_CHERRY_WOOD, Material.STRIPPED_CRIMSON_HYPHAE, Material.STRIPPED_WARPED_HYPHAE,
            Material.PUMPKIN, Material.MELON, Material.BOOKSHELF, Material.CRAFTING_TABLE, Material.CHEST, Material.TRAPPED_CHEST, Material.NOTE_BLOCK, Material.MUSHROOM_STEM,
            Material.BAMBOO, Material.COCOA, Material.VINE, Material.MANGROVE_ROOTS, Material.WARPED_WART_BLOCK, Material.NETHER_WART_BLOCK
    ));
    private static final Set<Material> FORAGING_MATERIALS_SHOVEL = new HashSet<>(Arrays.asList(
            Material.DIRT, Material.GRASS_BLOCK, Material.SAND, Material.RED_SAND, Material.GRAVEL,
            Material.CLAY, Material.SOUL_SAND, Material.SOUL_SOIL, Material.SNOW_BLOCK, Material.SNOW,
            Material.MYCELIUM, Material.PODZOL, Material.FARMLAND, Material.DIRT_PATH
    ));


    public PlayerToolListener(MMOCraft plugin) {
        this.plugin = plugin;
        this.statsManager = plugin.getPlayerStatsManager();
    }

    // Using BlockDamageEvent (Requires Paper API for this event to fire reliably per tick)
    // If not using Paper, BlockBreakEvent needs a different custom breaking implementation.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        ItemStack tool = event.getItemInHand();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (tool == null || tool.getType() == Material.AIR) {
            return;
        }
        if (customBreakingBlocks.contains(block)) {
            return;
        }

        PlayerStats stats = statsManager.getStats(player);
        int speedStat = 0;
        boolean isCorrectToolForBlock = false;

        String toolName = tool.getType().name();

        if (toolName.endsWith("_PICKAXE") && MINING_MATERIALS.contains(block.getType())) {
            speedStat = stats.getMiningSpeed();
            isCorrectToolForBlock = true;
        } else if (toolName.endsWith("_SHOVEL") && FORAGING_MATERIALS_SHOVEL.contains(block.getType())) {
            speedStat = stats.getMiningSpeed();
            isCorrectToolForBlock = true;
        } else if (toolName.endsWith("_AXE") && FORAGING_MATERIALS_AXE.contains(block.getType())) {
            speedStat = stats.getForagingSpeed();
            isCorrectToolForBlock = true;
        }

        if (speedStat > 0 && isCorrectToolForBlock) {
            event.setCancelled(true); // Cancel vanilla damaging

            float blockHardness = block.getType().getHardness();
            if (blockHardness <= 0) blockHardness = 0.1f;

            // Simplified damage calculation - needs significant tuning for balance
            // Base damage contribution per tick (higher = faster break)
            float baseDamageContribution = 0.05f; // Adjust this base value
            float damagePerTick = (baseDamageContribution / blockHardness) * (1.0f + (speedStat / 100.0f));

            Map<Block, Float> damages = playerBlockDamage.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
            float currentDamage = damages.getOrDefault(block, 0.0f) + damagePerTick;

            // logger.fine("Block: " + block.getType() + " Hardness: " + blockHardness + " SpeedStat: " + speedStat + " DamageTick: " + damagePerTick + " CurrentDamage: " + currentDamage);

            if (currentDamage >= 1.0f) {
                customBreakingBlocks.add(block);
                boolean success = block.breakNaturally(tool);
                if (!success) {
                    block.setType(Material.AIR);
                }
                player.playSound(block.getLocation(), block.getBlockData().getSoundGroup().getBreakSound(), 1.0f, 0.8f);

                // Tool Durability
                if (player.getGameMode() == GameMode.SURVIVAL && tool.getItemMeta() instanceof Damageable) {
                    ItemMeta iMeta = tool.getItemMeta(); // Get fresh meta
                    Damageable dMeta = (Damageable) iMeta;
                    if (!dMeta.isUnbreakable()) {
                        dMeta.setDamage(dMeta.getDamage() + 1);
                        tool.setItemMeta(dMeta); // Apply updated meta
                        if (dMeta.getDamage() >= tool.getType().getMaxDurability()) {
                            player.getInventory().setItemInMainHand(null);
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                        }
                    }
                }
                damages.remove(block);
                customBreakingBlocks.remove(block);
            } else {
                damages.put(block, currentDamage);
                // Visuals: Send block crack packets (Requires NMS or ProtocolLib)
                // Without NMS/ProtocolLib, the block cracking animation won't reflect the custom speed.
            }
        } else {
            // Clear damage if not using a custom tool or not applicable
            playerBlockDamage.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).remove(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakCleanup(BlockBreakEvent event) {
        // Clean up tracking maps when a block is broken (by vanilla or custom logic)
        playerBlockDamage.computeIfAbsent(event.getPlayer().getUniqueId(), k -> new HashMap<>()).remove(event.getBlock());
        customBreakingBlocks.remove(event.getBlock());
    }


    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        FishHook hook = event.getHook();

        if (event.getState() == PlayerFishEvent.State.FISHING || event.getState() == PlayerFishEvent.State.REEL_IN || event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            PlayerStats stats = statsManager.getStats(player);
            int fishingSpeedStat = stats.getFishingSpeed();

            if (fishingSpeedStat > 0 && hook != null) {

                // --- Paper API Specific Code (Commented Out for Spigot Compatibility) ---
                // If using Paper, uncomment these lines to enable fishing speed.
                /*
                try {
                    // Lure level modification
                    int lureAmplifier = Math.min(5, (fishingSpeedStat / 25) - 1 ); // Cap at Lure VI (amplifier 5)
                    int currentLure = 0;
                    // getLureLevel() might not exist in all Paper versions or Spigot. Check API docs.
                    // Assuming getLureLevel exists for this example:
                    // currentLure = hook.getLureLevel();

                    if (lureAmplifier >= 0) {
                        // hook.setLureLevel(lureAmplifier + currentLure); // Additive or set directly
                        hook.setApplyLure(true);
                    }

                    // Reduce Min/Max Wait Time
                    int minWaitReduction = Math.min(hook.getMinWaitTime() - 20, fishingSpeedStat / 2);
                    if (hook.getMinWaitTime() - minWaitReduction >= 20) {
                        hook.setMinWaitTime(hook.getMinWaitTime() - minWaitReduction);
                    } else {
                        hook.setMinWaitTime(20);
                    }

                    int maxWaitReduction = Math.min(hook.getMaxWaitTime() - hook.getMinWaitTime() - 20, fishingSpeedStat * 2);
                     if (hook.getMaxWaitTime() - maxWaitReduction >= hook.getMinWaitTime() + 20) {
                        hook.setMaxWaitTime(hook.getMaxWaitTime() - maxWaitReduction);
                    } else {
                        hook.setMaxWaitTime(hook.getMinWaitTime() + 20);
                    }
                     logger.fine(String.format("Player %s, FSP: %d, Lure: %d, MinWait: %d, MaxWait: %d",
                            player.getName(), fishingSpeedStat, hook.getLureLevel(), hook.getMinWaitTime(), hook.getMaxWaitTime()));

                } catch (NoSuchMethodError e) {
                    // This catch block will execute if the Paper API methods are not present (i.e., on Spigot/Bukkit)
                    logger.warning("Paper API for FishHook modification not available. Fishing speed stat requires Paper server software to function.");
                    // Optionally disable the feature entirely if Paper isn't detected on plugin startup.
                } catch (Exception e) {
                    // Catch other potential errors during reflection/API calls
                     logger.log(Level.SEVERE, "Error applying fishing speed for " + player.getName(), e);
                }
                */
                // --- End Paper API Specific Code ---

                // If not using Paper, the FISHING_SPEED stat currently has no effect.
                // You could potentially add a Luck potion effect as a fallback,
                // but it's less direct than modifying wait times.
                // Example Fallback (Optional):
                /*
                int luckAmplifier = Math.min(4, (fishingSpeedStat / 20) - 1); // Luck V max
                if (luckAmplifier >= 0 && !player.hasPotionEffect(PotionEffectType.LUCK)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 20 * 15, luckAmplifier, true, false, true));
                }
                */
            }
        }
    }
}
