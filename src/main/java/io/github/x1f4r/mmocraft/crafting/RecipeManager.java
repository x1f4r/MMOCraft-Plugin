package io.github.x1f4r.mmocraft.crafting;

import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.MMOPlugin;
import io.github.x1f4r.mmocraft.crafting.models.CustomRecipe;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RecipeManager {

    private final MMOCore core;
    private final MMOPlugin plugin;
    private final Logger log;
    private final List<CustomRecipe> customRecipes = new ArrayList<>();
    private final Map<String, Tag<Material>> customMaterialTags = new HashMap<>();
    private final File recipesFile;

    public RecipeManager(MMOCore core) {
        this.core = core;
        this.plugin = core.getPlugin();
        this.log = MMOPlugin.getMMOLogger();
        this.recipesFile = new File(plugin.getDataFolder(), "recipes.yml");
    }

    public void initialize() {
        defineCustomTags();
        loadRecipes();
        log.info("RecipeManager initialized.");
    }

    // Defines custom tags like #LOGS used in recipes.yml
    private void defineCustomTags() {
        customMaterialTags.clear(); // Clear before redefining

        // LOGS Tag (Example) - includes logs, stems, wood, hyphae but excludes stripped versions
        Set<Material> logMaterials = EnumSet.noneOf(Material.class);
        for (Material mat : Material.values()) {
            String name = mat.name();
            // A more robust check might be needed depending on exact desired materials
            if ((name.endsWith("_LOG") || name.endsWith("_STEM") || name.endsWith("_WOOD") || name.endsWith("_HYPHAE"))
                && !name.startsWith("STRIPPED_") && !name.contains("POTTED_"))
            {
                logMaterials.add(mat);
            }
        }
        // Add specific ones if the pattern misses them
        logMaterials.add(Material.CRIMSON_STEM); logMaterials.add(Material.WARPED_STEM);
        logMaterials.add(Material.MANGROVE_ROOTS); // Example: Add roots if desired

        customMaterialTags.put("LOGS", createCustomTag("logs", logMaterials));

        // Add more custom tags here if needed (e.g., PLANKS, WOOL)
        // Set<Material> plankMaterials = Tag.PLANKS.getValues(); // Use Bukkit's tag
        // customMaterialTags.put("PLANKS", createCustomTag("planks", plankMaterials));

        log.info("Defined " + customMaterialTags.size() + " custom material tags for recipes.");
    }

    private Tag<Material> createCustomTag(String keyName, Set<Material> materials) {
        final NamespacedKey tagKey = new NamespacedKey(plugin, "custom_tag_" + keyName.toLowerCase());
        final Set<Material> finalMaterials = Collections.unmodifiableSet(materials); // Make immutable
        return new Tag<Material>() {
            @Override public boolean isTagged(Material item) { return finalMaterials.contains(item); }
            @Override public Set<Material> getValues() { return finalMaterials; }
            @Override public NamespacedKey getKey() { return tagKey; }
            @Override public String toString() { return "CustomTag[" + tagKey.toString() + "]"; }
        };
    }


    public void loadRecipes() {
        customRecipes.clear();
        // File should exist from MMOCore enableServices
        if (!recipesFile.exists()) {
            log.severe("recipes.yml not found! Cannot load custom recipes.");
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(recipesFile);
        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");

        if (recipesSection == null) {
            log.warning("Could not find 'recipes' section in recipes.yml or it's empty.");
            return;
        }

        int loadedCount = 0;
        for (String recipeId : recipesSection.getKeys(false)) {
            ConfigurationSection recipeConfig = recipesSection.getConfigurationSection(recipeId);
            if (recipeConfig == null) continue;

            try {
                CustomRecipe recipe = CustomRecipe.loadFromConfig(core, recipeId, recipeConfig, customMaterialTags);
                if (recipe != null) {
                    customRecipes.add(recipe);
                    loadedCount++;
                    log.finer("Loaded custom recipe: " + recipeId);
                }
            } catch (Exception e) {
                log.log(Level.SEVERE, "Failed to load custom recipe: " + recipeId, e);
            }
        }
        log.info("Loaded " + loadedCount + " custom recipes.");
    }

    /**
     * Finds the first matching CustomRecipe for the given crafting matrix.
     * @param matrix An array of 9 ItemStacks representing the 3x3 grid.
     * @return The matching CustomRecipe, or null if none found.
     */
    public CustomRecipe findMatchingRecipe(ItemStack[] matrix) {
        if (matrix == null || matrix.length != 9) {
            return null;
        }
        for (CustomRecipe recipe : customRecipes) {
            if (recipe.matches(matrix)) {
                return recipe;
            }
        }
        return null; // No custom recipe matched
    }

    public List<CustomRecipe> getAllRecipes() {
        return Collections.unmodifiableList(customRecipes);
    }
}

