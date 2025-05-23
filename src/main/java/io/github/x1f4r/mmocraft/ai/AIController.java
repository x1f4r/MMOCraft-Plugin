package io.github.x1f4r.mmocraft.ai;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.entities.CustomMobType;
import io.github.x1f4r.mmocraft.services.CustomMobService;
import io.github.x1f4r.mmocraft.services.LoggingService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob; // For Bukkit GoalSelector access if using Purpur Goals
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Manages the AI behaviors for a single custom entity instance.
 * It orchestrates which behavior should run based on priorities and conditions.
 * This implementation is for a custom runnable-based AI system.
 * For Purpur Goal system, this class would be significantly different.
 */
public class AIController {
    private final LivingEntity entity;
    private final CustomMobType mobType;
    private final MMOCore core;
    private final LoggingService logging;
    private final List<AIBehavior> registeredBehaviors = new ArrayList<>();
    // Stores tick counters for behaviors that have a specific tickRate > 1
    private final Map<AIBehavior, Integer> behaviorTickCounters = new HashMap<>();

    private AIBehavior currentExecutingBehavior = null;
    private BukkitTask aiTickTask;
    private final long controllerUpdateIntervalTicks; // How often the AIController itself updates (evaluates behaviors)

    // Shared data for behaviors (example)
    private LivingEntity currentTargetEntity; // Target shared across behaviors for this mob

    public AIController(@NotNull LivingEntity entity, @NotNull CustomMobType mobType, @NotNull MMOCore core, long updateIntervalTicks) {
        this.entity = Objects.requireNonNull(entity, "Entity cannot be null for AIController.");
        this.mobType = Objects.requireNonNull(mobType, "CustomMobType cannot be null for AIController.");
        this.core = Objects.requireNonNull(core, "MMOCore cannot be null for AIController.");
        this.logging = core.getService(LoggingService.class);
        this.controllerUpdateIntervalTicks = Math.max(1, updateIntervalTicks); // Ensure at least 1 tick

        CustomMobService customMobService = core.getService(CustomMobService.class);
        for (String behaviorId : mobType.aiBehaviorIds()) {
            AIBehavior behavior = customMobService.getAIBehavior(behaviorId);
            if (behavior != null) {
                registeredBehaviors.add(behavior);
                behaviorTickCounters.put(behavior, 0); // Initialize tick counter
            } else {
                logging.warn("CustomMobType '" + mobType.typeId() + "' (Entity: " + entity.getUniqueId() +
                        ") lists unknown AIBehavior ID: '" + behaviorId + "'. Behavior will be skipped.");
            }
        }
        // Sort behaviors by priority (lower number = higher priority)
        registeredBehaviors.sort(Comparator.comparingInt(AIBehavior::getPriority));
    }

