package io.github.x1f4r.mmocraft.stats;

public class PlayerStats {

    // --- Base Stats (Defaults or set by commands/other systems) ---
    private int baseStrength = 0;
    private int baseCritChance = 5; // Default base crit chance
    private int baseCritDamage = 50; // Default base crit damage bonus %
    private int baseDefense = 0;
    private int baseMaxMana = 100; // Default base max mana
    private int baseSpeed = 0; // Default base speed %
    private int baseMiningSpeed = 0;
    private int baseForagingSpeed = 0;
    private int baseFishingSpeed = 0;
    private int baseShootingSpeed = 0;

    // --- Calculated Stats (Bonuses from equipment/effects) ---
    // These store only the contribution from equipment/temporary effects
    private int equipmentStrength = 0;
    private int equipmentCritChance = 0;
    private int equipmentCritDamage = 0;
    private int equipmentDefense = 0;
    private int equipmentMaxMana = 0;
    private int equipmentSpeed = 0;
    private int equipmentMiningSpeed = 0;
    private int equipmentForagingSpeed = 0;
    private int equipmentFishingSpeed = 0;
    private int equipmentShootingSpeed = 0;

    // --- Current Resource ---
    private int currentMana; // Always clamped against total MaxMana

    // Private constructor for internal use (e.g., base())
    private PlayerStats(int baseMaxMana, int initialCurrentMana) {
        this.baseMaxMana = Math.max(1, baseMaxMana);
        // Initialize current mana, clamped by initial base max mana
        this.currentMana = Math.max(0, Math.min(this.baseMaxMana, initialCurrentMana));
        // Other base stats default to 0 or predefined values unless loaded
    }

    // Static factory method for default base stats
    public static PlayerStats createDefault() {
        // Creates stats with default base values and 0 equipment bonuses
        return new PlayerStats(100, 100); // Default base 100 max mana, start with full mana
    }

    // --- Getters for TOTAL Stats (Base + Equipment) ---
    // These calculate the final value on the fly
    public int getStrength() { return baseStrength + equipmentStrength; }
    public int getCritChance() { return Math.max(0, Math.min(100, baseCritChance + equipmentCritChance)); } // Clamp total
    public int getCritDamage() { return baseCritDamage + equipmentCritDamage; }
    public int getDefense() { return Math.max(0, baseDefense + equipmentDefense); } // Ensure non-negative
    public int getMaxMana() { return Math.max(1, baseMaxMana + equipmentMaxMana); } // Ensure at least 1
    public int getSpeed() { return baseSpeed + equipmentSpeed; }
    public int getMiningSpeed() { return Math.max(0, baseMiningSpeed + equipmentMiningSpeed); } // Ensure non-negative
    public int getForagingSpeed() { return Math.max(0, baseForagingSpeed + equipmentForagingSpeed); }
    public int getFishingSpeed() { return Math.max(0, baseFishingSpeed + equipmentFishingSpeed); }
    public int getShootingSpeed() { return Math.max(0, baseShootingSpeed + equipmentShootingSpeed); }

