package io.github.x1f4r.mmocraft.abilities.listeners;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.services.AbilityService;
import io.github.x1f4r.mmocraft.services.LoggingService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public class PlayerAbilityActivationListener implements Listener {
    private final AbilityService abilityService;
    private final LoggingService logging;

    public PlayerAbilityActivationListener(AbilityService abilityService) {
        this.abilityService = Objects.requireNonNull(abilityService, "AbilityService cannot be null.");
        // Assuming AbilityService is initialized via MMOCore, we can get LoggingService from there
        MMOCore core = MMOCraft.getInstance().getCore(); // Static access for simplicity in listener constructor
        this.logging = core.getService(LoggingService.class);
    }

    // Priority NORMAL should generally be fine. If abilities need to override other plugins' interact events,
    // HIGH or HIGHEST might be needed, but then careful event cancellation is required.
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteractForAbility(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        // We are interested in right-clicks and potentially left-clicks for some abilities.
        // The specific ability's getActivationActions() will determine if it matches.
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK &&
                action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            // This can happen in some edge cases or with older Bukkit versions for certain interactions.
            // Try to infer: if they clicked a block, it's likely main hand. If air, also likely main.
            // Defaulting to MAIN_HAND if null, but this is a slight assumption.
            // A more robust check might involve event.getItem() and seeing which hand it's in.
            // For now, if hand is null, we might primarily check main hand item.
            if (logging.isDebugMode()) {
                logging.debug("PlayerInteractEvent hand is null for " + player.getName() + ", action " + action + ". Checking main hand item.");
            }
            hand = EquipmentSlot.HAND; // Default assumption
        }

        ItemStack itemInvolved = player.getInventory().getItem(hand); // Get item from the interacting hand

        if (itemInvolved == null || itemInvolved.getType().isAir()) {
            // If the interacting hand was empty, but it was a RIGHT_CLICK,
            // some games allow off-hand item abilities to trigger.
            // Our AbilityService.attemptToExecuteAbility handles this specific logic for right clicks.
            if (hand == EquipmentSlot.HAND && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
                itemInvolved = player.getInventory().getItemInOffHand(); // Check offhand if main was empty on right click
                if (itemInvolved == null || itemInvolved.getType().isAir()) {
                    return; // Both hands effectively empty for ability activation.
                }
                hand = EquipmentSlot.OFF_HAND; // Mark that we're now considering the off-hand item
            } else {
                return; // No item involved in the primary interacting hand.
            }
        }

        // Pass the determined item, action, and hand to AbilityService
        boolean abilityProcessed = abilityService.attemptToExecuteAbility(player, itemInvolved, action, hand);

        if (abilityProcessed) {
            // If an ability was found and attemptToExecute (even if it failed mana/cooldown/canExecute)
            // usually means we want to cancel the original Bukkit event to prevent default behaviors
            // like opening a chest, placing a block, or vanilla item usage.
            // The specific ability's execute() method might also setCancelled if it needs finer control.
            // For most right-click abilities, cancelling is desired.
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
            }
            // For left-click abilities, cancellation needs more thought to not interfere with attacking/breaking.
            // An ability can set event.setUseInteractedBlock(Event.Result.DENY) and event.setUseItemInHand(Event.Result.DENY)
            // if it wants to specifically deny block interaction or item use without fully cancelling the animation.
        }
    }
}