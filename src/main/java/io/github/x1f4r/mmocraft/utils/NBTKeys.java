package io.github.x1f4r.mmocraft.utils;

import io.github.x1f4r.mmocraft.MMOCraft;
import org.bukkit.NamespacedKey;

public final class NBTKeys {

    private static MMOCraft plugin;

    // Item Identification
    public static NamespacedKey ITEM_ID_KEY;

    // Mob Identification
    public static NamespacedKey MOB_TYPE_KEY;

    // Custom Stats
    public static NamespacedKey STRENGTH_KEY;
    public static NamespacedKey CRIT_CHANCE_KEY;
    public static NamespacedKey CRIT_DAMAGE_KEY;
    public static NamespacedKey MANA_KEY;       // Can represent max_mana on armor or mana_cost on items
    public static NamespacedKey SPEED_KEY;

    public static void init(MMOCraft pluginInstance) {
        if (NBTKeys.plugin != null) { // Ensure it's initialized only once
            return;
        }
        NBTKeys.plugin = pluginInstance;

        // It's good practice to prefix plugin-specific keys
        ITEM_ID_KEY = new NamespacedKey(plugin, "mmo_item_id");
        MOB_TYPE_KEY = new NamespacedKey(plugin, "mmo_mob_type");

        STRENGTH_KEY = new NamespacedKey(plugin, "mmo_strength");
        CRIT_CHANCE_KEY = new NamespacedKey(plugin, "mmo_crit_chance");
        CRIT_DAMAGE_KEY = new NamespacedKey(plugin, "mmo_crit_damage");
        MANA_KEY = new NamespacedKey(plugin, "mmo_mana_stat"); // General mana key
        SPEED_KEY = new NamespacedKey(plugin, "mmo_speed");
    }

    private NBTKeys() {
        // Prevent instantiation
        throw new IllegalStateException("Utility class. Use NBTKeys.init(plugin) to initialize.");
    }
}
