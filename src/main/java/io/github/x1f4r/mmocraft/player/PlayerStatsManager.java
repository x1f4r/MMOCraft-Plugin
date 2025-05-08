package io.github.x1f4r.mmocraft.player;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerStatsManager {

    private final MMOCraft plugin;
    private final Map<UUID, PlayerStats> playerStatsCache = new HashMap<>();
    private final Map<UUID, AttributeModifier> speedModifiers = new HashMap<>();
    private static final String SPEED_MODIFIER_UUID_NAMESPACE = "mmocraft_speed_modifier_v2";

    // Ender Health Bonus UUIDs remain the same
    private static final UUID ENDER_HELMET_HEALTH_BONUS_UUID = UUID.fromString("7a6a2fb6-bedd-4ccb-856e-dd85f78b7cb6");
    private static final UUID ENDER_CHESTPLATE_HEALTH_BONUS_UUID = UUID.fromString("eb367bca-b1e5-41a1-b8cc-ed65e59d84b0");
    private static final UUID ENDER_LEGGINGS_HEALTH_BONUS_UUID = UUID.fromString("9914ed32-fbab-4c18-a2da-7474b9cd5881");
    private static final UUID ENDER_BOOTS_HEALTH_BONUS_UUID = UUID.fromString("011d3c1d-bad6-4321-8cc5-5c0355faae4b");
    private static final Map<EquipmentSlot, UUID> enderHealthBonusUuids = new HashMap<>();
    private static final EquipmentSlot[] equipmentSlotsArray = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // Regen config remains the same
    private final double healthRegenPercentage = 0.020;
    private final double minHealthRegenAmount = 0.5;
    private final long healthRegenIntervalTicks = 20L;
    private final double manaRegenPercentage = 0.025;
    private final int minManaRegenAmount = 1;
    private final long manaRegenIntervalTicks = 20L;

    static {
        enderHealthBonusUuids.put(EquipmentSlot.HEAD, ENDER_HELMET_HEALTH_BONUS_UUID);
        enderHealthBonusUuids.put(EquipmentSlot.CHEST, ENDER_CHESTPLATE_HEALTH_BONUS_UUID);
        enderHealthBonusUuids.put(EquipmentSlot.LEGS, ENDER_LEGGINGS_HEALTH_BONUS_UUID);
        enderHealthBonusUuids.put(EquipmentSlot.FEET, ENDER_BOOTS_HEALTH_BONUS_UUID);
    }


    public PlayerStatsManager(MMOCraft plugin) {
        this.plugin = plugin;
        startManaRegenTask();
        startHealthRegenTask();
        startStatRefreshTask();
        startManaBarUpdateTask();
    }

    public PlayerStats getStats(Player player) {
        if (player == null || !player.isOnline()) {
            return PlayerStats.base();
        }
        // Ensures a base PlayerStats object exists in the cache for the player
        return playerStatsCache.computeIfAbsent(player.getUniqueId(), uuid -> PlayerStats.base());
    }

    /**
     * Recalculates only the stat contributions from the player's equipment
     * and updates the equipment fields in the PlayerStats object.
     * Base stats remain untouched here.
     */
    private void updateEquipmentContributions(Player player, PlayerStats stats) {
        if (player == null || !player.isOnline() || stats == null) return;

        PlayerInventory inventory = player.getInventory();

        // FIX: Reset EQUIPMENT contributions to 0 using the correct setters
        stats.setEquipmentStrength(0);
        stats.setEquipmentCritChance(0);
        stats.setEquipmentCritDamage(0);
        stats.setEquipmentDefense(0);
        stats.setEquipmentMaxMana(0);
        stats.setEquipmentSpeed(0);
        stats.setEquipmentMiningSpeed(0);
        stats.setEquipmentForagingSpeed(0);
        stats.setEquipmentFishingSpeed(0);
        stats.setEquipmentShootingSpeed(0);

        // Accumulate stats from armor and held items into equipment fields
        ItemStack[] armor = inventory.getArmorContents();
        for (ItemStack item : armor) {
            accumulateEquipmentStatsFromItem(player, item, stats);
        }
        accumulateEquipmentStatsFromItem(player, inventory.getItemInMainHand(), stats);
        // accumulateEquipmentStatsFromItem(player, inventory.getItemInOffHand(), stats); // Optional

        // Clamp current mana against the NEW total max mana (base + equipment) after calculation
        stats.setCurrentMana(stats.getCurrentMana());
    }

    /**
     * Updates the player's stats based on equipment and applies necessary effects
     * like speed modifiers and health attribute changes.
     */
    public void updateAndApplyAllEffects(Player player) {
        if (player == null || !player.isOnline()) return;

        PlayerStats stats = getStats(player); // Get the cached PlayerStats object
        updateEquipmentContributions(player, stats); // Recalculate equipment bonuses

        // Apply effects based on the TOTAL calculated stats (base + equipment)
        applySpeedModifier(player, stats.getSpeed()); // Uses total speed stat
        applyEnderHealthBonus(player); // Handles vanilla health attribute based on Ender armor

        // Ensure player's current health doesn't exceed their max health attribute
        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttribute != null) {
            double newMaxHealth = maxHealthAttribute.getValue();
            if (player.getHealth() > newMaxHealth) {
                player.setHealth(newMaxHealth);
            }
        }
        // Current mana is clamped within updateEquipmentContributions
    }

    /**
     * Reads stats from a single item's NBT and adds them to the
     * appropriate EQUIPMENT bonus fields in the PlayerStats object.
     */
    private void accumulateEquipmentStatsFromItem(Player player, ItemStack item, PlayerStats stats) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                String itemId = pdc.get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);

                // FIX: Add item stats to the corresponding EQUIPMENT bonus fields using correct setters
                if (NBTKeys.STRENGTH_KEY != null) stats.setEquipmentStrength(stats.getEquipmentStrength() + pdc.getOrDefault(NBTKeys.STRENGTH_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.CRIT_CHANCE_KEY != null) stats.setEquipmentCritChance(stats.getEquipmentCritChance() + pdc.getOrDefault(NBTKeys.CRIT_CHANCE_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.CRIT_DAMAGE_KEY != null) stats.setEquipmentCritDamage(stats.getEquipmentCritDamage() + pdc.getOrDefault(NBTKeys.CRIT_DAMAGE_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.MANA_KEY != null) stats.setEquipmentMaxMana(stats.getEquipmentMaxMana() + pdc.getOrDefault(NBTKeys.MANA_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.SPEED_KEY != null) stats.setEquipmentSpeed(stats.getEquipmentSpeed() + pdc.getOrDefault(NBTKeys.SPEED_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.MINING_SPEED_KEY != null) stats.setEquipmentMiningSpeed(stats.getEquipmentMiningSpeed() + pdc.getOrDefault(NBTKeys.MINING_SPEED_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.FORAGING_SPEED_KEY != null) stats.setEquipmentForagingSpeed(stats.getEquipmentForagingSpeed() + pdc.getOrDefault(NBTKeys.FORAGING_SPEED_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.FISHING_SPEED_KEY != null) stats.setEquipmentFishingSpeed(stats.getEquipmentFishingSpeed() + pdc.getOrDefault(NBTKeys.FISHING_SPEED_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.SHOOTING_SPEED_KEY != null) stats.setEquipmentShootingSpeed(stats.getEquipmentShootingSpeed() + pdc.getOrDefault(NBTKeys.SHOOTING_SPEED_KEY, PersistentDataType.INTEGER, 0));

                // Defense with End Amplification logic
                if (NBTKeys.DEFENSE_KEY != null ) {
                    if (pdc.has(NBTKeys.DEFENSE_KEY, PersistentDataType.INTEGER)) {
                        int itemDefense = pdc.getOrDefault(NBTKeys.DEFENSE_KEY, PersistentDataType.INTEGER, 0);
                        if (itemId != null && itemId.startsWith("ender_") && player.getWorld().getEnvironment() == World.Environment.THE_END) {
                            itemDefense *= 2; // End Amplification
                        }
                        // FIX: Use correct setter for equipment defense
                        stats.setEquipmentDefense(stats.getEquipmentDefense() + itemDefense);
                    }
                }
            }
        }
    }

    // --- Speed Modifier Logic (Unchanged) ---
    private void applySpeedModifier(Player player, int speedStatPercentage) {
        AttributeInstance speedAttribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttribute == null) return;
        UUID modifierUUID = UUID.nameUUIDFromBytes((SPEED_MODIFIER_UUID_NAMESPACE + player.getUniqueId().toString()).getBytes());
        removeModifier(speedAttribute, modifierUUID);
        if (speedStatPercentage != 0) {
            double modifierAmount = (double) speedStatPercentage / 100.0;
            AttributeModifier newModifier = new AttributeModifier(modifierUUID, "mmocraft_speed_boost", modifierAmount, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            try {
                if (!hasModifier(speedAttribute, modifierUUID)) {
                    speedAttribute.addModifier(newModifier);
                }
                speedModifiers.put(player.getUniqueId(), newModifier);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Could not apply speed modifier for " + player.getName(), e);
            }
        } else {
            speedModifiers.remove(player.getUniqueId());
        }
    }

    // --- Ender Health Bonus Logic (Unchanged) ---
    private void applyEnderHealthBonus(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] armorPieces = {inventory.getHelmet(), inventory.getChestplate(), inventory.getLeggings(), inventory.getBoots()};
        World.Environment environment = player.getWorld().getEnvironment();
        boolean inTheEnd = environment == World.Environment.THE_END;
        String[] enderItemIds = {"ender_helmet", "ender_chestplate", "ender_leggings", "ender_boots"};
        AttributeInstance healthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthAttribute == null) return;

        for (int i = 0; i < armorPieces.length; i++) {
            ItemStack armorPiece = armorPieces[i];
            EquipmentSlot currentEquipmentSlot = equipmentSlotsArray[i];
            UUID currentHealthBonusUuid = enderHealthBonusUuids.get(currentEquipmentSlot);
            removeModifier(healthAttribute, currentHealthBonusUuid); // Remove existing first

            if (armorPiece != null && armorPiece.hasItemMeta() && NBTKeys.ITEM_ID_KEY != null) {
                ItemMeta meta = armorPiece.getItemMeta();
                if (meta != null) {
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    String itemId = pdc.get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);
                    if (itemId != null && itemId.equalsIgnoreCase(enderItemIds[i])) {
                        double baseBonus = 0;
                        if (meta.hasAttributeModifiers()) {
                            Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(Attribute.GENERIC_MAX_HEALTH);
                            if (modifiers != null) {
                                for (AttributeModifier mod : modifiers) {
                                    if ((mod.getSlot() == null || mod.getSlot() == currentEquipmentSlot) && mod.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                                        baseBonus += mod.getAmount();
                                    }
                                }
                            }
                        }
                        if (baseBonus > 0) {
                            double effectiveBonus = inTheEnd ? baseBonus * 2 : baseBonus;
                            AttributeModifier healthMod = new AttributeModifier(currentHealthBonusUuid, "enderBonusHealth_" + currentEquipmentSlot.name(), effectiveBonus, AttributeModifier.Operation.ADD_NUMBER);
                            if (!hasModifier(healthAttribute, currentHealthBonusUuid)) {
                                healthAttribute.addModifier(healthMod);
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Modifier Helper Methods (Unchanged) ---
    private void removeModifier(AttributeInstance attributeInstance, UUID modifierUuid) {
        if (attributeInstance == null || modifierUuid == null) return;
        AttributeModifier toRemove = null;
        for (AttributeModifier modifier : attributeInstance.getModifiers()) {
            if (modifier.getUniqueId().equals(modifierUuid)) {
                toRemove = modifier;
                break;
            }
        }
        if (toRemove != null) {
            try { attributeInstance.removeModifier(toRemove); } catch (IllegalStateException e) {}
        }
    }
    private boolean hasModifier(AttributeInstance attributeInstance, UUID modifierUuid) {
        if (attributeInstance == null || modifierUuid == null) return false;
        for (AttributeModifier modifier : attributeInstance.getModifiers()) {
            if (modifier.getUniqueId().equals(modifierUuid)) return true;
        }
        return false;
    }

    // --- Player Join/Quit Logic (Unchanged) ---
    public void handlePlayerJoin(Player player) {
        getStats(player);
        updateAndApplyAllEffects(player);
    }
    public void handlePlayerQuit(Player player) {
        AttributeInstance speedAttribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttribute != null) {
            AttributeModifier currentSpeedModifier = speedModifiers.remove(player.getUniqueId());
            if (currentSpeedModifier != null) removeModifier(speedAttribute, currentSpeedModifier.getUniqueId());
        }
        AttributeInstance healthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthAttribute != null) {
            for (EquipmentSlot slot : equipmentSlotsArray) {
                if (enderHealthBonusUuids.containsKey(slot)) removeModifier(healthAttribute, enderHealthBonusUuids.get(slot));
            }
        }
        playerStatsCache.remove(player.getUniqueId());
    }

    // --- Regen Tasks (Unchanged) ---
    private void startManaRegenTask() {
        new BukkitRunnable() {
            @Override public void run() {
                for (UUID playerUUID : new ArrayList<>(playerStatsCache.keySet())) {
                    Player player = Bukkit.getPlayer(playerUUID);
                    if (player != null && player.isOnline() && !player.isDead()) {
                        PlayerStats stats = getStats(player);
                        if (stats.getCurrentMana() < stats.getMaxMana()) {
                            int manaToRegen = (int) Math.max(minManaRegenAmount, stats.getMaxMana() * manaRegenPercentage);
                            stats.addMana(manaToRegen);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, manaRegenIntervalTicks, manaRegenIntervalTicks);
    }
    private void startHealthRegenTask() {
        new BukkitRunnable() {
            @Override public void run() {
                for (UUID playerUUID : new ArrayList<>(playerStatsCache.keySet())) {
                    Player player = Bukkit.getPlayer(playerUUID);
                    if (player != null && player.isOnline() && !player.isDead()) {
                        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                        if (maxHealthAttribute == null) continue;
                        double currentMaxHealth = maxHealthAttribute.getValue();
                        double currentHealth = player.getHealth();
                        if (currentHealth < currentMaxHealth && !player.hasPotionEffect(PotionEffectType.WITHER) && !player.hasPotionEffect(PotionEffectType.POISON)) {
                            double healthToRegen = Math.max(minHealthRegenAmount, currentMaxHealth * healthRegenPercentage);
                            player.setHealth(Math.min(currentMaxHealth, currentHealth + healthToRegen));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, healthRegenIntervalTicks, healthRegenIntervalTicks);
    }

    // --- Stat Refresh Task (Unchanged) ---
    private void startStatRefreshTask() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) updateAndApplyAllEffects(player);
            }
        }.runTaskTimer(plugin, 40L, 20L);
    }

    // --- Mana Bar Update Task (Unchanged) ---
    private void startManaBarUpdateTask() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerStats stats = getStats(player);
                    String manaMessage = ChatColor.AQUA + "Mana: " + ChatColor.DARK_AQUA + stats.getCurrentMana() + ChatColor.AQUA + "/" + ChatColor.DARK_AQUA + stats.getMaxMana();
                    try { player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(manaMessage)); } catch (Exception e) {}
                }
            }
        }.runTaskTimer(plugin, 60L, 20L);
    }

    // --- Schedule Update Method (Unchanged) ---
    public void scheduleStatsUpdate(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player != null && player.isOnline()) updateAndApplyAllEffects(player);
        }, 2L);
    }
}
