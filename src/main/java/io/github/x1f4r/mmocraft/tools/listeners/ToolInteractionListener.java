package io.github.x1f4r.mmocraft.tools.listeners;

import io.github.x1f4r.mmocraft.services.NBTService; // For tagging projectiles
import io.github.x1f4r.mmocraft.services.ToolProficiencyService;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

public class ToolInteractionListener implements Listener {
    private final ToolProficiencyService toolService;

    public ToolInteractionListener(ToolProficiencyService toolService) {
        this.toolService = Objects.requireNonNull(toolService, "ToolProficiencyService cannot be null.");
    }

    // --- Block Breaking Listeners ---

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerLeftClickBlock(PlayerInteractEvent event) {
        // We only care about left-clicking a block to initiate or continue mining.
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null && event.getItem() != null) {
            // Pass the item in hand at the moment of the click.
            // ToolProficiencyService will decide if it's a valid tool or just hand.
            toolService.handleBlockLeftClick(event.getPlayer(), event.getClickedBlock(), event.getItem());
        }
        // Right-click for Treecapitator or other tool abilities might be handled here too,
        // or by PlayerAbilityListener if they are standard "abilities".
        // For Treecapitator, it's triggered on BlockBreakEvent currently.
    }

    // Priority HIGH to intercept vanilla damage if our system should handle this block break.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVanillaBlockDamage(BlockDamageEvent event) {
        // This event fires continuously as a player damages a block.
        // ToolProficiencyService.interceptBlockDamage will cancel it if our custom system is active for this block.
        toolService.interceptBlockDamage(event.getPlayer(), event.getBlock(), event.getItemInHand(), event);
    }

    // Priority MONITOR to act *after* the block is confirmed broken by any means
    // (vanilla, our custom system, or another plugin).
    // Used for Treecapitator activation and cleaning up any lingering mining tasks.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onActualBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Always clear any custom mining task associated with this player and block,
        // regardless of who or what broke it, to prevent orphaned tasks.
        // false: block is gone, no need to send animation clear packet.
        toolService.clearMiningTask(player.getUniqueId(), false);

        // Treecapitator check: only if broken by the player themselves.
        // The tool used for the break is event.getPlayer().getInventory().getItemInMainHand() typically.
        // Or, if a specific tool was registered with the event, use that.
        // For Treecapitator, it makes sense to check the main hand tool at the moment of break.
        ItemStack toolInHand = player.getInventory().getItemInMainHand();
        toolService.attemptTreecapitator(player, event.getBlock(), toolInHand);
    }

    // Clean up mining task if player quits mid-mine.
    @EventHandler(priority = EventPriority.MONITOR) // Act late on quit
    public void onPlayerQuitMidMine(PlayerQuitEvent event) {
        // true: send clear animation to other players if they were observing.
        toolService.clearMiningTask(event.getPlayer().getUniqueId(), true);
    }


    // --- Fishing Listener ---
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerCastFishingRod(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.FISHING) { // When bobber is initially cast and in water
            FishHook hook = event.getHook();
            if (hook != null) {
                toolService.applyFishingSpeed(event.getPlayer(), hook);
            }
        }
        // Other states like CAUGHT_FISH, FAILED_ATTEMPT can be handled here for:
        // - Custom fishing loot tables (Future Part)
        // - Durability on fishing rod (ToolProficiencyService.handleToolDurability)
        // - Applying player stats like "Angler" (bonus chance for rare fish)
        // Example for durability on successful catch:
        // if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() != null) {
        //     ItemStack rod = event.getPlayer().getInventory().getItemInMainHand(); // Or offhand if supported
        //     if (rod != null && rod.getType() == Material.FISHING_ROD) {
        //         toolService.handleToolDurability(event.getPlayer(), rod, 1);
        //     }
        // }
    }

    // --- Bow Shooting Listener (for DRAWN bows) ---
    // Instant-shot bows are typically handled by PlayerAbilityListener if they are abilities.
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerShootDrawnBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof Arrow arrow)) {
            return; // Not a player shooting an arrow
        }
        ItemStack bow = event.getBow(); // The bow ItemStack used
        if (bow == null || bow.getType().isAir() || !bow.hasItemMeta()) return;

        // 1. Apply Shooting Speed stat from player to the arrow's velocity
        toolService.applyShootingSpeedToDrawnProjectile(player, bow, arrow);

        // 2. Tag the arrow with relevant NBT from the bow for CombatService
        ItemMeta bowMeta = bow.getItemMeta(); // Already checked hasItemMeta
        if (bowMeta == null) return; // Should not happen
        PersistentDataContainer bowPdc = bowMeta.getPersistentDataContainer();
        PersistentDataContainer arrowPdc = arrow.getPersistentDataContainer();

        // Tag with source custom item ID if it's a custom bow
        String customBowId = NBTService.get(bowPdc, NBTService.ITEM_ID_KEY, PersistentDataType.STRING, null);
        if (customBowId != null) {
            NBTService.set(arrowPdc, NBTService.PROJECTILE_SOURCE_ITEM_ID_KEY, PersistentDataType.STRING, customBowId);
        }

        // Transfer a generic projectile damage multiplier if the bow has one
        Integer projDamageMultiplier = NBTService.get(bowPdc, NBTService.PROJECTILE_DAMAGE_MULTIPLIER, PersistentDataType.INTEGER, null);
        if (projDamageMultiplier != null) {
            NBTService.set(arrowPdc, NBTService.PROJECTILE_DAMAGE_MULTIPLIER, PersistentDataType.INTEGER, projDamageMultiplier);
        }

        // Transfer True Damage flag from bow to arrow
        Byte trueDamageFlag = NBTService.get(bowPdc, NBTService.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE, null);
        if (trueDamageFlag != null && trueDamageFlag == (byte)1) {
            NBTService.set(arrowPdc, NBTService.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE, (byte)1);
        }

        // Future: Tag with other effects or properties from the bow
        // (e.g., "EXPLOSIVE_ARROW_FLAG", "POISON_ARROW_DURATION")
        // These would then be read by CombatService or an AbilityProjectileListener.
    }
}