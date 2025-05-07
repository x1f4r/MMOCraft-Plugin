package io.github.x1f4r.mmocraft.crafting;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.crafting.models.CustomRecipe;
import io.github.x1f4r.mmocraft.items.ItemManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack; // <-- Import ItemStack

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class RecipeManager {

    private final MMOCraft plugin;
    private final List<CustomRecipe> customRecipes = new ArrayList<>();
    private final Map<String, Tag<Material>> customMaterialTags = new HashMap<>();

    public RecipeManager(MMOCraft plugin) {
        this.plugin = plugin;
        defineCustomTags();
        loadRecipes();
    }

    private void defineCustomTags() {
        Set<Material> logMaterials = EnumSet.noneOf(Material.class);
        for (Material mat : Material.values()) {
            String name = mat.name();
            if (name.endsWith("_LOG") || name.endsWith("_STEM") || name.endsWith("_WOOD") || name.endsWith("_HYPHAE")) {
                if (!name.contains("STRIPPED_") && !name.contains("POTTED_") && !name.endsWith("_PLANKS")) {
                    logMaterials.add(mat);
                }
            }
        }
        // Ensure specific stems are included if the pattern missed them
        logMaterials.add(Material.CRIMSON_STEM);
        logMaterials.add(Material.WARPED_STEM);
        logMaterials.add(Material.OAK_LOG); // Add common ones explicitly if needed
        logMaterials.add(Material.SPRUCE_LOG);
        logMaterials.add(Material.BIRCH_LOG);
        logMaterials.add(Material.JUNGLE_LOG);
        logMaterials.add(Material.ACACIA_LOG);
        logMaterials.add(Material.DARK_OAK_LOG);
        logMaterials.add(Material.MANGROVE_LOG);
        logMaterials.add(Material.CHERRY_LOG);


        customMaterialTags.put("LOGS", new Tag<Material>() {
            @Override public boolean isTagged(Material item) { return logMaterials.contains(item); }
            @Override public Set<Material> getValues() { return Collections.unmodifiableSet(logMaterials); }
            @Override public NamespacedKey getKey() { return new NamespacedKey(plugin, "logs_custom_tag"); }
            @Override public String toString() { return "CustomTag[LOGS]"; }
        });
    }

    public void loadRecipes() {
        customRecipes.clear();
        ItemManager itemManager = plugin.getItemManager();
        if (itemManager == null) {
            plugin.getLogger().severe("ItemManager is null during RecipeManager load! Cannot load recipes.");
            return;
        }

        File recipesFile = new File(plugin.getDataFolder(), "recipes.yml");
        if (!recipesFile.exists()) {
            plugin.getLogger().info("recipes.yml not found, saving default.");
            plugin.saveResource("recipes.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(recipesFile);
        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");

        if (recipesSection == null) {
            plugin.getLogger().warning("Could not find 'recipes' section in recipes.yml!");
            return;
        }

        for (String recipeId : recipesSection.getKeys(false)) {
            ConfigurationSection recipeConfig = recipesSection.getConfigurationSection(recipeId);
            if (recipeConfig == null) continue;

            try {
                CustomRecipe recipe = CustomRecipe.loadFromConfig(plugin, itemManager, recipeId, recipeConfig, customMaterialTags);
                if (recipe != null) {
                    customRecipes.add(recipe);
                    plugin.getLogger().info("Loaded custom recipe: " + recipeId);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load custom recipe: " + recipeId, e);
            }
        }
        plugin.getLogger().info("Loaded " + customRecipes.size() + " custom recipes.");
    }

    // MODIFIED: Now accepts ItemStack[] matrix directly
    public CustomRecipe findMatchingRecipe(ItemStack[] matrix) {
        if (matrix == null || matrix.length != 9) { // Basic validation
            return null;
        }
        for (CustomRecipe recipe : customRecipes) {
            if (recipe.matches(matrix)) { // Pass the matrix to CustomRecipe's matches method
                return recipe;
            }
        }
        return null;
    }

    public List<CustomRecipe> getAllRecipes() {
        return Collections.unmodifiableList(customRecipes);
    }
}