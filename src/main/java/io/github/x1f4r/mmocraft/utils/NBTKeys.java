package io.github.x1f4r.mmocraft.utils;

import io.github.x1f4r.mmocraft.core.MMOPlugin;
import org.bukkit.NamespacedKey;
import java.util.logging.Logger;

public final class NBTKeys {

    private static MMOPlugin pluginInstance;
    private static Logger log;

    public static NamespacedKey ITEM_ID_KEY;
    public static NamespacedKey MOB_TYPE_KEY;
    public static NamespacedKey STRENGTH_KEY;
    public static NamespacedKey CRIT_CHANCE_KEY;
    public static NamespacedKey CRIT_DAMAGE_KEY;
    public static NamespacedKey MANA_KEY;
    public static NamespacedKey MANA_COST_KEY;
    public static NamespacedKey SPEED_KEY;
    public static NamespacedKey DEFENSE_KEY;
    public static NamespacedKey TRUE_DAMAGE_FLAG_KEY;
    public static NamespacedKey TREE_BOW_POWER_KEY;
    public static NamespacedKey TREE_BOW_MAGICAL_AMMO_KEY;
    public static NamespacedKey PROJECTILE_DAMAGE_MULTIPLIER_KEY;
    public static NamespacedKey PROJECTILE_SOURCE_ITEM_ID_KEY;
    public static NamespacedKey MINING_SPEED_KEY;
    public static NamespacedKey FORAGING_SPEED_KEY;
    public static NamespacedKey FISHING_SPEED_KEY;
    public static NamespacedKey SHOOTING_SPEED_KEY;
    public static NamespacedKey INSTANT_SHOOT_BOW_TAG;
    public static NamespacedKey ABILITY_ID_KEY;
    public static NamespacedKey ABILITY_COOLDOWN_KEY;
    public static NamespacedKey ABILITY_DAMAGE_KEY;
    public static NamespacedKey UTILITY_ID_KEY;
    public static NamespacedKey COMPACTOR_FILTER_ITEM_KEY_PREFIX;
    public static NamespacedKey COMPACTOR_SLOT_COUNT_KEY;

    public static void init(MMOPlugin plugin) {
        if (NBTKeys.pluginInstance != null) {
            MMOPlugin.getMMOLogger().warning("NBTKeys already initialized!");
            return;
        }
        NBTKeys.pluginInstance = plugin;
        NBTKeys.log = MMOPlugin.getMMOLogger();

        ITEM_ID_KEY = createKey("item_id");
        MOB_TYPE_KEY = createKey("mob_type");
        STRENGTH_KEY = createKey("strength");
        CRIT_CHANCE_KEY = createKey("crit_chance");
        CRIT_DAMAGE_KEY = createKey("crit_damage");
        MANA_KEY = createKey("mana_stat");
        MANA_COST_KEY = createKey("mana_cost");
        SPEED_KEY = createKey("speed");
        DEFENSE_KEY = createKey("defense");
        TRUE_DAMAGE_FLAG_KEY = createKey("true_damage_flag");
        TREE_BOW_POWER_KEY = createKey("tree_bow_power");
        TREE_BOW_MAGICAL_AMMO_KEY = createKey("tree_bow_magical_ammo");
        PROJECTILE_DAMAGE_MULTIPLIER_KEY = createKey("projectile_damage_multiplier");
        PROJECTILE_SOURCE_ITEM_ID_KEY = createKey("projectile_source_item_id");
        MINING_SPEED_KEY = createKey("mining_speed");
        FORAGING_SPEED_KEY = createKey("foraging_speed");
        FISHING_SPEED_KEY = createKey("fishing_speed");
        SHOOTING_SPEED_KEY = createKey("shooting_speed");
        INSTANT_SHOOT_BOW_TAG = createKey("instant_shoot_bow_tag");
        ABILITY_ID_KEY = createKey("ability_id");
        ABILITY_COOLDOWN_KEY = createKey("ability_cooldown");
        ABILITY_DAMAGE_KEY = createKey("ability_damage");
        UTILITY_ID_KEY = createKey("utility_id");
        // COMPACTOR_FILTER_ITEM_KEY_PREFIX is just a string, not a full NamespacedKey yet.
        // We'll construct the full key dynamically in CompactorGUIListener like: createKey(COMPACTOR_FILTER_ITEM_KEY_PREFIX_STRING + slotNumber)
        COMPACTOR_SLOT_COUNT_KEY = createKey("compactor_slot_count");

        log.info("NBTKeys initialized.");
    }

    private static NamespacedKey createKey(String keyName) {
        if (pluginInstance == null) {
            throw new IllegalStateException("NBTKeys.init() must be called before creating keys!");
        }
        return new NamespacedKey(pluginInstance, "mmo_" + keyName);
    }

    // Helper method to create a NamespacedKey for a compactor filter slot
    public static NamespacedKey getCompactorFilterKey(int slotNumber) {
        if (pluginInstance == null) {
            throw new IllegalStateException("NBTKeys.init() must be called before creating keys!");
        }
        return new NamespacedKey(pluginInstance, "mmo_compactor_filter_" + slotNumber);
    }

    private NBTKeys() {
        throw new IllegalStateException("Utility class.");
    }
}

