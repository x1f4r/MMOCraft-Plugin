package io.github.x1f4r.mmocraft.abilities.list;

import io.github.x1f4r.mmocraft.abilities.AbstractItemAbility;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.items.CustomItem;
import io.github.x1f4r.mmocraft.player.stats.CalculatedPlayerStats;
import io.github.x1f4r.mmocraft.services.NBTService; // For ability parameter keys
import io.github.x1f4r.mmocraft.services.PlayerStatsService;
import io.github.x1f4r.mmocraft.services.CombatService; // To deal damage if you want custom source/type
import io.github.x1f4r.mmocraft.services.VisualFeedbackService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Objects;

public class DragonFuryAbility extends AbstractItemAbility {

    public DragonFuryAbility() {
        // ID, Display Name, Default Mana, Default Cooldown Ticks
        super("dragon_fury", "Dragon's Fury", 100, 20); // 1 second cooldown
    }

    @Override
    public List<Component> getDescription(CustomItem itemTemplate) {
        double baseDmg = itemTemplate.getGenericNbtValue(NBTService.ABILITY_DAMAGE_PARAM_SUFFIX.substring(1), 250.0, Double.class); // remove leading "_"
        double strScale = itemTemplate.getGenericNbtValue("dragon_fury_strength_scaling", 0.75, Double.class); // Example custom key
        double range = itemTemplate.getGenericNbtValue(NBTService.ABILITY_RANGE_PARAM_SUFFIX.substring(1), 5.0, Double.class);

        return List.of(
                Component.text("Unleash a wave of draconic energy in a cone,", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("hitting enemies up to ", NamedTextColor.GRAY).append(Component.text(String.format("%.1f",range) + " blocks", NamedTextColor.GREEN))
                        .append(Component.text(" away.", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false),
                Component.text("Deals ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("%.0f", baseDmg), NamedTextColor.RED))
                        .append(Component.text(" + " + (int)(strScale*100) + "% Strength as damage.", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false)
        );
    }

    @Override
    public boolean canExecute(Player player, ItemStack itemStack, CustomItem itemTemplate, MMOCore core) {
        return true; // No special preconditions for this ability
    }

    @Override
    public boolean execute(Player player, ItemStack itemStack, CustomItem itemTemplate, MMOCore core) {
        PlayerStatsService playerStatsService = core.getService(PlayerStatsService.class);
        VisualFeedbackService visualFeedback = core.getService(VisualFeedbackService.class); // For particles/sounds

        CalculatedPlayerStats stats = playerStatsService.getCalculatedStats(player);

        double baseDamage = itemTemplate.getGenericNbtValue(NBTService.ABILITY_DAMAGE_PARAM_SUFFIX.substring(1), 200.0, Double.class);
        double strengthScaling = itemTemplate.getGenericNbtValue("dragon_fury_strength_scaling", 0.5, Double.class);
        double range = itemTemplate.getGenericNbtValue(NBTService.ABILITY_RANGE_PARAM_SUFFIX.substring(1), 6.0, Double.class);
        double coneAngleDegrees = itemTemplate.getGenericNbtValue("dragon_fury_cone_angle", 60.0, Double.class); // Example: 60 degree cone

        double abilityFinalDamage = baseDamage + (stats.strength() * strengthScaling);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
        // Particle effect for cone
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize();
        for (int i = 0; i < 30; i++) { // Number of particle streams
            double randomAngle = (random.nextDouble() - 0.5) * Math.toRadians(coneAngleDegrees); // Spread within cone
            double randomPitch = (random.nextDouble() - 0.5) * Math.toRadians(coneAngleDegrees / 2); // Vertical spread

            Vector particleDir = direction.clone();
            // Rotate around Y axis for horizontal spread
            particleDir.rotateAroundY(randomAngle);
            // Rotate around an axis perpendicular to current direction and Y for vertical spread
            Vector perpendicularAxis = direction.clone().crossProduct(new Vector(0,1,0)).normalize();
            if (perpendicularAxis.lengthSquared() < 0.01) perpendicularAxis = direction.clone().crossProduct(new Vector(1,0,0)).normalize(); // Fallback if looking straight up/down
            if (perpendicularAxis.lengthSquared() > 0.01) particleDir.rotateAroundAxis(perpendicularAxis, randomPitch);


            for (double d = 1; d <= range; d += 0.5) {
                Location particlePoint = eyeLoc.clone().add(particleDir.clone().multiply(d));
                player.getWorld().spawnParticle(Particle.FLAME, particlePoint, 1, 0, 0, 0, 0.01);
                if (d > range/2 && random.nextInt(3)==0) player.getWorld().spawnParticle(Particle.LAVA, particlePoint, 1, 0,0,0,0);
            }
        }


        int hitCount = 0;
        for (LivingEntity entity : player.getWorld().getNearbyEntities(eyeLoc, range, range, range,
                e -> e instanceof LivingEntity && !e.equals(player) && !(e instanceof ArmorStand) && e.isValid() && !e.isDead() && player.hasLineOfSight(e))) {

            Vector toEntity = entity.getLocation().add(0, entity.getHeight()/2, 0).subtract(eyeLoc).toVector().normalize();
            double angleToEntity = Math.toDegrees(toEntity.angle(direction));

            if (angleToEntity <= coneAngleDegrees / 2.0) { // Check if entity is within the cone
                // For ability damage, we typically want to bypass the standard CombatService weapon calculations
                // and apply damage directly, or have a specific CombatService method for ability damage.
                // This ability damage IS the final value before any universal damage reductions (like Protection enchant).
                entity.damage(abilityFinalDamage, player); // Player is the source for Bukkit events

                if (visualFeedback != null) { // Show custom indicator for ability damage
                    visualFeedback.showDamageIndicator(entity, abilityFinalDamage, false, false); // Not a weapon crit, not inherently true damage unless specified
                }

                if (entity.isValid() && !entity.isDead()) { // Check if not killed
                    entity.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, entity.getEyeLocation(), 5, 0.2, 0.2, 0.2, 0.05);
                }
                hitCount++;
            }
        }

        if (hitCount > 0) {
            player.sendActionBar(Component.text("Dragon's Fury hit " + hitCount + " enemies for " + String.format("%.0f", abilityFinalDamage) + " damage!", NamedTextColor.GOLD));
        } else {
            player.sendActionBar(Component.text("Dragon's Fury missed!", NamedTextColor.GRAY));
        }
        return true;
    }
}