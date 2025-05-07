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

    // --- UUIDs for Ender Armor HEALTH Bonuses (Vanilla Attribute) ---
    // ** IMPORTANT: Replace these placeholder UUIDs with actual unique UUIDs **
    private static final UUID ENDER_HELMET_HEALTH_BONUS_UUID = UUID.fromString("7a6a2fb6-bedd-4ccb-856e-dd85f78b7cb6");
    private static final UUID ENDER_CHESTPLATE_HEALTH_BONUS_UUID = UUID.fromString("eb367bca-b1e5-41a1-b8cc-ed65e59d84b0");
    private static final UUID ENDER_LEGGINGS_HEALTH_BONUS_UUID = UUID.fromString("9914ed32-fbab-4c18-a2da-7474b9cd5881");
    private static final UUID ENDER_BOOTS_HEALTH_BONUS_UUID = UUID.fromString("011d3c1d-bad6-4321-8cc5-5c0355faae4b");

    private static final Map<EquipmentSlot, UUID> enderHealthBonusUuids = new HashMap<>();

    static {
        enderHealthBonusUuids.put(EquipmentSlot.HEAD, ENDER_HELMET_HEALTH_BONUS_UUID);
        enderHealthBonusUuids.put(EquipmentSlot.CHEST, ENDER_CHESTPLATE_HEALTH_BONUS_UUID);
        enderHealthBonusUuids.put(EquipmentSlot.LEGS, ENDER_LEGGINGS_HEALTH_BONUS_UUID);
        enderHealthBonusUuids.put(EquipmentSlot.FEET, ENDER_BOOTS_HEALTH_BONUS_UUID);
    }


    public PlayerStatsManager(MMOCraft plugin) {
        this.plugin = plugin;
        startManaRegenTask();
        startStatRefreshTask();
        startManaBarUpdateTask();
    }

    public PlayerStats getStats(Player player) {
        if (player == null || !player.isOnline()) {
            return PlayerStats.base();
        }
        return playerStatsCache.computeIfAbsent(player.getUniqueId(), uuid -> PlayerStats.base());
    }

    private void updateStatsFromEquipment(Player player, PlayerStats stats) {
        if (player == null || !player.isOnline() || stats == null) return;

        PlayerInventory inventory = player.getInventory();
        PlayerStats baseDefaults = PlayerStats.base(); // Get fresh base stats for reset

        // Reset current stats to base before recalculating from gear
        stats.setStrength(baseDefaults.getStrength());
        stats.setCritChance(baseDefaults.getCritChance());
        stats.setCritDamage(baseDefaults.getCritDamage());
        stats.setDefense(baseDefaults.getDefense()); // Reset custom defense
        stats.setMaxMana(baseDefaults.getMaxMana());
        stats.setSpeed(baseDefaults.getSpeed());

        // Iterate over armor and main hand item
        ItemStack[] armor = inventory.getArmorContents();
        for (ItemStack item : armor) {
            accumulateStatsFromItem(player, item, stats); // Pass player for world check
        }
        accumulateStatsFromItem(player, inventory.getItemInMainHand(), stats); // Pass player

        stats.setMaxMana(stats.getMaxMana());
        stats.setCurrentMana(stats.getCurrentMana());
    }

    public void updateAndApplyAllEffects(Player player) {
        if (player == null || !player.isOnline()) return;

        PlayerStats stats = getStats(player);
        updateStatsFromEquipment(player, stats); // Recalculate all stats from equipment

        applySpeedModifier(player, stats.getSpeed());
        applyEnderHealthBonus(player); // Apply Ender Armor conditional HEALTH bonus
    }

    // Modified to accept Player for world checking for End Armor Defense bonus
    private void accumulateStatsFromItem(Player player, ItemStack item, PlayerStats stats) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                String itemId = pdc.get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING); // Get item ID for checks

                if (NBTKeys.STRENGTH_KEY != null) stats.setStrength(stats.getStrength() + pdc.getOrDefault(NBTKeys.STRENGTH_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.CRIT_CHANCE_KEY != null) stats.setCritChance(stats.getCritChance() + pdc.getOrDefault(NBTKeys.CRIT_CHANCE_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.CRIT_DAMAGE_KEY != null) stats.setCritDamage(stats.getCritDamage() + pdc.getOrDefault(NBTKeys.CRIT_DAMAGE_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.MANA_KEY != null) stats.setMaxMana(stats.getMaxMana() + pdc.getOrDefault(NBTKeys.MANA_KEY, PersistentDataType.INTEGER, 0));
                if (NBTKeys.SPEED_KEY != null) stats.setSpeed(stats.getSpeed() + pdc.getOrDefault(NBTKeys.SPEED_KEY, PersistentDataType.INTEGER, 0));

                // Handle custom Defense and End Armor 2x bonus for Defense
                if (NBTKeys.DEFENSE_KEY != null && pdc.has(NBTKeys.DEFENSE_KEY, PersistentDataType.INTEGER)) {
                    int itemDefense = pdc.getOrDefault(NBTKeys.DEFENSE_KEY, PersistentDataType.INTEGER, 0);
                    if (itemId != null && itemId.startsWith("ender_") && player.getWorld().getEnvironment() == World.Environment.THE_END) {
                        itemDefense *= 2; // Double custom defense if End Armor piece and in The End
                    }
                    stats.setDefense(stats.getDefense() + itemDefense);
                }
            }
        }
    }

    private void applySpeedModifier(Player player, int speedStatPercentage) {
        AttributeInstance speedAttribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttribute == null) return;

        UUID modifierUUID = UUID.nameUUIDFromBytes((SPEED_MODIFIER_UUID_NAMESPACE + player.getUniqueId().toString()).getBytes());

        removeModifier(speedAttribute, modifierUUID);
        speedModifiers.remove(player.getUniqueId());


        if (speedStatPercentage != 0) {
            double modifierAmount = (double) speedStatPercentage / 100.0;
            AttributeModifier newModifier = new AttributeModifier(
                    modifierUUID,
                    "mmocraft_speed_boost",
                    modifierAmount,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1
            );
            try {
                if (!hasModifier(speedAttribute, modifierUUID)) {
                    speedAttribute.addModifier(newModifier);
                    speedModifiers.put(player.getUniqueId(), newModifier);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Could not apply speed modifier for " + player.getName() + " (UUID: " + modifierUUID +"): " + e.getMessage());
            }
        }
    }

    // This method now ONLY handles the GENERIC_MAX_HEALTH bonus for End Armor
    private void applyEnderHealthBonus(Player player) {
        PlayerInventory inventory = player.getInventory();
        // Ensure armorContents indices match EquipmentSlot order for direct mapping
        ItemStack helmet = inventory.getHelmet();
        ItemStack chestplate = inventory.getChestplate();
        ItemStack leggings = inventory.getLeggings();
        ItemStack boots = inventory.getBoots();
        ItemStack[] armorPieces = {helmet, chestplate, leggings, boots};

        World.Environment environment = player.getWorld().getEnvironment();
        boolean inTheEnd = environment == World.Environment.THE_END;

        String[] enderItemIds = {"ender_helmet", "ender_chestplate", "ender_leggings", "ender_boots"};
        // EquipmentSlot[] equipmentSlots is defined as a class member now

        double[] baseHealthValues = {20.0, 30.0, 25.0, 15.0}; // Corresponds to Helmet, Chest, Legs, Boots

        for (int i = 0; i < armorPieces.length; i++) {
            ItemStack armorPiece = armorPieces[i];
            EquipmentSlot currentEquipmentSlot = equipmentSlots[i]; // Use the class member
            AttributeInstance healthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            UUID currentHealthBonusUuid = enderHealthBonusUuids.get(currentEquipmentSlot);

            removeModifier(healthAttribute, currentHealthBonusUuid); // Always remove old

            if (armorPiece != null && armorPiece.hasItemMeta() && NBTKeys.ITEM_ID_KEY != null) {
                ItemMeta meta = armorPiece.getItemMeta();
                if (meta != null) {
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    String itemId = pdc.get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);

                    if (itemId != null && itemId.equalsIgnoreCase(enderItemIds[i])) {
                        if (inTheEnd) {
                            AttributeModifier healthMod = new AttributeModifier(currentHealthBonusUuid,
                                    "enderBonusHealth_" + currentEquipmentSlot.name(), baseHealthValues[i],
                                    AttributeModifier.Operation.ADD_NUMBER);

                            if (healthAttribute != null && !hasModifier(healthAttribute, currentHealthBonusUuid)) {
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
            try {
                attributeInstance.removeModifier(toRemove);
            } catch (IllegalStateException e) {
                plugin.getLogger().log(Level.FINEST, "Tried to remove modifier that was already gone: " + modifierUuid);
            }
        }
    }

    private boolean hasModifier(AttributeInstance attributeInstance, UUID modifierUuid) {
        if (attributeInstance == null || modifierUuid == null) return false;
        for (AttributeModifier modifier : attributeInstance.getModifiers()) {
            if (modifier.getUniqueId().equals(modifierUuid)) {
                return true;
            }
        }
        return false;
    }

    public void handlePlayerJoin(Player player) {
        getStats(player); // Ensures a PlayerStats object is created
        updateAndApplyAllEffects(player);
    }

    public void handlePlayerQuit(Player player) {
        AttributeInstance speedAttribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttribute != null) {
            AttributeModifier currentModifier = speedModifiers.remove(player.getUniqueId());
            if (currentModifier != null) {
                removeModifier(speedAttribute, currentModifier.getUniqueId());
            }
        }
        // Also remove Ender Armor HEALTH bonuses on quit
        AttributeInstance healthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        for (EquipmentSlot slot : equipmentSlots) {
            if (enderHealthBonusUuids.containsKey(slot)) {
                removeModifier(healthAttribute, enderHealthBonusUuids.get(slot));
            }
        }
        playerStatsCache.remove(player.getUniqueId());
    }

    // Defined at class level for use in handlePlayerQuit and applyEnderHealthBonus
    private static final EquipmentSlot[] equipmentSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};


    private void startManaRegenTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID playerUUID : new ArrayList<>(playerStatsCache.keySet())) { // Iterate over a copy
                    Player player = Bukkit.getPlayer(playerUUID);
                    if (player != null && player.isOnline()) {
                        PlayerStats stats = getStats(player); // Safe get
                        if (stats.getCurrentMana() < stats.getMaxMana()) {
                            int manaToRegen = (int) Math.max(1, stats.getMaxMana() * 0.025);
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
        }.runTaskTimer(plugin, 40L, 20L * 1); // Refresh stats every 1 second
    }

    private void startManaBarUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerStats stats = getStats(player);
                    String manaMessage = ChatColor.AQUA + "Mana: " +
                            ChatColor.DARK_AQUA + stats.getCurrentMana() +
                            ChatColor.AQUA + "/" +
                            ChatColor.DARK_AQUA + stats.getMaxMana();
                    try {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(manaMessage));
                    } catch (NoSuchMethodError e) {
                        plugin.getLogger().warning("Spigot API for action bar not available. Mana bar will not show.");
                        this.cancel(); // Stop this task if the API isn't there
                        return;
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Error sending action bar message to " + player.getName(), e);
                    }
                }
            }
        }.runTaskTimer(plugin, 60L, 20L); // Update action bar every 1 second
    }

    public void scheduleStatsUpdate(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player != null && player.isOnline()) { // Check if player is still online
                updateAndApplyAllEffects(player);
            }
        }, 2L); // 2-tick delay
    }
}

