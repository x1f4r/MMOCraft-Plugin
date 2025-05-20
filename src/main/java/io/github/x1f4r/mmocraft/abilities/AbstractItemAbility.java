package io.github.x1f4r.mmocraft.abilities;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.items.CustomItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull; // If using Jetbrains annotations

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class AbstractItemAbility implements ItemAbility {
    private final String abilityId;
    private final Component displayName;
    private final int defaultManaCost;
    private final int defaultCooldownTicks;

    public AbstractItemAbility(@NotNull String abilityId, @NotNull String displayName, int defaultManaCost, int defaultCooldownTicks) {
        this.abilityId = Objects.requireNonNull(abilityId, "Ability ID cannot be null");
        this.displayName = Component.text(Objects.requireNonNull(displayName, "Display name cannot be null"))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        this.defaultManaCost = Math.max(0, defaultManaCost);
        this.defaultCooldownTicks = Math.max(0, defaultCooldownTicks);
    }

    @Override
    @NotNull
    public String getAbilityId() { return abilityId; }

    @Override
    @NotNull
    public Component getDisplayName() { return displayName; }

    @Override
    @NotNull
    public List<Component> getDescription(@NotNull CustomItem itemTemplate) {
        // Default implementation: provides a generic placeholder.
        // Subclasses should override this to provide meaningful descriptions,
        // potentially using itemTemplate.getGenericNbtValue() for dynamic parts.
        return Collections.singletonList(
                Component.text("A mystical ability of " + PlainTextComponentSerializer.plainText().serialize(displayName) + ".", NamedTextColor.GRAY)
                        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
        );
    }

    @Override
    public int getDefaultManaCost() { return defaultManaCost; }

    @Override
    public int getDefaultCooldownTicks() { return defaultCooldownTicks; }

    /**
     * Default implementation for canExecute.
     * Assumes no special preconditions beyond what AbilityService checks (mana, cooldown).
     * Override in subclasses if specific conditions are needed (e.g., target required, specific world).
     * If returning false, the ability implementation should message the player why.
     */
    @Override
    public boolean canExecute(@NotNull Player player, @NotNull ItemStack itemStack, @NotNull CustomItem itemTemplate, @NotNull MMOCore core) {
        return true;
    }

    // The 'execute' method must be implemented by concrete subclasses.
    // public abstract boolean execute(@NotNull Player player, @NotNull ItemStack itemStack, @NotNull CustomItem itemTemplate, @NotNull MMOCore core);
}