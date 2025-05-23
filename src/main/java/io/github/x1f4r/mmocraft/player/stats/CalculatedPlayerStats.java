package io.github.x1f4r.mmocraft.player.stats;

/**
 * Represents the final, calculated stats of a player after considering all sources
 * (base from PlayerProfile, equipment from item NBT, buffs/debuffs from future systems).
 * This object is immutable by design (being a Java Record).
 */
public record CalculatedPlayerStats(
        int strength,
        int defense,
        int critChance,      // Already clamped 0-100 by PlayerStatsService
        int critDamage,
        int maxHealth,       // This will be the value applied to the Bukkit GENERIC_MAX_HEALTH attribute's base
        int maxMana,
        int speedPercent,    // Percentage value (e.g., 10 for +10% of vanilla base speed)
        int miningSpeed,
        int foragingSpeed,
        int fishingSpeed,
        int shootingSpeed
        // Future: Add other calculated stats like resistances, specific damage type bonuses, etc.
) {
    /**
     * Provides a default instance representing pure vanilla-like stats or a baseline.
     * Used as a fallback if stats cannot be calculated for a player.
     */
    public static final CalculatedPlayerStats PURE_VANILLA_DEFAULTS = new CalculatedPlayerStats(
            0,   // strength
            0,   // defense
            5,   // critChance (e.g. base 5%)
            50,  // critDamage (e.g. base +50%)
            20,  // maxHealth (vanilla default)
            100, // maxMana (example default)
            0,   // speedPercent (0% bonus over vanilla)
            0,   // miningSpeed
            0,   // foragingSpeed
            0,   // fishingSpeed
            0    // shootingSpeed
    );

    // Java Records automatically generate:
    // - A canonical constructor (takes all fields)
    // - Getters for all fields (e.g., strength(), defense())
    // - equals(), hashCode(), and toString() methods

    // Example of a potential helper method if needed elsewhere, though typically
    // the service using these stats (PlayerStatsService, CombatService) would handle conversions.
    // public double getSpeedAttributeMultiplier() {
    //     // Assuming speedPercent is an addition to a base of 100% (vanilla speed)
    //     // So, 20 speedPercent means 1.2x vanilla speed.
    //     return 1.0 + (speedPercent / 100.0);
    // }
}