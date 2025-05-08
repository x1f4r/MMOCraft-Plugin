package io.github.x1f4r.mmocraft.stats;

public class EntityStats {

    private double maxHealth;
    // currentHealth is managed by the Bukkit entity itself after initial set
    private int defense;
    private int strength;
    private int speed; // Percentage modifier
    private int maxMana;
    // currentMana might be tracked here if mobs use mana, or just use maxMana
    private int critChance; // 0-100
    private int critDamage; // Percentage bonus

    // Constructor used by EntityStatsManager when loading from config
    public EntityStats(double maxHealth, int defense, int strength, int speed, int maxMana, int critChance, int critDamage) {
        this.maxHealth = Math.max(1.0, maxHealth);
        this.defense = Math.max(0, defense);
        this.strength = strength;
        this.speed = speed;
        this.maxMana = Math.max(0, maxMana);
        this.critChance = Math.max(0, Math.min(100, critChance));
        this.critDamage = critDamage;
    }

    // Base stats for an unrecognized or default entity (can be adjusted)
    public static EntityStats createDefault() {
        // Represents typical base stats before any config is applied.
        // Max health might default to the entity's vanilla default later if needed.
        return new EntityStats(20.0, 0, 0, 0, 0, 0, 0); // Example defaults
    }

    // Getters
    public double getMaxHealth() { return maxHealth; }
    public int getDefense() { return defense; }
    public int getStrength() { return strength; }
    public int getSpeed() { return speed; }
    public int getMaxMana() { return maxMana; }
    public int getCritChance() { return critChance; }
    public int getCritDamage() { return critDamage; }

    // Setters might be needed if stats can change dynamically (buffs/debuffs)
    // but for config-loaded stats, they are often immutable after creation.
}

