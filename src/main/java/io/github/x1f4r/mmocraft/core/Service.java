package io.github.x1f4r.mmocraft.core;

/**
 * Defines the contract for all manageable services within MMOCore.
 * Services are modular components of the plugin that handle specific functionalities.
 */
public interface Service {
    /**
     * Called when the service should initialize its components,
     * register listeners, load configurations, and perform any setup.
     * This method is called by {@link MMOCore} during the plugin's enabling sequence.
     *
     * @param core The {@link MMOCore} instance, providing access to other services,
     *             the plugin instance, and utility methods like listener/command registration.
     * @throws Exception if a critical error occurs during initialization that should
     *                   prevent the plugin or this service from enabling.
     */
    void initialize(MMOCore core) throws Exception;

    /**
     * Called when the service should shut down.
     * This includes unregistering listeners, saving any pending data,
     * cancelling scheduled tasks, and releasing resources.
     * This method is called by {@link MMOCore} during the plugin's disabling sequence.
     */
    void shutdown();

    /**
     * Provides a user-friendly name for the service, primarily used for logging purposes.
     * The default implementation returns the simple class name of the service.
     *
     * @return The name of the service.
     */
    default String getServiceName() {
        return this.getClass().getSimpleName();
    }
}