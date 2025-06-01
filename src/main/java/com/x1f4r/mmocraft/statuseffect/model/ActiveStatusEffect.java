package com.x1f4r.mmocraft.statuseffect.model;

import java.time.LocalDateTime; // Added
import java.time.ZoneOffset;    // Added
import java.util.UUID;
import java.util.Objects;

/**
 * Represents an active instance of a {@link StatusEffect} currently applied to an entity.
 * It tracks the specific effect, its target, and its remaining duration or next tick time.
 */
public class ActiveStatusEffect {

    private final StatusEffect statusEffect;
    private final UUID targetId; // UUID of the LivingEntity this effect is on
    private final long applicationTimeMillis;
    private long expirationTimeMillis; // System.currentTimeMillis() + duration. Long.MAX_VALUE if permanent. // Made non-final
    private long nextTickTimeMillis;   // For effects with tickIntervalSeconds > 0
    private int stacks;                // For stackable effects, default 1.

    /**
     * Creates a new active instance of a status effect.
     *
     * @param statusEffect The base {@link StatusEffect} being applied.
     * @param targetId The UUID of the entity this effect is applied to.
     */
    public ActiveStatusEffect(StatusEffect statusEffect, UUID targetId) {
        this.statusEffect = Objects.requireNonNull(statusEffect, "StatusEffect cannot be null.");
        this.targetId = Objects.requireNonNull(targetId, "Target UUID cannot be null.");
        this.applicationTimeMillis = System.currentTimeMillis();

        if (statusEffect.isPermanent()) {
            this.expirationTimeMillis = Long.MAX_VALUE;
        } else {
            this.expirationTimeMillis = this.applicationTimeMillis + (long) (statusEffect.getDurationSeconds() * 1000);
        }

        if (statusEffect.doesTick()) {
            this.nextTickTimeMillis = this.applicationTimeMillis + (long) (statusEffect.getTickIntervalSeconds() * 1000);
        } else {
            this.nextTickTimeMillis = Long.MAX_VALUE; // Will never tick if interval is 0 or less
        }
        this.stacks = 1; // Default to 1 stack
    }

    // Getters
    public StatusEffect getStatusEffect() { return statusEffect; }
    public UUID getTargetId() { return targetId; }
    public long getApplicationTimeMillis() { return applicationTimeMillis; }
    public long getExpirationTimeMillis() { return expirationTimeMillis; }
    public long getNextTickTimeMillis() { return nextTickTimeMillis; }
    public int getStacks() { return stacks; }

    // Setters (mainly for internal manager use)
    public void setNextTickTimeMillis(long nextTickTimeMillis) {
        this.nextTickTimeMillis = nextTickTimeMillis;
    }

    public void setExpirationTimeMillis(long expirationTimeMillis) {
        // Used for refreshing duration
        // this.expirationTimeMillis = expirationTimeMillis;
        throw new UnsupportedOperationException("Use StatusEffectManager to refresh effects. Expiration should be immutable or managed.");
    }

    public void setStacks(int stacks) {
        if (stacks <= 0) {
            throw new IllegalArgumentException("Stacks must be positive.");
        }
        this.stacks = stacks; // For now, direct set. Stacking rules (max stacks, etc.) would be in StatusEffectManager or the effect itself.
    }

    public void incrementStacks(int amount, double newDurationSeconds) {
        this.stacks += amount;
        // Optionally refresh duration on new stack application
        // this.expirationTimeMillis = System.currentTimeMillis() + (long) (newDurationSeconds * 1000);
    }


    /**
     * Checks if this status effect instance has expired based on current time.
     * @return True if expired, false otherwise.
     */
    public boolean isExpired() {
        if (statusEffect.isPermanent()) {
            return false;
        }
        return System.currentTimeMillis() >= expirationTimeMillis;
    }

    /**
     * Checks if this status effect instance is ready to tick based on current time.
     * @return True if it's time for the {@code onTick} method to be called, false otherwise.
     */
    public boolean isReadyToTick() {
        if (!statusEffect.doesTick() || isExpired()) { // Don't tick if permanent and no interval, or if expired
            return false;
        }
        return System.currentTimeMillis() >= nextTickTimeMillis;
    }

    /**
     * Updates the next tick time based on the current time and the effect's interval.
     * Should be called after an onTick operation.
     */
    public void updateNextTickTime() {
        if (statusEffect.doesTick()) {
            this.nextTickTimeMillis = System.currentTimeMillis() + (long) (statusEffect.getTickIntervalSeconds() * 1000);
        }
    }

    public void refreshDuration(double newDurationSeconds) {
        if (newDurationSeconds < 0) { // Permanent
             // this.expirationTimeMillis = Long.MAX_VALUE; // Not supported by this simple method
             throw new IllegalArgumentException("Cannot refresh to permanent with this method, use specific logic.");
        }
         this.expirationTimeMillis = System.currentTimeMillis() + (long) (newDurationSeconds * 1000);
    }


    @Override
    public String toString() {
        return "ActiveStatusEffect{" +
               "statusEffect=" + statusEffect.getEffectType() +
               ", targetId=" + targetId.toString().substring(0,8) +
               ", stacks=" + stacks +
               ", expiresAt=" + (statusEffect.isPermanent() ? "Permanent" : LocalDateTime.ofEpochSecond(expirationTimeMillis / 1000, 0, java.time.ZoneOffset.UTC)) +
               (statusEffect.doesTick() ? ", nextTickAt=" + LocalDateTime.ofEpochSecond(nextTickTimeMillis / 1000, 0, java.time.ZoneOffset.UTC) : "") +
               '}';
    }
}
