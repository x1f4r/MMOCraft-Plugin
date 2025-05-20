package io.github.x1f4r.mmocraft.abilities.list;

import io.github.x1f4r.mmocraft.abilities.AbstractItemAbility;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.items.CustomItem;
import io.github.x1f4r.mmocraft.player.stats.CalculatedPlayerStats;
import io.github.x1f4r.mmocraft.services.AbilityService; // Added import
import io.github.x1f4r.mmocraft.services.NBTService;
import io.github.x1f4r.mmocraft.services.PlayerStatsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action; // For activation action
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random; // Added import
import java.util.Set;

public class InstantShotBowAbility extends AbstractItemAbility {

    private static final Random random = new Random(); // Added random field

    public InstantShotBowAbility() {
        // ID, Display Name, Mana Cost (can be 0), Cooldown (dynamic based on shooting speed)
        super("instant_shot_bow", "Instant Shot", 10, 0); // Cooldown is dynamic
    }

    @Override
    public Set<Action> getActivationActions() {
        // This ability activates on LEFT_CLICK as well, like Terminator in SkyBlock
        return Set.of(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK, Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK);
    }

    @Override
    public int getDefaultCooldownTicks() {
        // This ability's cooldown is dynamically calculated based on shooting speed.
        // A base could be set here, or on the item, and then modified.
        // Returning 0 makes AbilityService skip its fixed cooldown check if not overridden on item.
        return 0;
    }


    @Override
    public List<Component> getDescription(CustomItem itemTemplate) {
        // Cooldown is dynamic, so "Shooting Speed" stat implies cooldown.
        return List.of(
                Component.text("Instantly shoots an arrow.", NamedTextColor.GRAY),
                Component.text("Consumes one arrow from your inventory.", NamedTextColor.GRAY),
                Component.text("Cooldown is based on your Shooting Speed.", NamedTextColor.DARK_AQUA)
        );
    }

    @Override
    public boolean canExecute(Player player, ItemStack itemStack, CustomItem itemTemplate, MMOCore core) {
        // Check for arrows
        if (!player.getInventory().contains(Material.ARROW) && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            player.sendActionBar(Component.text("No arrows!", NamedTextColor.RED));
            return false;
        }

        // Dynamic Cooldown Check based on Shooting Speed
        PlayerStatsService statsService = core.getService(PlayerStatsService.class);
        CalculatedPlayerStats playerStats = statsService.getCalculatedStats(player);
        int shootingSpeedStat = playerStats.shootingSpeed(); // Higher is faster

        // Example: 100 shooting speed = 0.5s cooldown (10 ticks). 0 speed = 2s (40 ticks).
        // Cooldown (ticks) = BaseCooldown / (1 + ShootingSpeedFactor)
        // Let BaseCooldown = 40 ticks (2 seconds)
        // ShootingSpeedFactor = shootingSpeedStat / 100.0
        // Min cooldown e.g. 5 ticks (0.25s)

        int baseItemCooldown = itemTemplate.getGenericNbtValue("BASE_SHOOT_COOLDOWN_TICKS", 40, Integer.class); // e.g. 2 seconds
        double speedFactor = 1.0 + (Math.max(0, shootingSpeedStat) / 100.0); // Ensure positive factor
        int dynamicCooldownTicks = Math.max(5, (int) (baseItemCooldown / speedFactor)); // Min 5 ticks

        AbilityService abilityService = core.getService(AbilityService.class);
        if (abilityService.isOnCooldown(getAbilityId(), player.getUniqueId(), dynamicCooldownTicks)) {
            long remainingMillis = abilityService.getRemainingCooldownMillis(getAbilityId(), player.getUniqueId(), dynamicCooldownTicks);
            player.sendActionBar(Component.text("Bow on cooldown! (" + String.format("%.1f", remainingMillis / 1000.0) + "s)", NamedTextColor.RED));
            return false;
        }

        return true;
    }

    @Override
    public boolean execute(Player player, ItemStack bowStack, CustomItem bowTemplate, MMOCore core) {
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            if (!player.getInventory().removeItem(new ItemStack(Material.ARROW, 1)).isEmpty()) {
                // This should not happen if canExecute passed, but as a safeguard.
                player.sendActionBar(Component.text("Failed to consume arrow!", NamedTextColor.RED));
                return false; // Failed to consume arrow
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f / (random.nextFloat() * 0.4f + 0.8f));

        Arrow arrow = player.launchProjectile(Arrow.class);
        arrow.setShooter(player);
        // Velocity is determined by ToolProficiencyService based on shooting speed for drawn bows.
        // For instant shot, we might want a fixed high velocity or one also scaled by shooting speed.
        // Let's use a fixed high velocity for "instant" feel, but configurable.
        double velocityMultiplier = bowTemplate.getGenericNbtValue("INSTANT_SHOT_VELOCITY_MULT", 3.0, Double.class);
        arrow.setVelocity(player.getEyeLocation().getDirection().multiply(velocityMultiplier));
        arrow.setPickupStatus(Arrow.PickupStatus.CREATIVE_ONLY); // Or DISALLOWED based on config

        // Tag arrow with bow's NBT for CombatService (damage, true damage, etc.)
        ItemMeta bowMeta = bowStack.getItemMeta();
        if (bowMeta != null) {
            PersistentDataContainer bowPdc = bowMeta.getPersistentDataContainer();
            PersistentDataContainer arrowPdc = arrow.getPersistentDataContainer();

            String customBowId = NBTService.get(bowPdc, NBTService.ITEM_ID_KEY, PersistentDataType.STRING, null);
            if (customBowId != null) {
                NBTService.set(arrowPdc, NBTService.PROJECTILE_SOURCE_ITEM_ID_KEY, PersistentDataType.STRING, customBowId); // Prefixed with NBTService
            }
            Integer projDamageMultiplier = NBTService.get(bowPdc, NBTService.PROJECTILE_DAMAGE_MULTIPLIER, PersistentDataType.INTEGER, null);
            if (projDamageMultiplier != null) {
                NBTService.set(arrowPdc, NBTService.PROJECTILE_DAMAGE_MULTIPLIER, PersistentDataType.INTEGER, projDamageMultiplier);
            }
            Byte trueDamageFlag = NBTService.get(bowPdc, NBTService.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE, null);
            if (trueDamageFlag != null && trueDamageFlag == (byte)1) {
                NBTService.set(arrowPdc, NBTService.TRUE_DAMAGE_FLAG_KEY, PersistentDataType.BYTE, (byte)1);
            }
            // Transfer other relevant NBT like critical chance/damage modifiers if bow grants them to projectile
        }

        // Set the dynamic cooldown after successful execution
        PlayerStatsService statsService = core.getService(PlayerStatsService.class);
        CalculatedPlayerStats playerStats = statsService.getCalculatedStats(player);
        int shootingSpeedStat = playerStats.shootingSpeed();
        int baseItemCooldown = bowTemplate.getGenericNbtValue("BASE_SHOOT_COOLDOWN_TICKS", 40, Integer.class);
        double speedFactor = 1.0 + (Math.max(0, shootingSpeedStat) / 100.0);
        int dynamicCooldownTicks = Math.max(5, (int) (baseItemCooldown / speedFactor));

        AbilityService abilityService = core.getService(AbilityService.class);
        abilityService.setCooldown(getAbilityId(), player.getUniqueId(), dynamicCooldownTicks); // Use specific setCooldown

        return true;
    }
}