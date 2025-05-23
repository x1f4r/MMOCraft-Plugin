package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.MMOCraft;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import io.github.x1f4r.mmocraft.items.RequirementType;
import io.github.x1f4r.mmocraft.items.RequiredIngredient;
import io.github.x1f4r.mmocraft.recipes.CustomRecipe;
import io.github.x1f4r.mmocraft.recipes.RecipeType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey; // For Bukkit Tags
import org.bukkit.Tag;
import org.bukkit.Registry; // For iterating Bukkit tags
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RecipeService implements Service {
    private MMOCore core;
    private LoggingService logging;
    private ConfigService configService;
    private ItemService itemService; // Needed to validate result_item_id and for matching custom item ingredients
    // NBTService is accessed via static NBTService.ITEM_ID_KEY in RequiredIngredient

    private final List<CustomRecipe> customRecipeRegistry = Collections.synchronizedList(new ArrayList<>());
    // For resolving string tag names like "LOGS" to actual Bukkit Tag<Material> objects
    private final Map<String, Tag<Material>> knownMaterialTags = new ConcurrentHashMap<>();

    public RecipeService(MMOCore core) {
        this.core = core;
    }

    @Override
    public void initialize(MMOCore core) {
        this.logging = core.getService(LoggingService.class);
        this.configService = core.getService(ConfigService.class);
        this.itemService = core.getService(ItemService.class); // ItemService should be ready

        populateKnownMaterialTags(); // Populate Bukkit tags

        FileConfiguration recipesConfig = configService.getConfig(ConfigService.RECIPES_CONFIG_FILENAME);
        if (recipesConfig.getKeys(false).isEmpty() && recipesConfig.getConfigurationSection("recipes") == null) {
            logging.warn("'" + ConfigService.RECIPES_CONFIG_FILENAME + "' appears to be empty or missing 'recipes' section. No custom recipes will be loaded.");
        } else {
            loadRecipesFromConfig(recipesConfig);
        }

        configService.subscribeToReload(ConfigService.RECIPES_CONFIG_FILENAME, this::loadRecipesFromConfig);
        logging.info(getServiceName() + " initialized. Loaded " + customRecipeRegistry.size() + " custom recipes.");
    }

    @Override
    public void shutdown() {
        customRecipeRegistry.clear();
        knownMaterialTags.clear();
        logging.info(getServiceName() + " shutdown. Custom recipes cleared.");
    }

    private void populateKnownMaterialTags() {
        knownMaterialTags.clear();
        // Iterate over Bukkit's Registry of Material Tags
        // This is a robust way to get all standard tags.
        Registry.MATERIAL.getTags().forEach(tag -> {
            // Store by both simple key (e.g., "logs") and full namespaced key (e.g., "minecraft:logs")
            // Bukkit's Tag.getKey().getKey() gives the "logs" part.
            String simpleKey = tag.getKey().getKey().toUpperCase(); // LOGS
            knownMaterialTags.put(simpleKey, tag);
            // For custom tags defined elsewhere (not standard Bukkit), they'd be added here too.
        });
        logging.debug("Populated " + knownMaterialTags.size() + " known Material Tags from Bukkit Registry.");
    }

    /**
     * Resolves a string tag identifier (e.g., "LOGS", "PLANKS", "minecraft:logs")
     * to a Bukkit {@link Tag<Material>}.
     * @param tagValue The string identifier of the tag. Case-insensitive for simple keys.
     * @return The resolved {@link Tag<Material>}, or null if not found.
     */
    @org.jetbrains.annotations.Nullable
    public Tag<Material> resolveMaterialTag(@org.jetbrains.annotations.NotNull String tagValue) {
        Objects.requireNonNull(tagValue, "Tag value cannot be null for resolution.");
        // Try direct lookup (case-sensitive for namespaced, potentially case-insensitive for simple)
        Tag<Material> foundTag = knownMaterialTags.get(tagValue.toUpperCase()); // Try uppercase simple key
        if (foundTag != null) return foundTag;

        // If it contains ':', assume it's a full NamespacedKey string
        if (tagValue.contains(":")) {
            try {
                NamespacedKey key = NamespacedKey.fromString(tagValue.toLowerCase());
                return Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class); // Or Tag.REGISTRY_ITEMS
                // Using Registry.MATERIAL.getTag(key) is better if available
                // return Registry.MATERIAL.getTag(key); // Purpur/Paper often has improved Registry access
            } catch (IllegalArgumentException | NullPointerException e) {
                // Invalid NamespacedKey format or tag not found
                return null;
            }
        }
        // If custom tag system is implemented with a prefix like "#":
        // if (tagValue.startsWith("#")) { /* ... lookup custom tag ... */ }
        return null;
    }


    private void loadRecipesFromConfig(FileConfiguration config) {
        customRecipeRegistry.clear();
        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");
        if (recipesSection == null) {
            logging.info("No 'recipes' section found in " + ConfigService.RECIPES_CONFIG_FILENAME + ". No custom recipes loaded.");
            return;
        }

        for (String recipeId : recipesSection.getKeys(false)) {
            ConfigurationSection recipeConfig = recipesSection.getConfigurationSection(recipeId);
            if (recipeConfig == null) continue;

            try {
                RecipeType type = RecipeType.valueOf(recipeConfig.getString("type", "SHAPED").toUpperCase());
                ConfigurationSection resultConfig = recipeConfig.getConfigurationSection("result");
                if (resultConfig == null || !resultConfig.contains("item_id")) {
                    logging.warn("Recipe '" + recipeId + "' is missing 'result.item_id'. Skipping.");
                    continue;
                }
                String resultItemId = resultConfig.getString("item_id");
                int resultAmount = resultConfig.getInt("amount", 1);

                // Validate result item exists (either as custom or vanilla)
                if (!resultItemId.toUpperCase().startsWith("VANILLA:") && itemService.getCustomItemTemplate(resultItemId) == null) {
                    if (Material.matchMaterial(resultItemId.toUpperCase()) == null) { // Check if it's a valid vanilla material if not prefixed
                        logging.warn("Result item_id '" + resultItemId + "' for recipe '" + recipeId +
                                "' is not a known CustomItem ID nor a valid VANILLA material. Skipping.");
                        continue;
                    }
                    // If it's a valid vanilla material without prefix, assume VANILLA:
                    // This could be an option, or require explicit VANILLA: prefix.
                    // For now, let's assume custom items are primary, vanilla needs VANILLA:
                }


                List<String> shape = null;
                Map<Character, RequiredIngredient> shapedIngredients = null;
                List<RequiredIngredient> shapelessIngredients = null;
                boolean strictShapeless = recipeConfig.getBoolean("strict_shapeless_ingredient_count", false);

                if (type == RecipeType.SHAPED) {
                    shape = recipeConfig.getStringList("shape");
                    if (shape.isEmpty() || shape.size() > 3 || shape.stream().anyMatch(row -> row.length() > 3 || row.isEmpty())) {
                        logging.warn("Invalid shape for SHAPED recipe '" + recipeId + "'. Shape must be 1-3 rows, 1-3 columns. Skipping.");
                        continue;
                    }
                    // Normalize shape: ensure all rows are same length as the longest row (max 3)
                    int maxWidth = shape.stream().mapToInt(String::length).max().orElse(0);
                    final int finalMaxWidth = Math.min(3, maxWidth); // Cap at 3
                    shape = shape.stream()
                            .map(s -> String.format("%-" + finalMaxWidth + "s", s).substring(0,finalMaxWidth))
                            .collect(Collectors.toList());


                    shapedIngredients = new HashMap<>();
                    ConfigurationSection ingredientsConf = recipeConfig.getConfigurationSection("ingredients");
                    if (ingredientsConf == null) {
                        logging.warn("SHAPED recipe '" + recipeId + "' missing 'ingredients' key mapping section. Skipping.");
                        continue;
                    }
                    for (String keyCharStr : ingredientsConf.getKeys(false)) {
                        if (keyCharStr.length() != 1) {
                            logging.warn("Invalid ingredient key char '" + keyCharStr + "' in SHAPED recipe '" + recipeId + "'. Skipping ingredient key.");
                            continue;
                        }
                        RequiredIngredient req = parseRequiredIngredient(ingredientsConf.getConfigurationSection(keyCharStr), recipeId, "ingredients." + keyCharStr);
                        if (req != null) shapedIngredients.put(keyCharStr.charAt(0), req);
                    }
                    if (shapedIngredients.isEmpty() && shape.stream().anyMatch(s -> !s.isBlank())) {
                        logging.warn("SHAPED recipe '" + recipeId + "' has a non-empty shape but no valid ingredients defined. Skipping.");
                        continue;
                    }

                } else { // SHAPELESS
                    List<?> rawIngredientsList = recipeConfig.getList("ingredients");
                    if (rawIngredientsList == null || rawIngredientsList.isEmpty()) {
                        logging.warn("SHAPELESS recipe '" + recipeId + "' missing 'ingredients' list or list is empty. Skipping.");
                        continue;
                    }
                    shapelessIngredients = new ArrayList<>();
                    for (int i = 0; i < rawIngredientsList.size(); i++) {
                        Object ingredientObj = rawIngredientsList.get(i);
                        if (ingredientObj instanceof Map) {
                            // Convert Map to ConfigurationSection for parsing helper
                            MemoryConfiguration tempSection = new MemoryConfiguration();
                            ((Map<?, ?>) ingredientObj).forEach((k, v) -> tempSection.set(String.valueOf(k), v));
                            RequiredIngredient req = parseRequiredIngredient(tempSection, recipeId, "ingredients["+i+"]");
                            if (req != null) shapelessIngredients.add(req);
                        } else {
                            logging.warn("Invalid ingredient format in SHAPELESS recipe '" + recipeId + "' at index " + i + ". Expected a map. Skipping ingredient.");
                        }
                    }
                    if (shapelessIngredients.isEmpty()) {
                        logging.warn("SHAPELESS recipe '" + recipeId + "' has no valid ingredients after parsing. Skipping.");
                        continue;
                    }
                }

                CustomRecipe customRecipe = new CustomRecipe(recipeId.toLowerCase(), type, resultItemId, resultAmount,
                        shape, shapedIngredients, shapelessIngredients, strictShapeless);
                customRecipeRegistry.add(customRecipe);

            } catch (IllegalArgumentException e) {
                logging.warn("Invalid configuration for recipe '" + recipeId + "'. Error: " + e.getMessage() + ". Skipping.");
            } catch (Exception e) {
                logging.severe("Unexpected error parsing custom recipe: '" + recipeId + "'", e);
            }
        }
        // Sort recipes? For now, order of definition is used.
        // Potentially sort by complexity or specificity if matching becomes ambiguous.
        logging.info("Reloaded " + customRecipeRegistry.size() + " custom recipes.");
    }

    private RequiredIngredient parseRequiredIngredient(ConfigurationSection conf, String recipeIdContext, String ingredientPath) {
        if (conf == null) {
            logging.warn("Ingredient configuration section is null for recipe '" + recipeIdContext + "' at path '" + ingredientPath + "'.");
            return null;
        }
        try {
            RequirementType reqType = RequirementType.valueOf(conf.getString("type", "MATERIAL").toUpperCase());
            String value = conf.getString("value");
            int amount = conf.getInt("amount", 1);

            if (value == null || value.isBlank()) {
                logging.warn("Missing or blank 'value' for ingredient in recipe '" + recipeIdContext + "' at path '" + ingredientPath + "'. Skipping ingredient.");
                return null;
            }
            if (amount < 1) amount = 1;

            Tag<Material> resolvedBukkitTag = null;
            if (reqType == RequirementType.TAG) {
                resolvedBukkitTag = resolveMaterialTag(value); // This uses our knownMaterialTags map
                if (resolvedBukkitTag == null) {
                    logging.warn("Unknown or unresolvable material tag '" + value + "' for ingredient in recipe '" + recipeIdContext + "' at path '" + ingredientPath + "'. Crafting with this tag may fail.");
                    // Still create the ingredient; matching logic will handle unresolved tags.
                }
            } else if (reqType == RequirementType.MATERIAL) {
                if (Material.matchMaterial(value.toUpperCase()) == null) {
                    logging.warn("Invalid material name '" + value + "' for ingredient in recipe '" + recipeIdContext + "' at path '" + ingredientPath + "'. Skipping ingredient.");
                    return null;
                }
            } else if (reqType == RequirementType.ITEM) {
                if (itemService.getCustomItemTemplate(value) == null) {
                    logging.warn("Unknown custom item ID '" + value + "' as ingredient in recipe '" + recipeIdContext + "' at path '" + ingredientPath + "'. Skipping ingredient.");
                    return null;
                }
            }
            return new RequiredIngredient(reqType, value, amount, resolvedBukkitTag);
        } catch (IllegalArgumentException e) {
            logging.warn("Invalid RequirementType for ingredient in recipe '" + recipeIdContext + "' at path '" + ingredientPath + "'. Error: " + e.getMessage() + ". Skipping ingredient.");
            return null;
        }
    }

    /**
     * Finds the first custom recipe that matches the given crafting matrix.
     * @param matrix A 9-slot ItemStack array representing the 3x3 crafting grid.
     * @return The matched CustomRecipe, or null if no custom recipe matches.
     */
    @org.jetbrains.annotations.Nullable
    public CustomRecipe findMatchingRecipe(@NotNull ItemStack[] matrix) {
        Objects.requireNonNull(matrix, "Crafting matrix cannot be null.");
        // Iterate in reverse of addition? Or sort by specificity? For now, simple iteration.
        for (CustomRecipe recipe : new ArrayList<>(customRecipeRegistry)) { // Iterate copy for thread safety during reloads
            if (recipe.matches(matrix, itemService, this)) { // Pass self for tag resolution
                return recipe;
            }
        }
        return null;
    }

    /**
     * Finds a compacting recipe for a given input material.
     * A compacting recipe is typically shapeless, takes a large quantity of one item,
     * and produces a different, more compressed item.
     * @param inputMaterial The material to find a compacting recipe for.
     * @return The CustomRecipe if found, otherwise null.
     */
    @org.jetbrains.annotations.Nullable
    public CustomRecipe findCompactingRecipeForItem(@NotNull Material inputMaterial) {
        Objects.requireNonNull(inputMaterial, "Input material for compacting recipe cannot be null.");
        logging.debug("[RecipeService] Attempting to find compacting recipe for input: " + inputMaterial.name());

        for (CustomRecipe recipe : new ArrayList<>(customRecipeRegistry)) { // Iterate copy
            if (recipe.type() == RecipeType.SHAPELESS &&
                    recipe.shapelessIngredients() != null &&
                    recipe.shapelessIngredients().size() == 1) { // Compacting recipes typically have one ingredient type

                RequiredIngredient required = recipe.shapelessIngredients().get(0);
                if (required.type() == RequirementType.MATERIAL) {
                    Material recipeMaterial = Material.matchMaterial(required.value().toUpperCase());
                    if (inputMaterial == recipeMaterial) {
                        // Check for common compacting amounts or if result is different
                        if (required.amount() == 160 || required.amount() == 9 || required.amount() == 4) {
                            ItemStack resultStack = recipe.getResult(itemService);
                            // Ensure result is different from input to avoid infinite loops with single item "compaction"
                            if (resultStack != null && !resultStack.getType().isAir() && resultStack.getType() != inputMaterial) {
                                logging.debug("[RecipeService] Found potential compacting recipe '" + recipe.id() + "' for " + inputMaterial.name() + " -> " + resultStack.getType().name());
                                return recipe;
                            }
                        }
                    }
                }
            }
        }
        logging.debug("[RecipeService] No suitable compacting recipe found for " + inputMaterial.name());
        return null;
    }
}