    // --- Getters/Setters for BASE Stats (For commands/saving/loading) ---
    public int getBaseMaxMana() { return baseMaxMana; }
    public void setBaseMaxMana(int baseMaxMana) {
        this.baseMaxMana = Math.max(1, baseMaxMana);
        // Clamp current mana whenever base max changes to ensure it's not invalid
        setCurrentMana(this.currentMana);
    }
    // Add getters/setters for other BASE stats if commands need to modify them
    public int getBaseStrength() { return baseStrength; }
    public void setBaseStrength(int baseStrength) { this.baseStrength = baseStrength; }
    public int getBaseCritChance() { return baseCritChance; }
    public void setBaseCritChance(int baseCritChance) { this.baseCritChance = baseCritChance; }
    public int getBaseCritDamage() { return baseCritDamage; }
    public void setBaseCritDamage(int baseCritDamage) { this.baseCritDamage = baseCritDamage; }
    public int getBaseDefense() { return baseDefense; }
    public void setBaseDefense(int baseDefense) { this.baseDefense = baseDefense; }
    public int getBaseSpeed() { return baseSpeed; }
    public void setBaseSpeed(int baseSpeed) { this.baseSpeed = baseSpeed; }
    public int getBaseMiningSpeed() { return baseMiningSpeed; }
    public void setBaseMiningSpeed(int baseMiningSpeed) { this.baseMiningSpeed = baseMiningSpeed; }
    public int getBaseForagingSpeed() { return baseForagingSpeed; }
    public void setBaseForagingSpeed(int baseForagingSpeed) { this.baseForagingSpeed = baseForagingSpeed; }
    public int getBaseFishingSpeed() { return baseFishingSpeed; }
    public void setBaseFishingSpeed(int baseFishingSpeed) { this.baseFishingSpeed = baseFishingSpeed; }
    public int getBaseShootingSpeed() { return baseShootingSpeed; }
    public void setBaseShootingSpeed(int baseShootingSpeed) { this.baseShootingSpeed = baseShootingSpeed; }


    // --- Getters/Setters for EQUIPMENT Bonuses (Used ONLY by PlayerStatsManager) ---
    // Changed from protected to public
    public int getEquipmentStrength() { return equipmentStrength; }
    public int getEquipmentCritChance() { return equipmentCritChance; }
    public int getEquipmentCritDamage() { return equipmentCritDamage; }
    public int getEquipmentDefense() { return equipmentDefense; }
    public int getEquipmentMaxMana() { return equipmentMaxMana; }
    public int getEquipmentSpeed() { return equipmentSpeed; }
    public int getEquipmentMiningSpeed() { return equipmentMiningSpeed; }
    public int getEquipmentForagingSpeed() { return equipmentForagingSpeed; }
    public int getEquipmentFishingSpeed() { return equipmentFishingSpeed; }
    public int getEquipmentShootingSpeed() { return equipmentShootingSpeed; }

    public void setEquipmentStrength(int equipmentStrength) { this.equipmentStrength = equipmentStrength; }
    public void setEquipmentCritChance(int equipmentCritChance) { this.equipmentCritChance = equipmentCritChance; }
    public void setEquipmentCritDamage(int equipmentCritDamage) { this.equipmentCritDamage = equipmentCritDamage; }
    public void setEquipmentDefense(int equipmentDefense) { this.equipmentDefense = equipmentDefense; }
    public void setEquipmentMaxMana(int equipmentMaxMana) { this.equipmentMaxMana = equipmentMaxMana; }
    public void setEquipmentSpeed(int equipmentSpeed) { this.equipmentSpeed = equipmentSpeed; }
    public void setEquipmentMiningSpeed(int equipmentMiningSpeed) { this.equipmentMiningSpeed = equipmentMiningSpeed; }
    public void setEquipmentForagingSpeed(int equipmentForagingSpeed) { this.equipmentForagingSpeed = equipmentForagingSpeed; }
    public void setEquipmentFishingSpeed(int equipmentFishingSpeed) { this.equipmentFishingSpeed = equipmentFishingSpeed; }
    public void setEquipmentShootingSpeed(int equipmentShootingSpeed) { this.equipmentShootingSpeed = equipmentShootingSpeed; }

    // --- Current Mana Management ---
    public int getCurrentMana() { return currentMana; }
    public void setCurrentMana(int currentMana) {
        // Clamp against the TOTAL max mana (base + equipment)
        this.currentMana = Math.max(0, Math.min(this.getMaxMana(), currentMana));
    }
    public void addMana(int amount) {
        setCurrentMana(this.currentMana + amount);
    }
    public boolean consumeMana(int amount) {
        if (amount <= 0) return true; // Consuming 0 or negative is always successful
        if (this.currentMana >= amount) {
            setCurrentMana(this.currentMana - amount);
            return true;
        }
        return false; // Not enough mana
    }
}
