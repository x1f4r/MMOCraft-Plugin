package io.github.x1f4r.mmocraft.tools;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Stores data related to a player's active custom mining task on a specific block.
 * This helps manage custom block breaking speed and animations.
 */
public class MiningTaskData {
    @NotNull public final Location blockLocation;
    @NotNull public final Material originalBlockType;
    @NotNull public final ItemStack toolUsedSnapshot; // A clone of the tool when mining initiated/updated
    public float accumulatedProgress;         // Progress towards breaking (0.0 to 1.0)
    @NotNull public BukkitTask bukkitTaskInstance; // The MiningProgressRunnable instance handling this task
    public long lastProgressApplicationTime; // System.currentTimeMillis() of the last progress tick

    /**
     * Constructs a new MiningTaskData instance.
     * @param blockLocation The location of the block being mined.
     * @param originalBlockType The material of the block when mining started.
     * @param toolUsedSnapshot A snapshot (clone) of the tool used to initiate mining.
     * @param bukkitTaskInstance The BukkitTask (MiningProgressRunnable) associated with this mining operation.
     */
    public MiningTaskData(@NotNull Location blockLocation, @NotNull Material originalBlockType,
                          @NotNull ItemStack toolUsedSnapshot, @NotNull BukkitTask bukkitTaskInstance) {
        this.blockLocation = Objects.requireNonNull(blockLocation, "Block location cannot be null.");
        this.originalBlockType = Objects.requireNonNull(originalBlockType, "Original block type cannot be null.");
        // Ensure it's a true snapshot by cloning, especially if toolUsedSnapshot might be a direct reference
        this.toolUsedSnapshot = Objects.requireNonNull(toolUsedSnapshot, "Tool snapshot cannot be null.").clone();
        this.bukkitTaskInstance = Objects.requireNonNull(bukkitTaskInstance, "BukkitTask instance cannot be null.");
        this.accumulatedProgress = 0.0f;
        this.lastProgressApplicationTime = System.currentTimeMillis(); // Initialize time
    }

    @Override
    public String toString() {
        return "MiningTaskData{" +
                "block=" + originalBlockType + "@" + blockLocation.toVector() +
                ", tool=" + toolUsedSnapshot.getType() +
                ", progress=" + String.format("%.2f", accumulatedProgress) +
                ", taskActive=" + !bukkitTaskInstance.isCancelled() +
                '}';
    }
}