    public void start() {
        if (aiTickTask != null && !aiTickTask.isCancelled()) {
            aiTickTask.cancel(); // Cancel existing if any
        }
        if (registeredBehaviors.isEmpty()) {
            if (logging.isDebugMode()) logging.debug("No AI behaviors to run for " + mobType.typeId() + " (" + entity.getUniqueId() + "). AIController task not started.");
            return;
        }

        // Call onStart for all registered behaviors once
        for(AIBehavior behavior : registeredBehaviors) {
            try {
                behavior.onStart(entity, mobType, core, this);
            } catch (Exception e) {
                logging.severe("Error in AIBehavior.onStart() for " + behavior.getBehaviorId() + " on entity " + entity.getUniqueId(), e);
            }
        }

        aiTickTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!entity.isValid() || entity.isDead()) {
                    this.cancel(); // Stop task if entity is no longer valid
                    // CustomMobService's handleEntityDespawn will call AIController.stop() for map cleanup.
                    return;
                }
                tickAI(); // Perform AI logic evaluation
            }
        }.runTaskTimer(core.getPlugin(), 0L, controllerUpdateIntervalTicks); // Run at configured interval

        if (logging.isDebugMode()) logging.debug("AIController started for " + mobType.typeId() + " (" + entity.getUniqueId() + ") with update interval " + controllerUpdateIntervalTicks + " ticks.");
    }

    public void stop() {
        if (aiTickTask != null && !aiTickTask.isCancelled()) {
            aiTickTask.cancel();
        }
        // Call onEnd for the currently executing behavior if it exists
        if (currentExecutingBehavior != null) {
            try {
                currentExecutingBehavior.onEnd(entity, mobType, core, this);
            } catch (Exception e) {
                logging.severe("Error in current AIBehavior.onEnd() for " + currentExecutingBehavior.getBehaviorId() + " on entity " + entity.getUniqueId(), e);
            }
            currentExecutingBehavior = null;
        }
        // Optionally, call onEnd for ALL registered behaviors if they need global cleanup
        // for (AIBehavior behavior : registeredBehaviors) {
        //     try { behavior.onEnd(entity, mobType, core, this); } catch (Exception e) { /* log */ }
        // }
        if (logging.isDebugMode()) logging.debug("AIController stopped for " + mobType.typeId() + " (" + entity.getUniqueId() + ")");
    }

    private void tickAI() {
        AIBehavior newHighestPriorityBehavior = null;

        // Find the highest priority behavior that currently shouldExecute
        for (AIBehavior behavior : registeredBehaviors) {
            try {
                if (behavior.shouldExecute(entity, mobType, core, this)) {
                    newHighestPriorityBehavior = behavior;
                    break; // Since behaviors are sorted by priority, the first one is highest
                }
            } catch (Exception e) {
                logging.severe("Error in AIBehavior.shouldExecute() for " + behavior.getBehaviorId() + " on entity " + entity.getUniqueId(), e);
                // Skip this behavior for this tick to prevent further errors
            }
        }

        // --- Behavior Transition Logic ---
        if (currentExecutingBehavior != newHighestPriorityBehavior) { // A change in active behavior
            if (currentExecutingBehavior != null) { // If there was a behavior running
                // Check if the current behavior can be interrupted OR if the new one forces interruption
                if (currentExecutingBehavior.isInterruptible() ||
                        (newHighestPriorityBehavior != null && newHighestPriorityBehavior.canInterrupt() &&
                                newHighestPriorityBehavior.getPriority() < currentExecutingBehavior.getPriority())) {

                    if (logging.isDebugMode()) logging.debug("AI (" + mobType.typeId() + "): Stopping " + currentExecutingBehavior.getBehaviorId() +
                            (newHighestPriorityBehavior != null ? " for " + newHighestPriorityBehavior.getBehaviorId() : " (IDLE)"));
                    try {
                        currentExecutingBehavior.onEnd(entity, mobType, core, this);
                    } catch (Exception e) {
                        logging.severe("Error in AIBehavior.onEnd() for " + currentExecutingBehavior.getBehaviorId() + " on entity " + entity.getUniqueId(), e);
                    }
                    currentExecutingBehavior = null; // Clear current
                } else if (newHighestPriorityBehavior != null) {
                    // Current behavior is not interruptible by the new one, so new one doesn't start. Current continues.
                    newHighestPriorityBehavior = currentExecutingBehavior; // Effectively, no change for this tick's execution
                    if (logging.isDebugMode()) logging.debug("AI (" + mobType.typeId() + "): " + currentExecutingBehavior.getBehaviorId() + " is not interruptible by " + newHighestPriorityBehavior.getBehaviorId() + " or new behavior cannot interrupt.");
                }
                // If newHighestPriorityBehavior is null, and current is not interruptible, it will just continue.
            }
            // If current was null, and new is not null, new one will start below.
            if (currentExecutingBehavior == null && newHighestPriorityBehavior != null) {
                currentExecutingBehavior = newHighestPriorityBehavior;
                if (logging.isDebugMode()) logging.debug("AI (" + mobType.typeId() + "): Starting " + currentExecutingBehavior.getBehaviorId());
                try {
                    currentExecutingBehavior.onStart(entity, mobType, core, this);
                } catch (Exception e) {
                    logging.severe("Error in AIBehavior.onStart() for " + currentExecutingBehavior.getBehaviorId() + " on entity " + entity.getUniqueId(), e);
                    currentExecutingBehavior = null; // Prevent ticking a broken behavior
                }
                behaviorTickCounters.put(currentExecutingBehavior, 0); // Reset tick counter for the new behavior
            }
        }


        // --- Tick the Currently Executing Behavior ---
        if (currentExecutingBehavior != null) {
            // Re-check shouldExecute in case its conditions changed due to another behavior's onStart/onEnd
            // or if it's a self-terminating behavior.
            boolean stillShouldExecute = false;
            try {
                stillShouldExecute = currentExecutingBehavior.shouldExecute(entity, mobType, core, this);
            } catch (Exception e) {
                logging.severe("Error in AIBehavior.shouldExecute() during tick for " + currentExecutingBehavior.getBehaviorId() + " on entity " + entity.getUniqueId(), e);
            }

            if (!stillShouldExecute) {
                if (logging.isDebugMode()) logging.debug("AI (" + mobType.typeId() + "): " + currentExecutingBehavior.getBehaviorId() +
                        " no longer shouldExecute. Stopping.");
                try {
                    currentExecutingBehavior.onEnd(entity, mobType, core, this);
                } catch (Exception e) {
                    logging.severe("Error in AIBehavior.onEnd() for " + currentExecutingBehavior.getBehaviorId() + " on entity " + entity.getUniqueId(), e);
                }
                currentExecutingBehavior = null;
                // No recursive call to tickAI(); let the next controller update interval handle new behavior selection.
            } else {
                // Check if it's time to tick this specific behavior based on its own tickRate
                int tickCount = behaviorTickCounters.getOrDefault(currentExecutingBehavior, 0);
                if (tickCount % Math.max(1, currentExecutingBehavior.getTickRate()) == 0) {
                    try {
                        // if (logging.isDebugMode()) logging.debug("AI (" + mobType.typeId() + "): Ticking " + currentExecutingBehavior.getBehaviorId());
                        currentExecutingBehavior.onTick(entity, mobType, core, this);
                    } catch (Exception e) {
                        logging.severe("Error in AIBehavior.onTick() for " + currentExecutingBehavior.getBehaviorId() + " on entity " + entity.getUniqueId(), e);
                        // Optionally, stop this behavior if it errors to prevent spam
                        // currentExecutingBehavior.onEnd(entity, mobType, core, this);
                        // currentExecutingBehavior = null;
                    }
                }
                behaviorTickCounters.put(currentExecutingBehavior, tickCount + 1);
            }
        }
    }

    // --- Shared Data Access for Behaviors ---
    @Nullable
    public LivingEntity getSharedTarget() {
        // Validate target if stored
        if (currentTargetEntity != null && (!currentTargetEntity.isValid() || currentTargetEntity.isDead())) {
            currentTargetEntity = null;
        }
        return currentTargetEntity;
    }

    public void setSharedTarget(@Nullable LivingEntity target) {
        if (target != null && (!target.isValid() || target.isDead())) {
            this.currentTargetEntity = null;
            if (entity instanceof Mob) ((Mob) entity).setTarget(null); // Clear Bukkit target too
            return;
        }
        this.currentTargetEntity = target;
        if (entity instanceof Mob) { // Also set Bukkit's target if applicable
            ((Mob) entity).setTarget(target);
        }
        if (logging.isDebugMode() && target != null) {
            logging.debug("AI (" + mobType.typeId() + "): Target set to " + target.getName());
        } else if (logging.isDebugMode() && target == null) {
            logging.debug("AI (" + mobType.typeId() + "): Target cleared.");
        }
    }

    public LivingEntity getEntity() {
        return entity;
    }
}