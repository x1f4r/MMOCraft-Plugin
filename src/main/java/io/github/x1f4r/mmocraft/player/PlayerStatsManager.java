package io.github.x1f4r.mmocraft.player;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import io.github.x1f4r.mmocraft.stats.PlayerStats;
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
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayerStatsManager {

    private final MMOCore core;
    private final MMOPlugin plugin;
    private final Logger log;
    private final Map<UUID, PlayerStats> playerStatsCache = new HashMap<>();
    private final Map<UUID, BukkitTask> statUpdateTasks = new HashMap<>();

    private static final String MMO_SPEED_MODIFIER_NAME = "mmocraft_speed_bonus";
    private static final UUID SPEED_MODIFIER_UUID_SALT = UUID.fromString("a1f910c1-8e1a-476b-97a0-f2ae903684e0");

    private static final UUID ENDER_HELMET_HEALTH_BONUS_UUID = UUID.fromString("7a6a2fb6-bedd-4ccb-856e-dd85f78b7cb6");
    private static final UUID ENDER_CHESTPLATE_HEALTH_BONUS_UUID = UUID.fromString("eb367bca-b1e5-41a1-b8cc-ed65e59d84b0");
    private static final UUID ENDER_LEGGINGS_HEALTH_BONUS_UUID = UUID.fromString("9914ed32-fbab-4c18-a2da-7474b9cd5881");
    private static final UUID ENDER_BOOTS_HEALTH_BONUS_UUID = UUID.fromString("011d3c1d-bad6-4321-8cc5-5c0355faae4b");
    private static final Map<EquipmentSlot, UUID> enderHealthBonusUuids = new HashMap<>();
    private static final EquipmentSlot[] ARMOR_SLOTS_ARRAY = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final double healthRegenPercentage;
    private final double minHealthRegenAmount;
    private final long healthRegenIntervalTicks;
    private final double manaRegenPercentage;
    private final int minManaRegenAmount;
    private final long manaRegenIntervalTicks;

    static {
        enderHealthBonusUuids.put(EquipmentSlot.HEAD, ENDER_HELMET_HEALTH_BONUS_UUID);
        enderHealthBonusUuids.put(EquipmentSlot.CHEST, ENDER_CHESTPLATE_HEALTH_BONUS_UUID);
        enderHealthBonusUuids.put(EquipmentSlot.LEGS, ENDER_LEGGINGS_HEALTH_BONUS_UUID);
        enderHealthBonusUuids.put(EquipmentSlot.FEET, ENDER_BOOTS_HEALTH_BONUS_UUID);
    }

    public PlayerStatsManager(MMOCore core) {
        this.core = core;
        this.plugin = core.getPlugin();
        this.log = MMOPlugin.getMMOLogger();

        this.healthRegenPercentage = plugin.getConfig().getDouble("player.regen.health.percentage", 0.02);
        this.minHealthRegenAmount = plugin.getConfig().getDouble("player.regen.health.min_amount", 0.5);
        this.healthRegenIntervalTicks = plugin.getConfig().getLong("player.regen.health.interval_ticks", 40L);
        this.manaRegenPercentage = plugin.getConfig().getDouble("player.regen.mana.percentage", 0.025);
        this.minManaRegenAmount = plugin.getConfig().getInt("player.regen.mana.min_amount", 1);
        this.manaRegenIntervalTicks = plugin.getConfig().getLong("player.regen.mana.interval_ticks", 20L);
    }

    public void initialize() {
        startHealthRegenTask();
        startManaRegenTask();
        startManaBarUpdateTask();
        log.info("PlayerStatsManager initialized and tasks started.");
        Bukkit.getOnlinePlayers().forEach(this::handlePlayerJoin); // Process already online players
    }

    public PlayerStats getStats(Player player) {
        if (player == null) return PlayerStats.createDefault();
        return playerStatsCache.computeIfAbsent(player.getUniqueId(), uuid -> {
            PlayerStats newStats = PlayerStats.createDefault();
            // Initial update for newly created stats object
             Bukkit.getScheduler().runTaskLater(plugin, () -> {
                 if (player.isOnline()) updateAndApplyAllEffects(player, newStats);
            }, 1L);
            return newStats;
        });
    }

    public void scheduleStatsUpdate(Player player) {
        if (player == null || !player.isOnline()) return;
        BukkitTask existingTask = statUpdateTasks.remove(player.getUniqueId());
        if (existingTask != null) existingTask.cancel();

        BukkitTask newTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                PlayerStats stats = getStats(player);
                updateAndApplyAllEffects(player, stats);
            }
            statUpdateTasks.remove(player.getUniqueId());
        }, 2L);
        statUpdateTasks.put(player.getUniqueId(), newTask);
    }

    private void updateAndApplyAllEffects(Player player, PlayerStats stats) {
        if (player == null || !player.isOnline() || stats == null) return;

        stats.setEquipmentStrength(0);
        stats.setEquipmentCritChance(0);
        stats.setEquipmentCritDamage(0);
        stats.setEquipmentMaxMana(0);
        stats.setEquipmentDefense(0);
        stats.setEquipmentSpeed(0);
        stats.setEquipmentMiningSpeed(0);
        stats.setEquipmentForagingSpeed(0);
        stats.setEquipmentFishingSpeed(0);
        stats.setEquipmentShootingSpeed(0);

        PlayerInventory inventory = player.getInventory();
        accumulateEquipmentStatsFromItem(player, inventory.getItemInMainHand(), stats);
        for (ItemStack armorPiece : inventory.getArmorContents()) {
            accumulateEquipmentStatsFromItem(player, armorPiece, stats);
        }

        applySpeedModifier(player, stats.getSpeed());
        applyEnderArmorHealthBonus(player);
        stats.setCurrentMana(stats.getCurrentMana());

        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttribute != null && player.getHealth() > maxHealthAttribute.getValue()) {
            player.setHealth(maxHealthAttribute.getValue());
        }
    }

    private void accumulateEquipmentStatsFromItem(Player player, ItemStack item, PlayerStats stats) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemId = pdc.get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);

        stats.setEquipmentStrength(stats.getEquipmentStrength() + pdc.getOrDefault(NBTKeys.STRENGTH_KEY, PersistentDataType.INTEGER, 0));
        stats.setEquipmentCritChance(stats.getEquipmentCritChance() + pdc.getOrDefault(NBTKeys.CRIT_CHANCE_KEY, PersistentDataType.INTEGER, 0));
        stats.setEquipmentCritDamage(stats.getEquipmentCritDamage() + pdc.getOrDefault(NBTKeys.CRIT_DAMAGE_KEY, PersistentDataType.INTEGER, 0));
        stats.setEquipmentMaxMana(stats.getEquipmentMaxMana() + pdc.getOrDefault(NBTKeys.MANA_KEY, PersistentDataType.INTEGER, 0));
        stats.setEquipmentSpeed(stats.getEquipmentSpeed() + pdc.getOrDefault(NBTKeys.SPEED_KEY, PersistentDataType.INTEGER, 0));
        stats.setEquipmentMiningSpeed(stats.getEquipmentMiningSpeed() + pdc.getOrDefault(NBTKeys.MINING_SPEED_KEY, PersistentDataType.INTEGER, 0));
        stats.setEquipmentForagingSpeed(stats.getEquipmentForagingSpeed() + pdc.getOrDefault(NBTKeys.FORAGING_SPEED_KEY, PersistentDataType.INTEGER, 0));
        stats.setEquipmentFishingSpeed(stats.getEquipmentFishingSpeed() + pdc.getOrDefault(NBTKeys.FISHING_SPEED_KEY, PersistentDataType.INTEGER, 0));
        stats.setEquipmentShootingSpeed(stats.getEquipmentShootingSpeed() + pdc.getOrDefault(NBTKeys.SHOOTING_SPEED_KEY, PersistentDataType.INTEGER, 0));

        int itemDefense = pdc.getOrDefault(NBTKeys.DEFENSE_KEY, PersistentDataType.INTEGER, 0);
        if (itemId != null && itemId.startsWith("ender_") && player.getWorld().getEnvironment() == World.Environment.THE_END) {
            itemDefense *= 2;
        }
        stats.setEquipmentDefense(stats.getEquipmentDefense() + itemDefense);
    }

    private void applySpeedModifier(Player player, int totalSpeedStatPercentage) {
        AttributeInstance speedAttribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttribute == null) return;
        UUID playerSpecificModifierUUID = UUID.nameUUIDFromBytes((player.getUniqueId().toString() + SPEED_MODIFIER_UUID_SALT.toString()).getBytes());
        removeModifierByUUID(speedAttribute, playerSpecificModifierUUID);

        if (totalSpeedStatPercentage != 0) {
            double modifierValue = totalSpeedStatPercentage / 100.0;
            AttributeModifier speedModifier = new AttributeModifier(playerSpecificModifierUUID, MMO_SPEED_MODIFIER_NAME, modifierValue, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            speedAttribute.addModifier(speedModifier);
        }
    }

    private void applyEnderArmorHealthBonus(Player player) {
        AttributeInstance healthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthAttribute == null) return;
        PlayerInventory inventory = player.getInventory();
        boolean inTheEnd = player.getWorld().getEnvironment() == World.Environment.THE_END;

        for (EquipmentSlot slot : ARMOR_SLOTS_ARRAY) {
            UUID modifierUuid = enderHealthBonusUuids.get(slot);
            removeModifierByUUID(healthAttribute, modifierUuid);
            ItemStack armorPiece = null;
            String specificExpectedItemId = "";

            switch (slot) {
                case HEAD: armorPiece = inventory.getHelmet(); specificExpectedItemId = "ender_helmet"; break;
                case CHEST: armorPiece = inventory.getChestplate(); specificExpectedItemId = "ender_chestplate"; break;
                case LEGS: armorPiece = inventory.getLeggings(); specificExpectedItemId = "ender_leggings"; break;
                case FEET: armorPiece = inventory.getBoots(); specificExpectedItemId = "ender_boots"; break;
                default: continue;
            }

            if (armorPiece != null && armorPiece.hasItemMeta()) {
                ItemMeta meta = armorPiece.getItemMeta();
                if (meta != null) {
                    String itemId = meta.getPersistentDataContainer().get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);
                    if (itemId != null && itemId.equalsIgnoreCase(specificExpectedItemId)) {
                        double baseBonusFromItemAttribute = 0;
                        if (meta.hasAttributeModifiers()) {
                            Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(Attribute.GENERIC_MAX_HEALTH);
                            if (modifiers != null) {
                                for (AttributeModifier mod : modifiers) {
                                    if (mod.getOperation() == AttributeModifier.Operation.ADD_NUMBER && (mod.getSlot() == null || mod.getSlot() == slot)) {
                                        baseBonusFromItemAttribute += mod.getAmount();
                                    }
                                }
                            }
                        }
                        if (baseBonusFromItemAttribute > 0) {
                            double effectiveBonus = inTheEnd ? baseBonusFromItemAttribute * 2 : baseBonusFromItemAttribute;
                            AttributeModifier healthMod = new AttributeModifier(modifierUuid, "mmo_ender_health_" + slot.name(), effectiveBonus, AttributeModifier.Operation.ADD_NUMBER);
                            healthAttribute.addModifier(healthMod);
                        }
                    }
                }
            }
        }
    }

    private void removeModifierByUUID(AttributeInstance attributeInstance, UUID modifierUuid) {
        if (attributeInstance == null || modifierUuid == null) return;
        AttributeModifier toRemove = null;
        for (AttributeModifier modifier : attributeInstance.getModifiers()) {
            if (modifier.getUniqueId().equals(modifierUuid)) {
                toRemove = modifier;
                break;
            }
        }
        if (toRemove != null) {
            try { attributeInstance.removeModifier(toRemove); }
            catch (Exception e) { /* ignore */ }
        }
    }

    public void handlePlayerJoin(Player player) {
        getStats(player); // Ensures stats object is created and initial update might be triggered
    }

    public void handlePlayerQuit(Player player) {
        AttributeInstance speedAttr = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) removeModifierByUUID(speedAttr, UUID.nameUUIDFromBytes((player.getUniqueId().toString() + SPEED_MODIFIER_UUID_SALT.toString()).getBytes()));
        AttributeInstance healthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthAttr != null) {
            for (UUID modUuid : enderHealthBonusUuids.values()) removeModifierByUUID(healthAttr, modUuid);
        }
        playerStatsCache.remove(player.getUniqueId());
        BukkitTask existingTask = statUpdateTasks.remove(player.getUniqueId());
        if (existingTask != null) existingTask.cancel();
    }

    private void startHealthRegenTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.isDead()) continue;
                    AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    if (maxHealthAttr == null) continue;
                    double currentMaxHealth = maxHealthAttr.getValue();
                    double currentHealth = player.getHealth();
                    if (currentHealth < currentMaxHealth && !player.hasPotionEffect(PotionEffectType.WITHER) && !player.hasPotionEffect(PotionEffectType.POISON)) {
                        double healthToRegen = Math.max(minHealthRegenAmount, currentMaxHealth * healthRegenPercentage);
                        player.setHealth(Math.min(currentMaxHealth, currentHealth + healthToRegen));
                    }
                }
            }
        }.runTaskTimer(plugin, healthRegenIntervalTicks, healthRegenIntervalTicks);
    }

    private void startManaRegenTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.isDead()) continue;
                    PlayerStats stats = getStats(player);
                    if (stats.getCurrentMana() < stats.getMaxMana()) {
                        int manaToRegen = (int) Math.max(minManaRegenAmount, stats.getMaxMana() * manaRegenPercentage);
                        stats.addMana(manaToRegen);
                    }
                }
            }
        }.runTaskTimer(plugin, manaRegenIntervalTicks, manaRegenIntervalTicks);
    }

    private void startManaBarUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.isDead()) continue;
                    PlayerStats stats = getStats(player);
                    AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    double currentHealth = player.getHealth();
                    double currentMaxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
                    String healthStr = String.format("%.0f", currentHealth);
                    String maxHealthStr = String.format("%.0f", currentMaxHealth);
                    String actionBarMsg = ChatColor.RED + "HP: " + healthStr + "/" + maxHealthStr + ChatColor.DARK_GRAY + " | " + ChatColor.AQUA + "Mana: " + stats.getCurrentMana() + "/" + stats.getMaxMana();
                    try {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionBarMsg));
                    } catch (Exception e) {
                        log.log(Level.FINEST, "Could not send action bar to " + player.getName() + " (Spigot API might not be available for this component)", e);
                         try { player.sendActionBar(actionBarMsg); } catch (Exception e2) {log.log(Level.FINEST, "Could not send action bar to " + player.getName() + " (Paper API fallback also failed)", e2); }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
}

