package me.whereareiam.identica;

import com.google.inject.Injector;
import com.google.inject.Key;
import lombok.Getter;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.database.DatabaseService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.lifecycle.IdenticaReadyEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.identity.account.RegistrationAccountService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.service.MigrationService;
import org.jetbrains.annotations.NotNull;

/**
 * Main API access point for the Identica authentication plugin.
 *
 * <p>External plugins should use this class to access Identica services.
 * All services become available after the {@link IdenticaReadyEvent}
 * is fired.</p>
 *
 * <p><b>Example usage:</b></p>
 * <pre>{@code
 * // Check if API is ready
 * if (!IdenticaAPI.isInitialized()) {
 *     getLogger().warning("Identica not ready yet!");
 *     return;
 * }
 *
 * // Get the connection coordinator
 * ConnectionCoordinator connectionCoordinator = IdenticaAPI.getConnectionCoordinator();
 *
 * // Or get any service by class
 * ProviderManager providerManager = IdenticaAPI.getService(ProviderManager.class);
 * }</pre>
 *
 * <p><b>Important:</b> Always check {@link #isInitialized()} before accessing services,
 * or wait for {@link IdenticaReadyEvent}.</p>
 */
@SuppressWarnings("unused")
public final class IdenticaAPI {
	private static volatile Injector injector;
	@Getter
	private static volatile boolean initialized = false;

	private IdenticaAPI() {
		throw new UnsupportedOperationException("This class cannot be instantiated");
	}

	/**
	 * Initializes the API with the Guice injector.
	 * <p>This method is called internally by Identica during startup.
	 * External plugins should never call this method.</p>
	 *
	 * @param injector the Guice injector
	 * @throws IllegalStateException if already initialized
	 */
	public static void initialize(@NotNull Injector injector) {
		if (IdenticaAPI.injector != null) {
			throw new IllegalStateException("IdenticaAPI is already initialized");
		}
		IdenticaAPI.injector = injector;
		IdenticaAPI.initialized = true;
	}

	/**
	 * Shuts down the API and clears the injector reference.
	 * <p>This method is called internally by Identica during shutdown.
	 * External plugins should never call this method.</p>
	 */
	public static void shutdown() {
		IdenticaAPI.injector = null;
		IdenticaAPI.initialized = false;
	}

	/**
	 * Gets a service instance from the Identica API.
	 *
	 * @param serviceClass the service class to retrieve
	 * @param <T>          the service type
	 * @return the service instance
	 * @throws IllegalStateException if the API is not initialized
	 */
	@NotNull
	public static <T> T getService(@NotNull Class<T> serviceClass) {
		return getService(Key.get(serviceClass));
	}

	@NotNull
	public static <T> T getService(@NotNull Key<T> key) {
		Injector currentInjector = injector;
		if (currentInjector == null) {
			throw new IllegalStateException(
					"IdenticaAPI is not initialized. Make sure Identica is loaded and wait for IdenticaReadyEvent."
			);
		}
		return currentInjector.getInstance(key);
	}

	// ===== Convenience Methods for Common Services =====

	/**
	 * Gets the EventManager for registering listeners and calling events.
	 *
	 * @return the EventManager instance
	 * @throws IllegalStateException if the API is not initialized
	 */
	@NotNull
	public static EventManager getEventManager() {
		return getService(EventManager.class);
	}

	/**
	 * Gets the ConnectionCoordinator for processing connection lifecycle operations.
	 *
	 * @return the ConnectionCoordinator instance
	 * @throws IllegalStateException if the API is not initialized
	 */
	@NotNull
	public static ConnectionCoordinator getConnectionCoordinator() {
		return getService(ConnectionCoordinator.class);
	}

	/**
	 * Gets the ProviderManager for managing authentication providers.
	 *
	 * @return the ProviderManager instance
	 * @throws IllegalStateException if the API is not initialized
	 */
	@NotNull
	public static ProviderManager getProviderManager() {
		return getService(ProviderManager.class);
	}

	/**
	 * Gets runtime provider operations (eligibility, resolver resolution).
	 *
	 * @return the ProviderOperations instance
	 * @throws IllegalStateException if the API is not initialized
	 */
	@NotNull
	public static ProviderOperations getProviderOperations() {
		return getService(ProviderOperations.class);
	}

	/**
	 * Gets the CommandService for command operations.
	 *
	 * @return the CommandService instance
	 * @throws IllegalStateException if the API is not initialized
	 */
	@NotNull
	public static CommandService getCommandService() {
		return getService(CommandService.class);
	}

	/**
	 * Gets the DatabaseService for persistence operations.
	 *
	 * @return the DatabaseService instance
	 * @throws IllegalStateException if the API is not initialized
	 */
	@NotNull
	public static DatabaseService getDatabaseService() {
		return getService(DatabaseService.class);
	}


	/**
	 * Gets the RegistrationAccountService for account reservation operations.
	 *
	 * @return the RegistrationAccountService instance
	 * @throws IllegalStateException if the API is not initialized
	 */
	@NotNull
	public static RegistrationAccountService getRegistrationAccountService() {
		return getService(RegistrationAccountService.class);
	}

	/**
	 * Gets the AccountService for account lookup and lifecycle operations.
	 *
	 * @return the AccountService instance
	 * @throws IllegalStateException if the API is not initialized
	 */
	@NotNull
	public static AccountService getAccountService() {
		return getService(AccountService.class);
	}

	/**
	 * Gets the IdentityService for online identity tracking.
	 *
	 * @return the IdentityService instance
	 * @throws IllegalStateException if the API is not initialized
	 */
	@NotNull
	public static IdentityService getPresenceService() {
		return getService(IdentityService.class);
	}

	/**
	 * Gets the SessionService for session lifecycle operations.
	 *
	 * @return the SessionService instance
	 * @throws IllegalStateException if the API is not initialized
	 */
	@NotNull
	public static SessionService getSessionService() {
		return getService(SessionService.class);
	}

	/**
	 * Gets the MigrationService for provider migration operations.
	 *
	 * @return the MigrationService instance
	 * @throws IllegalStateException if the API is not initialized
	 */
	@NotNull
	public static MigrationService getMigrationService() {
		return getService(MigrationService.class);
	}

	/**
	 * Gets the ReplicationSystem for replicated caches and channels.
	 *
	 * @return the ReplicationSystem instance
	 * @throws IllegalStateException if the API is not initialized
	 */
	@NotNull
	public static ReplicationSystem getReplicationSystem() {
		return getService(ReplicationSystem.class);
	}

	/**
	 * Gets the PipelineExtensionRegistry for registering pipeline extensions.
	 *
	 * @return the PipelineExtensionRegistry instance
	 * @throws IllegalStateException if the API is not initialized
	 */
	@NotNull
	public static PipelineExtensionRegistry getPipelineExtensionRegistry() {
		return getService(PipelineExtensionRegistry.class);
	}

	/**
	 * Gets the configured base Configura instance used by Identica.
	 *
	 * <p>External addons can derive their own configuration copies from this instance
	 * with default providers or document features
	 * while keeping Identica's configured format and modules.</p>
	 *
	 * @return the configured base Configura instance
	 * @throws IllegalStateException if the API is not initialized
	 */
	@NotNull
	public static Configura getConfigura() {
		return getService(Configura.class);
	}

}
