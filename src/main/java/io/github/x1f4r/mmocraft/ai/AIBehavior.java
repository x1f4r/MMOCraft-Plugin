package io.github.x1f4r.mmocraft.ai;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.entities.CustomMobType;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a modular piece of AI logic for a custom mob.
 * Implementations of this interface define specific actions or decision-making processes.
 */
public interface AIBehavior {
    /**
     * @return A unique machine-readable identifier for this behavior type (e.g., "basic_melee", "random_stroll").
     */
    @NotNull String getBehaviorId();

    /**
     * Called once when this behavior instance is first associated with an entity's AIController
     * and is about to become active or considered for execution.
     * Can be used for one-time setup specific to this entity-behavior pairing.
     *
     * @param entity The entity this behavior is for.
     * @param mobType The CustomMobType definition of the entity.
     * @param core The MMOCore instance for service access.
     * @param controller The AIController managing this behavior.
     */
    void onStart(@NotNull LivingEntity entity, @NotNull CustomMobType mobType, @NotNull MMOCore core, @NotNull AIController controller);

    /**
     * Determines if this behavior should start or continue executing in the current tick.
     * Called frequently by the AIController.
     *
     * @param entity The entity.
     * @param mobType The CustomMobType.
     * @param core The MMOCore.
     * @param controller The AIController.
     * @return true if the behavior should execute, false otherwise.
     */
    boolean shouldExecute(@NotNull LivingEntity entity, @NotNull CustomMobType mobType, @NotNull MMOCore core, @NotNull AIController controller);

    /**
     * The main update logic for this behavior.
     * Called when {@link #shouldExecute(LivingEntity, CustomMobType, MMOCore, AIController)} returns true
     * and according to its {@link #getTickRate()} or the AIController's decision logic.
     *
     * @param entity The entity.
     * @param mobType The CustomMobType.
     * @param core The MMOCore.
     * @param controller The AIController.
     */
    void onTick(@NotNull LivingEntity entity, @NotNull CustomMobType mobType, @NotNull MMOCore core, @NotNull AIController controller);

    /**
     * Called when this behavior stops executing.
     * This can happen if {@link #shouldExecute(LivingEntity, CustomMobType, MMOCore, AIController)} becomes false,
     * or if it's interrupted by a higher priority behavior.
     * Used for cleanup or resetting state for this behavior.
     *
     * @param entity The entity.
     * @param mobType The CustomMobType.
     * @param core The MMOCore.
     * @param controller The AIController.
     */
    void onEnd(@NotNull LivingEntity entity, @NotNull CustomMobType mobType, @NotNull MMOCore core, @NotNull AIController controller);

    /**
     * Defines how often {@link #onTick(LivingEntity, CustomMobType, MMOCore, AIController)} should ideally be called, in game ticks.
     * The {@link AIController} may use this as a guide for its update loop for this behavior.
     * A value of 0 or 1 typically means it should be ticked every time the AIController ticks.
     *
     * @return The preferred tick rate for this behavior.
     */
    default int getTickRate() { return 1; } // Default to tick every AI controller update

    /**
     * The priority of this behavior. Lower numbers indicate higher priority.
     * The {@link AIController} uses this to decide which behavior should run if multiple
     * behaviors report {@code shouldExecute() == true} simultaneously.
     *
     * @return The priority value.
     */
    default int getPriority() { return 10; } // Default to a medium-low priority

    /**
     * @return true if this behavior can interrupt other currently running, lower-priority behaviors that are interruptible.
     */
    default boolean canInterrupt() { return false; }

    /**
     * @return true if this behavior can be interrupted by other higher-priority behaviors that can interrupt.
     */
    default boolean isInterruptible() { return true; }
}