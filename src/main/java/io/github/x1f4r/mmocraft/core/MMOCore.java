package io.github.x1f4r.mmocraft.core;

import io.github.x1f4r.mmocraft.MMOCraft;
// Import specific services as they are created and needed for registration order
import io.github.x1f4r.mmocraft.services.ConfigService;
import io.github.x1f4r.mmocraft.services.LoggingService;
import io.github.x1f4r.mmocraft.services.NBTService;
import io.github.x1f4r.mmocraft.services.ItemService;
import io.github.x1f4r.mmocraft.services.PersistenceService;
import io.github.x1f4r.mmocraft.services.PlayerDataService;
import io.github.x1f4r.mmocraft.services.PlayerStatsService;
import io.github.x1f4r.mmocraft.services.PlayerResourceService;
import io.github.x1f4r.mmocraft.services.PlayerInterfaceService;
import io.github.x1f4r.mmocraft.services.CustomMobService;
import io.github.x1f4r.mmocraft.services.MobDropService;
import io.github.x1f4r.mmocraft.services.AbilityService;
import io.github.x1f4r.mmocraft.services.RecipeService;
import io.github.x1f4r.mmocraft.services.CraftingGUIService;
import io.github.x1f4r.mmocraft.services.EntityStatsService;
import io.github.x1f4r.mmocraft.services.CombatService;
import io.github.x1f4r.mmocraft.services.VisualFeedbackService;
import io.github.x1f4r.mmocraft.services.ToolProficiencyService;
import io.github.x1f4r.mmocraft.services.CompactorService;
import io.github.x1f4r.mmocraft.services.CommandService;


