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

    private static final UUID ENDER_HELMET_HEALTH_BONUS_UUID = UUID.fromString("7a6a2fb6-bedd-4ccb-856e-dd85f78b7cb6");
    private static final UUID ENDER_CHESTPLATE_HEALTH_BONUS_UUID = UUID.fromString("eb367bca-b1e5-41a1-b8cc-ed65e59d84b0");
    private static final UUID ENDER_LEGGINGS_HEALTH_BONUS_UUID = UUID.fromString("9914ed32-fbab-4c18-a2da-7474b9cd5881");
    private static final UUID ENDER_BOOTS_HEALTH_BONUS_UUID = UUID.fromString("011d3c1d-bad6-4321-8cc5-5c0355faae4b");
    private static final Map<EquipmentSlot, UUID> enderHealthBonusUuids = new HashMap<>();
    private static final EquipmentSlot[] equipmentSlotsArray = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // MODIFIED: New UUID for health capping. Replace placeholder with a real UUID.
    private static final UUID HEALTH_CAP_MODIFIER_UUID = UUID.fromString("YOUR_UNIQUE_UUID_HERE_HEALTH_CAP");


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
        if (HEALTH_CAP_MODIFIER_UUID.toString().equals("YOUR_UNIQUE_UUID_HERE_HEALTH_CAP")) {
            plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            plugin.getLogger().warning("WARNING: HEALTH_CAP_MODIFIER_UUID is using a placeholder value!");
            plugin.getLogger().warning("Please generate a unique UUID and update it in PlayerStatsManager.java.");
            plugin.getLogger().warning("You can use your AutoUUIDReplace.ps1 script if configured for it.");
            plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
    }

    public PlayerStats getStats(Player player) {
        if (player == null || !player.isOnline()) {
            return PlayerStats.base();
        }
        return playerStatsCache.computeIfAbsent(player.getUniqueId(), uuid -> PlayerStats.base());
    }

    private void updateEquipmentContributions(Player player, PlayerStats stats) {
        if (player == null || !player.isOnline() || stats == null) return;

        PlayerInventory inventory = player.getInventory();

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

        ItemStack[] armor = inventory.getArmorContents();
        for (ItemStack item : armor) {
            accumulateEquipmentStatsFromItem(player, item, stats);
        }
        accumulateEquipmentStatsFromItem(player, inventory.getItemInMainHand(), stats);
        // accumulateEquipmentStatsFromItem(player, inventory.getItemInOffHand(), stats); // Optional

        stats.setCurrentMana(stats.getCurrentMana());
    }

    /**
     * Updates the player's stats based on equipment and applies necessary effects
     * like speed modifiers, health attribute changes, and health-to-absorption conversion.
     */
    public void updateAndApplyAllEffects(Player player) {
        if (player == null || !player.isOnline()) return;

        PlayerStats stats = getStats(player);
        updateEquipmentContributions(player, stats);

        applySpeedModifier(player, stats.getSpeed());
        applyEnderHealthBonus(player); // Applies health bonuses from Ender armor

        // --- Health to Absorption Logic ---
        AttributeInstance healthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthAttribute != null) {
            // Remove our specific health cap modifier first to get the true potential max health
            // from all other sources (base, Ender armor, etc.)
            removeModifier(healthAttribute, HEALTH_CAP_MODIFIER_UUID);

            double currentPotentialMaxHealth = healthAttribute.getValue();
            double healthThreshold = 40.0; // Max 2 rows of red hearts (20 hearts * 2 HP/heart)
            double maxAbsorptionFromConversion = 20.0; // Max 1 row of yellow hearts (10 hearts * 2 HP/heart)

            if (currentPotentialMaxHealth > healthThreshold) {
                double healthToReduceToReachThreshold = currentPotentialMaxHealth - healthThreshold;

                AttributeModifier capModifier = new AttributeModifier(
                        HEALTH_CAP_MODIFIER_UUID,
                        "MMOCraftHealthCap",
                        -healthToReduceToReachThreshold, // Negative modifier to bring health down to the threshold
                        AttributeModifier.Operation.ADD_NUMBER
                );

                // Apply the capping modifier (ensuring it's not duplicated if already present for some reason)
                if (!hasModifier(healthAttribute, HEALTH_CAP_MODIFIER_UUID)) {
                    healthAttribute.addModifier(capModifier);
                }

                // Calculate absorption to give (excess health, up to the defined max absorption)
                double absorptionToApply = Math.min(healthToReduceToReachThreshold, maxAbsorptionFromConversion);
                player.setAbsorptionAmount(absorptionToApply);

            } else {
                // Health is at or below the threshold.
                // Our HEALTH_CAP_MODIFIER_UUID should have already been removed (or was never applied this tick).
                // We do not explicitly remove absorption here; absorption from other sources
                // (like golden apples) or absorption that was previously granted by this system
                // will persist and decay naturally according to vanilla mechanics.
                // If the player previously had absorption from this system and their max health
                // drops below the threshold (e.g. removing armor), they keep the absorption
                // until it's used or decays.
            }

            // Ensure player's current health doesn't exceed their new (potentially capped) max health attribute
            double newActualMaxHealth = healthAttribute.getValue();
            if (player.getHealth() > newActualMaxHealth) {
                player.setHealth(newActualMaxHealth);
            }
        }
        // Current mana is clamped within PlayerStats object via its setters and getMaxMana()
    }


    private void accumulateEquipmentStatsFromItem(Player player, ItemStack item, PlayerStats stats) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                String itemId = pdc.get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);

                if (NBTKeys.STRENGTH_KEY != null) stats.setEquipmentStrength(stats.getEquipmentStrength() + pdc.getOrDefault(NBTKeys.STRENGTH_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.CRIT_CHANCE_KEY != null) stats.setEquipmentCritChance(stats.getEquipmentCritChance() + pdc.getOrDefault(NBTKeys.CRIT_CHANCE_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.CRIT_DAMAGE_KEY != null) stats.setEquipmentCritDamage(stats.getEquipmentCritDamage() + pdc.getOrDefault(NBTKeys.CRIT_DAMAGE_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.MANA_KEY != null) stats.setEquipmentMaxMana(stats.getEquipmentMaxMana() + pdc.getOrDefault(NBTKeys.MANA_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.SPEED_KEY != null) stats.setEquipmentSpeed(stats.getEquipmentSpeed() + pdc.getOrDefault(NBTKeys.SPEED_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.MINING_SPEED_KEY != null) stats.setEquipmentMiningSpeed(stats.getEquipmentMiningSpeed() + pdc.getOrDefault(NBTKeys.MINING_SPEED_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.FORAGING_SPEED_KEY != null) stats.setEquipmentForagingSpeed(stats.getEquipmentForagingSpeed() + pdc.getOrDefault(NBTKeys.FORAGING_SPEED_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.FISHING_SPEED_KEY != null) stats.setEquipmentFishingSpeed(stats.getEquipmentFishingSpeed() + pdc.getOrDefault(NBTKeys.FISHING_SPEED_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.SHOOTING_SPEED_KEY != null) stats.setEquipmentShootingSpeed(stats.getEquipmentShootingSpeed() + pdc.getOrDefault(NBTKeys.SHOOTING_SPEED_KEY, PersistentDataType.INTEGER, 0));

                if (NBTKeys.DEFENSE_KEY != null ) {
                    if (pdc.has(NBTKeys.DEFENSE_KEY, PersistentDataType.INTEGER)) {
                        int itemDefense = pdc.getOrDefault(NBTKeys.DEFENSE_KEY, PersistentDataType.INTEGER, 0);
                        if (itemId != null && itemId.startsWith("ender_") && player.getWorld().getEnvironment() == World.Environment.THE_END) {
                            itemDefense *= 2;
                        }
                        stats.setEquipmentDefense(stats.getEquipmentDefense() + itemDefense);
                    }
                }
            }
        }
    }

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
            removeModifier(healthAttribute, currentHealthBonusUuid);

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

    public void handlePlayerJoin(Player player) {
        getStats(player); // Ensures stats object is created
        scheduleStatsUpdate(player); // Apply all effects after a short delay
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
            // Also remove the health cap modifier on quit
            removeModifier(healthAttribute, HEALTH_CAP_MODIFIER_UUID);
        }
        playerStatsCache.remove(player.getUniqueId());
    }

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

    private void startStatRefreshTask() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) updateAndApplyAllEffects(player);
            }
        }.runTaskTimer(plugin, 40L, 20L); // Refresh every 2 seconds (40 ticks), then every 1 sec (20 ticks)
    }

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

    public void scheduleStatsUpdate(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player != null && player.isOnline()) updateAndApplyAllEffects(player);
        }, 2L); // 2 ticks delay
    }
}