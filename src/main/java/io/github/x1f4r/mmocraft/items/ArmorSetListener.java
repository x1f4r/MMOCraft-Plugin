package io.github.x1f4r.mmocraft.items; // Updated package

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.utils.NBTKeys; // Updated import
import org.bukkit.Material;
// import org.bukkit.NamespacedKey; // No longer needed directly
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class ArmorSetListener implements Listener {

    private final MMOCraft plugin;
    private final Map<UUID, Long> sneakCooldown = new HashMap<>();
    private final long COOLDOWN_MILLIS = 5000; // 5 seconds
    private static final Logger logger = MMOCraft.getPlugin(MMOCraft.class).getLogger();

    // Define your custom item IDs as constants
    private static final String TREE_HELMET_ID = "tree_helmet";
    private static final String TREE_CHESTPLATE_ID = "tree_chestplate";
    private static final String TREE_LEGGINGS_ID = "tree_leggings";
    private static final String TREE_BOOTS_ID = "tree_boots";

    public ArmorSetListener(MMOCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return; // Only trigger when starting to sneak

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Cooldown check
        long currentTime = System.currentTimeMillis();
        long lastSneakTime = sneakCooldown.getOrDefault(playerUUID, 0L);
        if (currentTime - lastSneakTime < COOLDOWN_MILLIS) {
            // player.sendMessage(ChatColor.RED + "Tree set ability on cooldown!"); // Optional feedback
            return;
        }

        if (!isWearingFullTreeSet(player)) {
            return;
        }

        logger.info(player.getName() + " triggered Tree Armor sneak ability!");
        sneakCooldown.put(playerUUID, currentTime); // Update cooldown time

        // Ability: Drop an apple
        ItemStack apple = new ItemStack(Material.APPLE, 1);
        player.getWorld().dropItemNaturally(player.getLocation(), apple);
        player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 0.5f, 1.0f); // Example sound
        // player.sendMessage(ChatColor.GREEN + "You feel the forest's generosity!"); // Example message
    }

    private boolean isWearingFullTreeSet(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack helmet = inventory.getHelmet();
        ItemStack chestplate = inventory.getChestplate();
        ItemStack leggings = inventory.getLeggings();
        ItemStack boots = inventory.getBoots();

        if (!isTreeArmorPiece(helmet, TREE_HELMET_ID)) return false;
        if (!isTreeArmorPiece(chestplate, TREE_CHESTPLATE_ID)) return false;
        if (!isTreeArmorPiece(leggings, TREE_LEGGINGS_ID)) return false;
        if (!isTreeArmorPiece(boots, TREE_BOOTS_ID)) return false;

        return true;
    }

    private boolean isTreeArmorPiece(ItemStack item, String expectedId) {
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
}
