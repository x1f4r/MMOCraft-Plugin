package io.github.x1f4r.mmocraft.items; // Keep in items package

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
// import io.github.x1f4r.mmocraft.items.abilities.PlayerAbilityManager; // Removed unused and incorrect import
import io.github.x1f4r.mmocraft.utils.NBTKeys;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent; // Example trigger
// Could also use a repeating task in PlayerStatsManager or a dedicated ArmorManager
// to check equipped sets periodically or on equip events.
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.logging.Logger;

public class ArmorSetListener implements Listener {

    // private final MMOCore core; // Removed unused field
    // private final MMOPlugin plugin; // Removed unused field
    private final Logger log;

    // Define item IDs for armor sets
    private static final String TREE_HELMET_ID = "tree_helmet";
    private static final String TREE_CHESTPLATE_ID = "tree_chestplate";
    private static final String TREE_LEGGINGS_ID = "tree_leggings";
    private static final String TREE_BOOTS_ID = "tree_boots";

    // Add IDs for other sets like Ender Armor if they have set bonuses
    // private static final String ENDER_LEGGINGS_ID = "ender_leggings"; // Removed
    // private static final String ENDER_BOOTS_ID = "ender_boots"; // Removed

    // Constants for Potion Effects
    // private static final PotionEffectType ENDER_STRENGTH_EFFECT_TYPE = PotionEffectType.INCREASE_DAMAGE; // Removed

    public ArmorSetListener(MMOCore core) {
        // this.core = core; // Removed
        // this.plugin = core.getPlugin(); // Removed
        this.log = MMOPlugin.getMMOLogger();
    }

    // Example: Triggering a set bonus on sneak (like the original Tree Armor)
    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return; // Only trigger when starting to sneak

        Player player = event.getPlayer();

        // Check for Tree Set Bonus
        if (isWearingFullSet(player, TREE_HELMET_ID, TREE_CHESTPLATE_ID, TREE_LEGGINGS_ID, TREE_BOOTS_ID)) {
            log.finer(player.getName() + " triggered Tree Armor sneak ability!");
            // Ability: Drop an apple (Example)
            ItemStack apple = new ItemStack(Material.APPLE, 1);
            player.getWorld().dropItemNaturally(player.getLocation(), apple);
            player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 0.5f, 1.0f);
            // Prevent other set bonuses from triggering on the same sneak event if needed
            return;
        }

        // Check for other set bonuses here...
        // if (isWearingFullSet(player, ENDER_HELMET_ID, ... )) {
        //    // Apply Ender set bonus effect on sneak? (Example)
        //    return;
        // }
    }

    // Helper method to check if a player is wearing a specific full set
    private boolean isWearingFullSet(Player player, String helmetId, String chestplateId, String leggingsId, String bootsId) {
        PlayerInventory inventory = player.getInventory();
        if (!isSpecificArmorPiece(inventory.getHelmet(), helmetId)) return false;
        if (!isSpecificArmorPiece(inventory.getChestplate(), chestplateId)) return false;
        if (!isSpecificArmorPiece(inventory.getLeggings(), leggingsId)) return false;
        if (!isSpecificArmorPiece(inventory.getBoots(), bootsId)) return false;
        return true; // All pieces match
    }

    // Helper method to check if an ItemStack is a specific custom armor piece
    private boolean isSpecificArmorPiece(ItemStack item, String expectedId) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (NBTKeys.ITEM_ID_KEY != null && pdc.has(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING)) {
            String itemId = pdc.get(NBTKeys.ITEM_ID_KEY, PersistentDataType.STRING);
            return expectedId.equalsIgnoreCase(itemId);
        }
        return false;
    }

    // Note: For passive set bonuses (like stat boosts), the logic is typically handled
    // within the PlayerStatsManager during the equipment calculation phase, checking
    // if a full set is worn there. This listener is more for active/triggered effects.
}