import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.LinkedHashMap; // Preserves insertion order for ordered init/shutdown
import java.util.List;
import java.util.Map;\nimport java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MMOCore {
    private final MMOCraft plugin;
    // Use ConcurrentHashMap for thread safety. Order maintained by explicit service list.
    private final Map<Class<? extends Service>, Service> services = new ConcurrentHashMap<>();
    // Track registration order for proper initialization/shutdown sequence
    private final List<Class<? extends Service>> serviceOrder = new ArrayList<>();
    private LoggingService internalLogger; // Dedicated logger for MMOCore, initialized first

    public MMOCore(MMOCraft plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes and enables all registered services for the plugin.
     * This method is called from {@link MMOCraft#onEnable()}.
     * @throws Exception if any critical service fails to initialize.
     */
    public void onEnable() throws Exception {
        // Initialize LoggingService first as it's used by MMOCore itself and other services during their init.
        this.internalLogger = new LoggingService(this.plugin); // Pass plugin instance for its logger
        try {
            // Manually initialize internalLogger before it's formally "registered" as a service
            // to allow MMOCore to use it for logging the registration of other services.
            this.internalLogger.initialize(this); // Pass 'this' MMOCore instance
        } catch (Exception e) {
            // If internal logger fails, use the raw plugin logger.
            MMOCraft.getPluginLogger().log(Level.SEVERE, "Failed to initialize MMOCore's internal LoggingService!", e);
            throw new IllegalStateException("MMOCore internal logger could not be initialized.", e);
        }

        internalLogger.info("MMOCore enabling sequence initiated...");

        // --- Register services in their intended initialization order ---
        // Foundational Services
        registerService(this.internalLogger, LoggingService.class); // Now formally register it
        registerService(new NBTService(this.plugin), NBTService.class);
        registerService(new ConfigService(this.plugin), ConfigService.class);
        registerService(new CustomMobService(this), CustomMobService.class);
        // EntityStatsService might be needed by CustomMobService later, so register it reasonably early.
        registerService(new EntityStatsService(this), EntityStatsService.class);
        // VisualFeedbackService depends on EntityStatsService for initial health bar data.
        registerService(new VisualFeedbackService(this), VisualFeedbackService.class);
        // CombatService depends on PlayerStats, EntityStats, and VisualFeedback.
        registerService(new CombatService(this), CombatService.class);
        // Register services only once, in dependency order
        // Core services first
        registerService(new AbilityService(this), AbilityService.class);
        registerService(new ItemService(this), ItemService.class);
        registerService(new RecipeService(this), RecipeService.class);
        registerService(new CraftingGUIService(this), CraftingGUIService.class);
        registerService(new ToolProficiencyService(this), ToolProficiencyService.class);
        
        // PersistenceService depends on LoggingService
        registerService(new PersistenceService(getService(LoggingService.class)), PersistenceService.class);
        registerService(new CompactorService(this), CompactorService.class);
        registerService(new CommandService(this), CommandService.class);

        // Player-centric Services
        registerService(new PlayerDataService(this), PlayerDataService.class);
        registerService(new PlayerStatsService(this), PlayerStatsService.class);
        registerService(new PlayerResourceService(this), PlayerResourceService.class);
        registerService(new PlayerInterfaceService(this), PlayerInterfaceService.class);

        // Part 3 Services
        registerService(new EntityStatsService(this), EntityStatsService.class);
        registerService(new VisualFeedbackService(this), VisualFeedbackService.class);
        registerService(new CombatService(this), CombatService.class);

        // Part 4 Services
        registerService(new CustomMobService(this), CustomMobService.class);
        registerService(new MobDropService(this), MobDropService.class);
        registerService(new NBTService(this), NBTService.class);

        // --- Initialize all registered services in the order they were registered ---
        for (Class<? extends Service> serviceClass : serviceOrder) {
            Service service = services.get(serviceClass);
            // Skip re-initializing internalLogger if it's the one we set up first for MMOCore logging
            if (service == this.internalLogger && serviceClass.equals(LoggingService.class)) {
                // internalLogger was already initialized above, so skip its formal init call here.
                continue;
            }
            try {
                internalLogger.info("Initializing service: " + service.getServiceName() + " (" + service.getClass().getName() + ")");
                service.initialize(this);
            } catch (Exception e) {
                internalLogger.severe("Critical failure initializing service: " + service.getServiceName(), e);
                throw e; // Propagate to stop plugin enable if a service fails critically
            }
        }
        internalLogger.info("All services initialized successfully.");
    }

    /**
     * Shuts down all registered services.
     * This method is called from {@link MMOCraft#onDisable()}.
     */
    public void onDisable() {
        if (internalLogger == null) {
            MMOCraft.getPluginLogger().warning("MMOCore internal logger is null during onDisable. Service shutdown might be incomplete.");
            // Attempt to proceed if possible, but this indicates an issue during onEnable.
        } else {
            internalLogger.info("MMOCore disabling sequence initiated...");
        }

        // Shutdown in reverse order of registration
        List<Class<? extends Service>> reversedOrder = new ArrayList<>(serviceOrder);
        java.util.Collections.reverse(reversedOrder);

        for (Class<? extends Service> serviceClass : reversedOrder) {
            Service service = services.get(serviceClass);
            try {
                // If internalLogger is the one being shut down, or if it's null, use plugin's raw logger
                if (service == this.internalLogger || internalLogger == null) {
                    MMOCraft.getPluginLogger().info("Shutting down service: " + service.getServiceName());
                } else {
                    this.internalLogger.info("Shutting down service: " + service.getServiceName());
                }
                service.shutdown();
            } catch (Exception e) {
                if (service == this.internalLogger || internalLogger == null) {
                    MMOCraft.getPluginLogger().log(Level.SEVERE, "Error shutting down service: " + service.getServiceName(), e);
                } else {
                    this.internalLogger.severe("Error shutting down service: " + service.getServiceName(), e);
                }
            }
        }
        services.clear();
        if (internalLogger != null) {
            internalLogger.info("All services shut down.");
        } else {
            MMOCraft.getPluginLogger().info("MMOCore service shutdown process complete (internal logger was unavailable).");
        }
    }

    /**
     * Registers a service instance with the MMOCore.
     * Services are initialized in the order they are registered.
     * @param service The service instance to register.
     * @param registrationClass The class (usually an interface or the service's own class) to use as the key for retrieving this service.
     */
    public void registerService(Service service, Class<? extends Service> registrationClass) {
        if (services.containsKey(registrationClass)) {
            String warningMsg = "Service already registered for key: " + registrationClass.getName() +
                    ". Overwriting. Ensure this is intended. Old: " + services.get(registrationClass).getClass().getName() +
                    ", New: " + service.getClass().getName();
            if (this.internalLogger != null) {
                this.internalLogger.warn(warningMsg);
            } else {
                MMOCraft.getPluginLogger().warning(warningMsg);
            }
        }
        services.put(registrationClass, service);\n        serviceOrder.add(registrationClass); // Track registration order
        String infoMsg = "Registered service: " + service.getServiceName() + " under key " + registrationClass.getSimpleName();
        if (this.internalLogger != null) {
            this.internalLogger.info(infoMsg);
        } else {
            MMOCraft.getPluginLogger().info(infoMsg);
        }
    }

    /**
     * Registers a service instance using its own class as the registration key.
     * @param service The service instance to register.
     */
    public void registerService(Service service) {
        registerService(service, service.getClass());
    }

    /**
     * Retrieves a registered service instance.
     * @param serviceClass The class key used to register the service (usually an interface or the service's class).
     * @param <T> The type of the service.
     * @return The registered service instance.
     * @throws IllegalStateException if the service is not found.
     */
    @SuppressWarnings("unchecked")
    public <T extends Service> T getService(Class<T> serviceClass) {
        Service service = services.get(serviceClass);
        if (service == null) {
            String message = "Service not found: " + serviceClass.getName() +
                    ". Ensure it is registered and initialized before access, and check registration order.";
            if (this.internalLogger != null) {
                this.internalLogger.severe(message);
            } else {
                MMOCraft.getPluginLogger().severe(message);
            }
            throw new IllegalStateException(message);
        }
        try {
            return (T) service;
        } catch (ClassCastException e) {
            String message = "Service type mismatch for: " + serviceClass.getName() +
                    ". Registered as " + service.getClass().getName() + ".";
            if (this.internalLogger != null) {
                this.internalLogger.severe(message);
            } else {
                MMOCraft.getPluginLogger().severe(message);
            }
            throw new IllegalStateException(message, e);
        }
    }

    public MMOCraft getPlugin() {
        return plugin;
    }

    // --- Convenience methods for services to register listeners/commands via MMOCore ---
    public void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        String listenerName = listener.getClass().getSimpleName();
        if (this.internalLogger != null) {
            this.internalLogger.debug("Registered listener: " + listenerName); // Using debug as per instructions
        } else {
            MMOCraft.getPluginLogger().fine("Registered listener: " + listenerName);
        }
    }

    public void registerCommand(String commandNameInPluginYml, CommandExecutor executor, @javax.annotation.Nullable TabCompleter tabCompleter) {
        PluginCommand command = plugin.getCommand(commandNameInPluginYml);
        if (command != null) {
            command.setExecutor(executor);
            if (tabCompleter != null) {
                command.setTabCompleter(tabCompleter);
            }
            String infoMsg = "Registered command handler for /" + commandNameInPluginYml;
            if (this.internalLogger != null) {
                this.internalLogger.info(infoMsg);
            } else {
                MMOCraft.getPluginLogger().info(infoMsg);
            }
        } else {
            String warningMsg = "Command '/" + commandNameInPluginYml + "' not found in plugin.yml! Cannot register handler.";
            if (this.internalLogger != null) {
                this.internalLogger.warn(warningMsg);
            } else {
                MMOCraft.getPluginLogger().warning(warningMsg);
            }
        }
    }
}