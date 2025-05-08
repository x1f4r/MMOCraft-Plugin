package io.github.x1f4r.mmocraft.utils;

import io.github.x1f4r.mmocraft.MMOCraft;
import org.bukkit.NamespacedKey;

public final class NBTKeys {

    private static MMOCraft plugin;

    // Item Identification
    public static NamespacedKey ITEM_ID_KEY;

    // Mob Identification
    public static NamespacedKey MOB_TYPE_KEY;

    // Custom Combat/Resource Stats
    public static NamespacedKey STRENGTH_KEY;
    public static NamespacedKey CRIT_CHANCE_KEY;
    public static NamespacedKey CRIT_DAMAGE_KEY;
    public static NamespacedKey MANA_KEY; // General mana stat, can be max_mana bonus or mana_cost
    public static NamespacedKey MANA_COST_KEY; // Specifically for ability mana costs on items
    public static NamespacedKey SPEED_KEY; // Player movement speed
    public static NamespacedKey DEFENSE_KEY;

    // Damage Flags
    public static NamespacedKey TRUE_DAMAGE_FLAG_KEY;

    // Tree Bow Specific Keys (Original)
    public static NamespacedKey TREE_BOW_POWER_KEY;
    public static NamespacedKey TREE_BOW_MAGICAL_AMMO_KEY;

    // Projectile Specific Keys
    public static NamespacedKey PROJECTILE_DAMAGE_MULTIPLIER_KEY;
    public static NamespacedKey PROJECTILE_SOURCE_BOW_TYPE_KEY;

    // Custom Tool Speed Stats
    public static NamespacedKey MINING_SPEED_KEY;
    public static NamespacedKey FORAGING_SPEED_KEY;
    public static NamespacedKey FISHING_SPEED_KEY;
    public static NamespacedKey SHOOTING_SPEED_KEY;  // Affects bow projectile velocity AND fire rate for instant bows

    // Bow Mechanic Tags
    public static NamespacedKey INSTANT_SHOOT_BOW_TAG; // Tag for bows that shoot instantly on right click

    public static void init(MMOCraft pluginInstance) {
        if (NBTKeys.plugin != null) {
            return; // Already initialized
        }
        NBTKeys.plugin = pluginInstance;

        ITEM_ID_KEY = new NamespacedKey(plugin, "mmo_item_id");
        MOB_TYPE_KEY = new NamespacedKey(plugin, "mmo_mob_type");

        STRENGTH_KEY = new NamespacedKey(plugin, "mmo_strength");
        CRIT_CHANCE_KEY = new NamespacedKey(plugin, "mmo_crit_chance");
        CRIT_DAMAGE_KEY = new NamespacedKey(plugin, "mmo_crit_damage");
        MANA_KEY = new NamespacedKey(plugin, "mmo_mana_stat"); // Used for MAX_MANA bonus from items
        MANA_COST_KEY = new NamespacedKey(plugin, "mmo_mana_cost"); // Used for ability costs on items
        SPEED_KEY = new NamespacedKey(plugin, "mmo_speed");
        DEFENSE_KEY = new NamespacedKey(plugin, "mmo_defense");

        TRUE_DAMAGE_FLAG_KEY = new NamespacedKey(plugin, "mmo_true_damage_flag");

        TREE_BOW_POWER_KEY = new NamespacedKey(plugin, "mmo_tree_bow_power");
        TREE_BOW_MAGICAL_AMMO_KEY = new NamespacedKey(plugin, "mmo_tree_bow_magical_ammo");

        PROJECTILE_DAMAGE_MULTIPLIER_KEY = new NamespacedKey(plugin, "mmo_projectile_damage_multiplier");
        PROJECTILE_SOURCE_BOW_TYPE_KEY = new NamespacedKey(plugin, "mmo_projectile_source_bow_type");

        MINING_SPEED_KEY = new NamespacedKey(plugin, "mmo_mining_speed");
        FORAGING_SPEED_KEY = new NamespacedKey(plugin, "mmo_foraging_speed");
        FISHING_SPEED_KEY = new NamespacedKey(plugin, "mmo_fishing_speed");
        SHOOTING_SPEED_KEY = new NamespacedKey(plugin, "mmo_shooting_speed");

        INSTANT_SHOOT_BOW_TAG = new NamespacedKey(plugin, "mmo_instant_shoot_bow");
    }

    private NBTKeys() {
        throw new IllegalStateException("Utility class. Use NBTKeys.init(plugin) to initialize.");
    }
}
