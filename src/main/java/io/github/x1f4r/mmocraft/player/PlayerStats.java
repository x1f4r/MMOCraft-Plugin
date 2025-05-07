package io.github.x1f4r.mmocraft.player;

public class PlayerStats {

    // Combat Stats
    private int strength;
    private int critChance; // Base 0-100%
    private int critDamage; // Additional % damage on top of base crit (e.g., 50 means +50% damage)
    private int defense;    // New Custom Defense Stat

    // Resource Stats
    private int currentMana;
    private int maxMana;

    // Utility Stats
    private int speed; // Percentage modifier

    public PlayerStats(int strength, int critChance, int critDamage, int defense, int currentMana, int maxMana, int speed) {
        this.strength = strength;
        this.critChance = Math.max(0, Math.min(100, critChance)); // Clamp
        this.critDamage = critDamage;
        this.defense = Math.max(0, defense); // Ensure non-negative
        this.maxMana = Math.max(1, maxMana); // Max mana should be at least 1
        // currentMana will be clamped against the initial maxMana here, which is fine for instantiation.
        this.currentMana = Math.max(0, Math.min(this.maxMana, currentMana));
        this.speed = speed;
    }

    // Default stats (e.g., for a new player or base values)
    public static PlayerStats base() {
        // Example: 5% base crit chance, 50% base crit damage, 0 custom defense, 100 mana
        return new PlayerStats(0, 5, 50, 0, 100, 100, 0);
    }

    // Getters
    public int getStrength() { return strength; }
    public int getCritChance() { return critChance; }
    public int getCritDamage() { return critDamage; }
    public int getDefense() { return defense; }
    public int getCurrentMana() { return currentMana; }
    public int getMaxMana() { return maxMana; }
    public int getSpeed() { return speed; }

    // Setters
    public void setStrength(int strength) { this.strength = strength; }
    public void setCritChance(int critChance) { this.critChance = Math.max(0, Math.min(100, critChance)); }
    public void setCritDamage(int critDamage) { this.critDamage = critDamage; }
    public void setDefense(int defense) { this.defense = Math.max(0, defense); }

    public void setCurrentMana(int currentMana) {
        this.currentMana = Math.max(0, Math.min(this.maxMana, currentMana)); // Clamp against current maxMana
    }

    /**
     * Sets the maximum mana.
     * IMPORTANT: This method now ONLY sets the maxMana field.
     * It does NOT clamp currentMana. Clamping of currentMana happens in setCurrentMana()
     * or at the end of a full stat update in PlayerStatsManager.
     * @param newMaxMana The new maximum mana.
     */
    public void setMaxMana(int newMaxMana) {
        this.maxMana = Math.max(1, newMaxMana);
        // REMOVED: Clamping logic for this.currentMana.
        // This prevents currentMana from being reset prematurely when maxMana is temporarily set to base during recalculation.
    }

    public void setSpeed(int speed) { this.speed = speed; }

    public void addMana(int amount) {
        setCurrentMana(this.currentMana + amount);
    }

    public boolean consumeMana(int amount) {
        if (amount <= 0) return true;
        if (this.currentMana >= amount) {
            setCurrentMana(this.currentMana - amount);
            return true;
        }
        return false;
    }
}