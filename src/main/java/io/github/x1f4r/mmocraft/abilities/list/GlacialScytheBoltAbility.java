package io.github.x1f4r.mmocraft.abilities.list;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.abilities.AbstractItemAbility;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.items.CustomItem;
import io.github.x1f4r.mmocraft.services.NBTService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball; // Using Snowball as a base for the projectile
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class GlacialScytheBoltAbility extends AbstractItemAbility {

    public GlacialScytheBoltAbility() {
        super("glacial_ice_bolt", "Glacial Ice Bolt", 75, 100); // Mana 75, Cooldown 5s (100 ticks)
    }

    @Override
    public List<Component> getDescription(CustomItem itemTemplate) {
        int damage = itemTemplate.getGenericNbtValue(NBTService.ABILITY_DAMAGE_PARAM_SUFFIX.substring(1), 150, Integer.class);
        int slowTicks = itemTemplate.getGenericNbtValue(NBTService.ABILITY_DURATION_PARAM_SUFFIX.substring(1), 100, Integer.class);
        int slowAmp = itemTemplate.getGenericNbtValue(NBTService.ABILITY_AMPLIFIER_PARAM_SUFFIX.substring(1), 1, Integer.class); // Slowness II (amplifier 1)
        boolean isAoe = itemTemplate.getGenericNbtValue("IS_AOE_ON_HIT", false, Boolean.class); // Custom NBT param

        List<Component> desc = new java.util.ArrayList<>();
        desc.add(Component.text("Launches a chilling bolt that deals ", NamedTextColor.GRAY)
                .append(Component.text(damage, NamedTextColor.AQUA))
                .append(Component.text(" Ability Damage.", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
        desc.add(Component.text("Applies Slowness " + (slowAmp + 1) + " for " + String.format("%.1fs", slowTicks / 20.0) + ".", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (isAoe) {
            desc.add(Component.text("Explodes on impact, affecting nearby enemies.", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
        }
        return desc;
    }

    @Override
    public boolean canExecute(Player player, ItemStack itemStack, CustomItem itemTemplate, MMOCore core) {
        return true; // No special preconditions
    }

    @Override
    public boolean execute(Player player, ItemStack itemStack, CustomItem itemTemplate, MMOCore core) {
        // Fetch ability parameters from the item's template (generic NBT)
        int abilityDamage = itemTemplate.getGenericNbtValue(NBTService.ABILITY_DAMAGE_PARAM_SUFFIX.substring(1), 150, Integer.class);
        int slowDuration = itemTemplate.getGenericNbtValue(NBTService.ABILITY_DURATION_PARAM_SUFFIX.substring(1), 100, Integer.class);
        int slowAmplifier = itemTemplate.getGenericNbtValue(NBTService.ABILITY_AMPLIFIER_PARAM_SUFFIX.substring(1), 1, Integer.class);
        boolean isAoe = itemTemplate.getGenericNbtValue("IS_AOE_ON_HIT", false, Boolean.class);
        double aoeRadius = itemTemplate.getGenericNbtValue("AOE_RADIUS", 3.0, Double.class);
        int aoeDamage = itemTemplate.getGenericNbtValue("AOE_DAMAGE", abilityDamage / 2, Integer.class); // AOE damage if specified

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.8f);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getEyeLocation().add(player.getLocation().getDirection()), 50, 0.3, 0.3, 0.3, 0.05);

        Snowball iceBolt = player.launchProjectile(Snowball.class);
        iceBolt.setShooter(player);
        iceBolt.setVelocity(player.getEyeLocation().getDirection().multiply(2.0)); // Adjust projectile speed
        // iceBolt.setGravity(false); // Optional: make it fly straight for a bit

        // Tag the projectile with data for AbilityProjectileListener
        PersistentDataContainer projPdc = iceBolt.getPersistentDataContainer();
        NBTService.set(projPdc, NBTService.PROJECTILE_IS_ABILITY_BOLT_FLAG, PersistentDataType.BYTE, (byte) 1);
        NBTService.set(projPdc, NBTService.PROJECTILE_SOURCE_ABILITY_ID_KEY, PersistentDataType.STRING, getAbilityId());
        NBTService.set(projPdc, NBTService.PROJECTILE_CUSTOM_DAMAGE, PersistentDataType.INTEGER, abilityDamage);
        NBTService.set(projPdc, NBTService.PROJECTILE_CUSTOM_EFFECT_TYPE, PersistentDataType.STRING, "SLOW"); // PotionEffectType.SLOW.getName()
        NBTService.set(projPdc, NBTService.PROJECTILE_CUSTOM_EFFECT_DURATION, PersistentDataType.INTEGER, slowDuration);
        NBTService.set(projPdc, NBTService.PROJECTILE_CUSTOM_EFFECT_AMPLIFIER, PersistentDataType.INTEGER, slowAmplifier);
        if (isAoe) {
            NBTService.set(projPdc, NBTService.PROJECTILE_CUSTOM_AOE_FLAG, PersistentDataType.BYTE, (byte) 1);
            NBTService.set(projPdc, NBTService.PROJECTILE_CUSTOM_AOE_RADIUS_KEY, PersistentDataType.DOUBLE, aoeRadius);
            NBTService.set(projPdc, NBTService.PROJECTILE_CUSTOM_AOE_DAMAGE_KEY, PersistentDataType.INTEGER, aoeDamage);
        }

        // Add a particle trail to the bolt
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!iceBolt.isValid() || iceBolt.isDead() || iceBolt.isOnGround() || ticks > 200) { // Max 10 seconds flight
                    if (iceBolt.isValid() && !iceBolt.isDead()) { // If it just landed and wasn't processed by hit event
                        iceBolt.getWorld().spawnParticle(Particle.SNOWBALL, iceBolt.getLocation(), 10, 0.2,0.2,0.2,0.1);
                        iceBolt.remove(); // Clean up if it just landed without hitting anything processable
                    }
                    this.cancel();
                    return;
                }
                iceBolt.getWorld().spawnParticle(Particle.ITEM_CRACK, iceBolt.getLocation(), 3, 0.05, 0.05, 0.05, 0.01, new ItemStack(Material.ICE));
                iceBolt.getWorld().spawnParticle(Particle.SNOWFLAKE, iceBolt.getLocation(), 1, 0, 0, 0, 0);
                ticks++;
            }
        }.runTaskTimer(core.getPlugin(), 0L, 1L); // Run every tick

        return true; // Ability successfully initiated
    }
}