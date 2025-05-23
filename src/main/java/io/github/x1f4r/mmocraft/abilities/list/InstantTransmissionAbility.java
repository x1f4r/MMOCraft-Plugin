package io.github.x1f4r.mmocraft.abilities.list;

import io.github.x1f4r.mmocraft.abilities.AbstractItemAbility;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.items.CustomItem;
import io.github.x1f4r.mmocraft.services.AbilityService; // Added import
import io.github.x1f4r.mmocraft.services.NBTService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Set;

public class InstantTransmissionAbility extends AbstractItemAbility {
    private static final Set<Material> UNSAFE_DESTINATION_MATERIALS = Set.of(
            Material.LAVA, Material.FIRE, Material.MAGMA_BLOCK, Material.CACTUS, Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE, Material.POINTED_DRIPSTONE, Material.POWDER_SNOW
    );

    public InstantTransmissionAbility() {
        super("instant_transmission", "Instant Transmission", 50, 0); // Mana: 50, Cooldown: 0 ticks
    }

    @Override
    public List<Component> getDescription(CustomItem itemTemplate) {
        int range = itemTemplate.getGenericNbtValue(AbilityService.TELEPORT_RANGE_KEY_STRING, 8, Integer.class); // Changed NBTService to AbilityService
        return List.of(
                Component.text("Teleport " + range + " blocks forward.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        );
    }

    @Override
    public boolean canExecute(Player player, ItemStack itemStack, CustomItem itemTemplate, MMOCore core) {
        return true; // No special preconditions
    }

    @Override
    public boolean execute(Player player, ItemStack itemStack, CustomItem itemTemplate, MMOCore core) {
        int teleportRange = itemTemplate.getGenericNbtValue(AbilityService.TELEPORT_RANGE_KEY_STRING, 8, Integer.class); // Changed NBTService to AbilityService
        final int MAX_SAFETY_ITERATIONS = 5; // How many times to step back to find a safe spot

        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize();
        Location targetBlockLoc = null; // The block *before* which the player will land

        // Ray trace to find the first non-passable block or max range
        RayTraceResult rayTrace = player.getWorld().rayTraceBlocks(eyeLoc, direction, teleportRange, FluidCollisionMode.NEVER, true);

        if (rayTrace != null && rayTrace.getHitBlock() != null) {
            // Hit a block, target the block *before* it
            targetBlockLoc = rayTrace.getHitBlock().getLocation().subtract(direction.multiply(0.1)); // Step back slightly from hit face
        } else {
            // No block hit, teleport full range
            targetBlockLoc = eyeLoc.clone().add(direction.multiply(teleportRange));
        }

        // Adjust targetBlockLoc to be a valid feet location, stepping back if unsafe
        Location safeTeleportLocation = null;
        for (int i = 0; i < MAX_SAFETY_ITERATIONS; i++) {
            // Check if the 2 blocks at targetBlockLoc (feet and head) are passable
            Location feet = targetBlockLoc.clone().setY(Math.floor(targetBlockLoc.getY())); // Ensure Y is at block base
            Location head = feet.clone().add(0, 1, 0);
            Block blockBelowFeet = feet.clone().subtract(0, 1, 0).getBlock();

            if (isPassable(feet.getBlock()) && isPassable(head.getBlock()) &&
                    (blockBelowFeet.getType().isSolid() || blockBelowFeet.isLiquid()) && // Must land on something solid or liquid
                    !UNSAFE_DESTINATION_MATERIALS.contains(feet.getBlock().getType()) &&
                    !UNSAFE_DESTINATION_MATERIALS.contains(head.getBlock().getType()) &&
                    !UNSAFE_DESTINATION_MATERIALS.contains(blockBelowFeet.getType())) {
                safeTeleportLocation = feet; // Found a safe spot
                break;
            }
            // If not safe, step back along the inverse direction
            targetBlockLoc.subtract(direction.multiply(0.5)); // Step back half a block
        }


        if (safeTeleportLocation != null) {
            // Finalize location: center on block, preserve player's original pitch/yaw
            safeTeleportLocation.setX(safeTeleportLocation.getBlockX() + 0.5);
            safeTeleportLocation.setY(safeTeleportLocation.getBlockY()); // Already at feet level
            safeTeleportLocation.setZ(safeTeleportLocation.getBlockZ() + 0.5);
            safeTeleportLocation.setPitch(player.getLocation().getPitch());
            safeTeleportLocation.setYaw(player.getLocation().getYaw());

            player.teleport(safeTeleportLocation);
            player.setFallDistance(0f); // Reset fall distance after teleport

            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.3f);
            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0,1,0), 40, 0.2, 0.4, 0.2, 0.05);
            return true;
        } else {
            player.sendActionBar(Component.text("Destination blocked or unsafe!", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.0f);
            return false; // Indicate execution failed (mana will be refunded by AbilityService)
        }
    }

    private boolean isPassable(Block block) {
        return block.isPassable() && !block.isLiquid(); // Treat liquids as non-passable for teleport *into*
    }
}