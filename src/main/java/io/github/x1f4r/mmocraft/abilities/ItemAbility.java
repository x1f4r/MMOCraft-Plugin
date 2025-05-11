package io.github.x1f4r.mmocraft.abilities;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.items.CustomItem; // Template for context
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface ItemAbility {
    @NotNull String getAbilityId();
    @NotNull Component getDisplayName();
    @NotNull List<Component> getDescription(@NotNull CustomItem itemTemplate); // Pass template for dynamic descriptions
    int getDefaultManaCost();
    int getDefaultCooldownTicks();

    /**
     * The hand slot this ability typically activates from.
     * If null, AbilityService might not check or allow activation from any hand.
     * @return EquipmentSlot, default HAND.
     */
    @NotNull default EquipmentSlot getActivationSlot() { return EquipmentSlot.HAND; }

    /**
     * The player action(s) that trigger this ability.
     * @return A Set of Actions. Default includes RIGHT_CLICK_AIR and RIGHT_CLICK_BLOCK.
     */
    @NotNull default Set<Action> getActivationActions() {
        return Set.of(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK);
    }

    /**
     * Helper to check if an event action matches this ability's activation actions.
     */
    default boolean matchesActivationAction(@NotNull Action eventAction) {
        return getActivationActions().contains(eventAction);
    }

    /**
     * Checks if the ability can be executed under the current conditions,
     * beyond basic mana/cooldown checks (which AbilityService handles prior).
     * @return true if preconditions are met, false otherwise (should send own message if false).
     */
    boolean canExecute(@NotNull Player player, @NotNull ItemStack itemStack, @NotNull CustomItem itemTemplate, @NotNull MMOCore core);

    /**
     * Executes the core logic of the ability.
     * @return true if the ability executed successfully (for cooldown/mana commit),
     *         false if an internal error prevented full execution (mana might be refunded).
     */
    boolean execute(@NotNull Player player, @NotNull ItemStack itemStack, @NotNull CustomItem itemTemplate, @NotNull MMOCore core);
}