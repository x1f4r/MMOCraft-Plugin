package io.github.x1f4r.mmocraft.player;

public class PlayerStats {

    // Combat Stats
    private int strength;
    private int critChance; // Base 0-100%
    private int critDamage; // Additional % damage on top of base crit (e.g., 50 means +50% damage)

    // Resource Stats
    private int currentMana;
    private int maxMana;

    // Utility Stats
    private int speed; // Percentage modifier

    public PlayerStats(int strength, int critChance, int critDamage, int currentMana, int maxMana, int speed) {
        this.strength = strength;
        this.critChance = Math.max(0, Math.min(100, critChance)); // Clamp
        this.critDamage = critDamage;
        this.maxMana = Math.max(1, maxMana); // Max mana should be at least 1
        this.currentMana = Math.max(0, Math.min(this.maxMana, currentMana)); // Clamp
        this.speed = speed;
    }

    // Default stats (e.g., for a new player or base values)
    public static PlayerStats base() {
        return new PlayerStats(0, 5, 50, 100, 100, 0); // Example: 5% base crit chance, 50% base crit damage, 100 mana
    }

    // Getters
    public int getStrength() { return strength; }
    public int getCritChance() { return critChance; }
    public int getCritDamage() { return critDamage; }
    public int getCurrentMana() { return currentMana; }
    public int getMaxMana() { return maxMana; }
    public int getSpeed() { return speed; }

    // Setters (or methods to modify stats)
    public void setStrength(int strength) { this.strength = strength; }
    public void setCritChance(int critChance) { this.critChance = Math.max(0, Math.min(100, critChance)); }
    public void setCritDamage(int critDamage) { this.critDamage = critDamage; }
    public void setCurrentMana(int currentMana) { this.currentMana = Math.max(0, Math.min(this.maxMana, currentMana));}
    public void setMaxMana(int maxMana) { 
        this.maxMana = Math.max(1, maxMana);
        // Ensure current mana doesn't exceed new max mana
        if (this.currentMana > this.maxMana) {
            this.currentMana = this.maxMana;
        }
    }
    public void setSpeed(int speed) { this.speed = speed; }

    public void addMana(int amount) {
        setCurrentMana(this.currentMana + amount);
    }

    public boolean consumeMana(int amount) {
        if (amount <= 0) return true; // Consuming 0 or negative mana is always successful
        if (this.currentMana >= amount) {
            setCurrentMana(this.currentMana - amount);
            return true;
        }
        return false;
    }
}
