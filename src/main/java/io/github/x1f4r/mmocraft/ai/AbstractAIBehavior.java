package io.github.x1f4r.mmocraft.ai;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.entities.CustomMobType;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * An optional abstract base class for {@link AIBehavior} implementations,
 * providing common boilerplate like storing the behavior ID.
 */
public abstract class AbstractAIBehavior implements AIBehavior {
    private final String behaviorId;
    private final int priority;
    private final boolean canInterruptBehavior;
    private final boolean isInterruptibleBehavior;
    private final int tickRateBehavior;

    protected AbstractAIBehavior(@NotNull String behaviorId, int priority, boolean canInterrupt, boolean isInterruptible, int tickRate) {
        this.behaviorId = Objects.requireNonNull(behaviorId, "Behavior ID cannot be null.");
        this.priority = priority;
        this.canInterruptBehavior = canInterrupt;
        this.isInterruptibleBehavior = isInterruptible;
        this.tickRateBehavior = Math.max(1, tickRate); // Ensure tick rate is at least 1
    }

    // Simplified constructor with common defaults
    protected AbstractAIBehavior(@NotNull String behaviorId, int priority) {
        this(behaviorId, priority, false, true, 1);
    }

    protected AbstractAIBehavior(@NotNull String behaviorId) {
        this(behaviorId, 10, false, true, 1); // Default priority 10
    }


    @Override
    @NotNull
    public String getBehaviorId() {
        return behaviorId;
    }

    @Override
    public void onStart(@NotNull LivingEntity entity, @NotNull CustomMobType mobType, @NotNull MMOCore core, @NotNull AIController controller) {
        // Default: no specific start action. Override if needed.
    }

    // shouldExecute() and onTick() must be implemented by concrete subclasses.

    @Override
    public void onEnd(@NotNull LivingEntity entity, @NotNull CustomMobType mobType, @NotNull MMOCore core, @NotNull AIController controller) {
        // Default: no specific end action. Override if needed.
    }

    @Override
    public int getTickRate() {
        return tickRateBehavior;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean canInterrupt() {
        return canInterruptBehavior;
    }

    @Override
    public boolean isInterruptible() {
        return isInterruptibleBehavior;
    }
}