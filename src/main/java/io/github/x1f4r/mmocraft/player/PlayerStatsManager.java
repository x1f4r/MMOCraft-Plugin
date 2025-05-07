package io.github.x1f4r.mmocraft.player;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import net.md_5.bungee.api.ChatMessageType; // Required for Action Bar
import net.md_5.bungee.api.chat.TextComponent; // Required for Action Bar
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerStatsManager {

    private final MMOCraft plugin;
    private final Map<UUID, PlayerStats> playerStatsCache = new HashMap<>();
    private final Map<UUID, AttributeModifier> speedModifiers = new HashMap<>();
    private static final String SPEED_MODIFIER_UUID_NAMESPACE = "mmocraft_speed_modifier_v2";


    public PlayerStatsManager(MMOCraft plugin) {
        this.plugin = plugin;
        startManaRegenTask();
        startStatRefreshTask();
        startManaBarUpdateTask(); // <-- THIS LINE WAS ADDED
    }

    public PlayerStats getStats(Player player) {
        if (player == null || !player.isOnline()) {
            // Return a new base stat object for safety if player is invalid,
            // though ideally, calls should only happen for valid players.
            return PlayerStats.base();
        }
        // computeIfAbsent ensures that we always work with and return the cached object
        return playerStatsCache.computeIfAbsent(player.getUniqueId(), uuid -> {
            PlayerStats newStats = PlayerStats.base();
            // Initial full update when the PlayerStats object is first created for a player
            // This ensures that even if join event is missed or called too early,
            // the first call to getStats will populate correctly from gear.
            // updateStatsFromEquipment(player, newStats);
            // applySpeedModifier(player, newStats.getSpeed());
            // The handlePlayerJoin method calls updateAndApplyAllEffects which should cover this.
            return newStats;
        });
    }

    private void updateStatsFromEquipment(Player player, PlayerStats stats) {
        if (player == null || !player.isOnline() || stats == null) return;

        PlayerInventory inventory = player.getInventory();
        PlayerStats baseDefaults = PlayerStats.base(); // Get fresh base stats for reset

        // Reset current stats to base before recalculating from gear
        stats.setStrength(baseDefaults.getStrength());
        stats.setCritChance(baseDefaults.getCritChance());
        stats.setCritDamage(baseDefaults.getCritDamage());
        stats.setMaxMana(baseDefaults.getMaxMana());
        stats.setSpeed(baseDefaults.getSpeed());

        // Iterate over armor and main hand item
        ItemStack[] armor = inventory.getArmorContents();
        for (ItemStack item : armor) {
            accumulateStatsFromItem(item, stats);
        }
        accumulateStatsFromItem(inventory.getItemInMainHand(), stats);
        // If you want to include off-hand:
        // accumulateStatsFromItem(inventory.getItemInOffHand(), stats);

        // Ensure current mana is correctly clamped after maxMana might have changed
        stats.setMaxMana(stats.getMaxMana()); // This also clamps currentMana if newMax < current
        stats.setCurrentMana(stats.getCurrentMana()); // Re-clamp currentMana just in case
    }

    public void updateAndApplyAllEffects(Player player) {
        if (player == null || !player.isOnline()) return;

        PlayerStats stats = getStats(player); // Ensures the player has a stats object in cache
        updateStatsFromEquipment(player, stats); // Recalculate all stats from equipment

        applySpeedModifier(player, stats.getSpeed()); // Apply speed effect based on new stat total
        // Other persistent effects like health boosts via attributes could be applied here too
    }

    private void accumulateStatsFromItem(ItemStack item, PlayerStats stats) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) { // Should not be null if hasItemMeta is true
                PersistentDataContainer pdc = meta.getPersistentDataContainer();

                if (NBTKeys.STRENGTH_KEY != null) stats.setStrength(stats.getStrength() + pdc.getOrDefault(NBTKeys.STRENGTH_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.CRIT_CHANCE_KEY != null) stats.setCritChance(stats.getCritChance() + pdc.getOrDefault(NBTKeys.CRIT_CHANCE_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.CRIT_DAMAGE_KEY != null) stats.setCritDamage(stats.getCritDamage() + pdc.getOrDefault(NBTKeys.CRIT_DAMAGE_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.MANA_KEY != null) stats.setMaxMana(stats.getMaxMana() + pdc.getOrDefault(NBTKeys.MANA_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.SPEED_KEY != null) stats.setSpeed(stats.getSpeed() + pdc.getOrDefault(NBTKeys.SPEED_KEY, PersistentDataType.INTEGER, 0));
            }
        }
    }

    private void applySpeedModifier(Player player, int speedStatPercentage) {
        AttributeInstance speedAttribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttribute == null) return;

        UUID modifierUUID = UUID.nameUUIDFromBytes((SPEED_MODIFIER_UUID_NAMESPACE + player.getUniqueId().toString()).getBytes());

        // Remove any existing modifier with the same UUID first
        AttributeModifier oldModifier = null;
        for(AttributeModifier modifier : speedAttribute.getModifiers()){
            if(modifier.getUniqueId().equals(modifierUUID)){
                oldModifier = modifier;
                break;
            }
        }
        if(oldModifier != null){
            try {
                speedAttribute.removeModifier(oldModifier);
            } catch (IllegalStateException e) {
                // This can happen if the modifier was already removed due to world change, death, etc.
                plugin.getLogger().log(Level.FINEST, "Tried to remove speed modifier that was already gone for " + player.getName() + " (UUID: " + modifierUUID +")");
            }
        }
        // Also ensure our tracking map is clean for this player if we just removed it
        speedModifiers.remove(player.getUniqueId());


        if (speedStatPercentage != 0) {
            double modifierAmount = (double) speedStatPercentage / 100.0;
            AttributeModifier newModifier = new AttributeModifier(
                    modifierUUID, // Use the consistent UUID
                    "mmocraft_speed_boost",
                    modifierAmount,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1
            );
            try {
                speedAttribute.addModifier(newModifier);
                speedModifiers.put(player.getUniqueId(), newModifier); // Track current modifier
            } catch (IllegalArgumentException e) {
                // This can happen if a modifier with the same UUID is somehow still there despite removal attempt
                plugin.getLogger().log(Level.WARNING, "Could not apply speed modifier for " + player.getName() + " (UUID: " + modifierUUID +"): " + e.getMessage());
            }
        }
    }

    public void handlePlayerJoin(Player player) {
        // Ensure a stats object exists, then update and apply effects
        getStats(player); // This will create a base PlayerStats object if not present
        updateAndApplyAllEffects(player);
    }

    public void handlePlayerQuit(Player player) {
        // Clean up speed modifier on quit
        AttributeInstance speedAttribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttribute != null) {
            AttributeModifier currentModifier = speedModifiers.remove(player.getUniqueId());
            if (currentModifier != null) {
                // Check if the modifier is actually present before trying to remove
                // to avoid IllegalStateException if it was already removed (e.g. by server/world change)
                boolean wasPresent = false;
                for(AttributeModifier mod : speedAttribute.getModifiers()){
                    if(mod.getUniqueId().equals(currentModifier.getUniqueId())){
                        wasPresent = true;
                        break;
                    }
                }
                if(wasPresent){
                    try { speedAttribute.removeModifier(currentModifier); }
                    catch (IllegalStateException e) { /* Modifier was already gone, ignore */ }
                }
            }
        }
        playerStatsCache.remove(player.getUniqueId()); // Clean up cache
    }

    private void startManaRegenTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Iterate over a copy of the keyset to avoid ConcurrentModificationException if a player quits during iteration
                for (UUID playerUUID : new ArrayList<>(playerStatsCache.keySet())) {
                    Player player = Bukkit.getPlayer(playerUUID);
                    if (player != null && player.isOnline()) {
                        PlayerStats stats = getStats(player); // Get their current stats object
                        if (stats.getCurrentMana() < stats.getMaxMana()) {
                            int manaToRegen = (int) Math.max(1, stats.getMaxMana() * 0.025); // Regen 2.5% of max mana, min 1
                            stats.addMana(manaToRegen);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 20L * 1); // Initial delay 5s, then every 1 second
    }

    private void startStatRefreshTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) { // Iterate over online players directly
                    updateAndApplyAllEffects(player);
                }
            }
        }.runTaskTimer(plugin, 40L, 20L * 1); // Refresh stats every 1 second, after initial 2s delay
    }

    // NEW TASK FOR MANA BAR
    private void startManaBarUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // No need to check player.isOnline() again if iterating Bukkit.getOnlinePlayers()
                    PlayerStats stats = getStats(player); // This gets from cache or creates base
                    String manaMessage = ChatColor.AQUA + "Mana: " +
                            ChatColor.DARK_AQUA + stats.getCurrentMana() +
                            ChatColor.AQUA + "/" +
                            ChatColor.DARK_AQUA + stats.getMaxMana();
                    try {
                        // Ensure Spigot API is available (usually is on modern servers)
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(manaMessage));
                    } catch (NoSuchMethodError e) {
                        // This catch is for if the server is pure Bukkit without Spigot API.
                        plugin.getLogger().warning("Spigot API for action bar not available. Mana bar will not show. Consider using a BossBar as an alternative for pure Bukkit.");
                        this.cancel(); // Stop this task if the API isn't there to prevent further errors.
                        return;
                    } catch (Exception e) {
                        // Catch any other unexpected errors during action bar message sending
                        plugin.getLogger().log(Level.WARNING, "Error sending action bar message to " + player.getName(), e);
                    }
                }
            }
        }.runTaskTimer(plugin, 60L, 20L); // Update action bar every 1 second, after initial 3s delay
    }

    public void scheduleStatsUpdate(Player player) {
        // Adding a small delay can prevent issues with inventory updates not being fully processed.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player != null && player.isOnline()) { // Check if player is still online
                updateAndApplyAllEffects(player);
            }
        }, 2L); // 2-tick delay
    }
}