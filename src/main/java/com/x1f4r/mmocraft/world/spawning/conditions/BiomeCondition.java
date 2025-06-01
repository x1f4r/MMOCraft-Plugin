package com.x1f4r.mmocraft.world.spawning.conditions;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashSet; // Added
import java.util.List;
import java.util.Set;
// import java.util.EnumSet; // Removed

public class BiomeCondition implements SpawnCondition {

    private final Set<Biome> allowedBiomes;

    public BiomeCondition(Set<Biome> allowedBiomes) {
        this.allowedBiomes = Collections.unmodifiableSet(new HashSet<>(allowedBiomes)); // Changed to HashSet
    }

    public BiomeCondition(Biome... biomes) {
        // Explicitly create a List, which is a Collection, for EnumSet.copyOf
        this.allowedBiomes = Collections.unmodifiableSet(new HashSet<>(List.of(biomes))); // Changed to HashSet
    }

    @Override
    public boolean check(Location location, World world, Player nearestPlayer) {
        if (location == null || world == null) {
            return false;
        }
        // Ensure the location's world matches the passed world context if necessary,
        // or rely on Bukkit to handle location.getBiome() correctly based on its world.
        // Biome biome = world.getBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ()); // Bukkit API for specific coords
        Biome biome = location.getBlock().getBiome(); // Simpler way
        return allowedBiomes.contains(biome);
    }

    public Set<Biome> getAllowedBiomes() {
        return allowedBiomes;
    }
}
