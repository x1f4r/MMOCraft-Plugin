package io.github.x1f4r.mmocraft.stats;

public class EntityStats {

    // Core Combat Stats
    private double maxHealth;
    private double currentHealth; // Not directly managed by this object after initial set, entity manages its own.
    private int defense;
    private int strength; // Can be base damage or an additive bonus
    private int speed;    // Percentage modifier for movement speed attribute

    // Optional: Mana for mobs if they use abilities
    private int maxMana;
    private int currentMana;

    // Optional: Crit stats for mobs
    private int critChance; // 0-100
    private int critDamage; // Percentage, e.g., 50 for +50% damage

    public EntityStats(double maxHealth, int defense, int strength, int speed, int maxMana, int critChance, int critDamage) {
        this.maxHealth = Math.max(1.0, maxHealth);
        this.currentHealth = this.maxHealth; // Initialized to max
        this.defense = Math.max(0, defense);
        this.strength = strength; // Can be negative if it's a modifier
        this.speed = speed; // Can be negative
        this.maxMana = Math.max(0, maxMana);
        this.currentMana = this.maxMana;
        this.critChance = Math.max(0, Math.min(100, critChance));
        this.critDamage = critDamage; // Can be negative
    }

    // Base stats for an unrecognized or default entity
    public static EntityStats base() {
        return new EntityStats(20.0, 0, 0, 0, 0, 0, 50); // Vanilla Zombie health, 0 for others
    }

    // Getters
    public double getMaxHealth() { return maxHealth; }
    public double getCurrentHealth() { return currentHealth; } // Primarily for initialization tracking
    public int getDefense() { return defense; }
    public int getStrength() { return strength; }
    public int getSpeed() { return speed; }
    public int getMaxMana() { return maxMana; }
    public int getCurrentMana() { return currentMana; }
    public int getCritChance() { return critChance; }
    public int getCritDamage() { return critDamage; }

    // Setters - useful if stats can change dynamically beyond initial load (e.g. mob buffs/debuffs)
    public void setMaxHealth(double maxHealth) { this.maxHealth = Math.max(1.0, maxHealth); }
    public void setCurrentHealth(double currentHealth) { this.currentHealth = Math.max(0, Math.min(this.maxHealth, currentHealth));} // mainly for internal tracking
    public void setDefense(int defense) { this.defense = Math.max(0, defense); }
    public void setStrength(int strength) { this.strength = strength; }
    public void setSpeed(int speed) { this.speed = speed; }
    public void setMaxMana(int maxMana) { this.maxMana = Math.max(0, maxMana); }
    public void setCurrentMana(int currentMana) { this.currentMana = Math.max(0, Math.min(this.maxMana, currentMana));}
    public void setCritChance(int critChance) { this.critChance = Math.max(0, Math.min(100, critChance)); }
    public void setCritDamage(int critDamage) { this.critDamage = critDamage; }

    // Mana methods for mobs if they use it
    public boolean consumeMana(int amount) {
        if (amount <= 0) return true;
        if (this.currentMana >= amount) {
            setCurrentMana(this.currentMana - amount);
            return true;
        }
        return false;
    }
    public void addMana(int amount) {
        setCurrentMana(this.currentMana + amount);
    }
}