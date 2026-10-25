package me.whereareiam.identica;

import me.whereareiam.configura.Configura;
import me.whereareiam.identica.checkpoint.Checkpoint;
import me.whereareiam.identica.checkpoint.Checkpoints;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.config.ConfigCatalog;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.identity.account.RegistrationAccountService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.pipeline.PendingScenarioService;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.service.MigrationService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Identica's API: the services the running Identica instance offers to other plugins.
 *
 * <p>Obtain it through {@link #get()} or {@link #whenReady(Consumer)} and pass it to the classes of your
 * addon, which then depend on this interface instead of static calls and can be given a mock in tests:</p>
 *
 * <pre>{@code
 * IdenticaAPI.whenReady(identica -> {
 *     identica.getEventManager().register(new MyListener(identica));
 * });
 * }</pre>
 *
 * <p>Identica registers its API during startup, before it loads providers, and removes it when it shuts down.
 * {@link #whenReady(Consumer)} waits until the providers are loaded, so their commands and documents are there.</p>
 */
public interface IdenticaAPI {
	/**
	 * Returns the API of the running Identica instance. Use it when your addon is loaded after Identica.
	 *
	 * @return API of the running instance
	 * @throws IllegalStateException if Identica is not running
	 */
	static @NotNull IdenticaAPI get() {
		return RunningInstance.get();
	}

	/**
	 * Returns the API of the running Identica instance, or empty when Identica is not running.
	 *
	 * @return API of the running instance, if any
	 */
	static @NotNull Optional<IdenticaAPI> find() {
		return RunningInstance.find();
	}

	/**
	 * Runs the action with the API once Identica is ready: started, with its providers loaded and their commands
	 * and documents in place. When it already is, the action runs immediately. A waiting action runs once, on the
	 * thread that starts Identica; one that fails is logged and does not keep Identica or the other actions from
	 * starting.
	 *
	 * @param action action to run with the API
	 */
	static void whenReady(@NotNull Consumer<IdenticaAPI> action) {
		RunningInstance.whenReady(action);
	}

	/**
	 * Registers the API of the running instance, which {@link #get()} returns from then on. Called by Identica
	 * during startup, before it loads providers; addons never call it.
	 *
	 * @param identica API of the running instance
	 * @throws IllegalStateException if an instance is already registered
	 */
	@ApiStatus.Internal
	static void register(@NotNull IdenticaAPI identica) {
		RunningInstance.register(identica);
	}

	/**
	 * Marks Identica as ready, once its providers are loaded, and runs the actions waiting in
	 * {@link #whenReady(Consumer)}. Called by Identica during startup; addons never call it.
	 *
	 * @throws IllegalStateException if no instance is registered
	 */
	@ApiStatus.Internal
	static void markReady() {
		RunningInstance.markReady();
	}

	/**
	 * Removes the API of the running instance. Called by Identica during shutdown; addons never call it.
	 */
	@ApiStatus.Internal
	static void unregister() {
		RunningInstance.unregister();
	}

	/**
	 * Returns the event manager for registering listeners and calling events.
	 *
	 * @return event manager
	 */
	@NotNull EventManager getEventManager();

	/**
	 * Returns the coordinator of incoming connections.
	 *
	 * @return connection coordinator
	 */
	@NotNull ConnectionCoordinator getConnectionCoordinator();

	/**
	 * Returns the manager of loaded providers.
	 *
	 * @return provider manager
	 */
	@NotNull ProviderManager getProviderManager();

	/**
	 * Returns provider eligibility and runtime provider operations.
	 *
	 * @return provider operations
	 */
	@NotNull ProviderOperations getProviderOperations();

	/**
	 * Returns the service that registers and lists commands.
	 *
	 * @return command service
	 */
	@NotNull CommandService getCommandService();

	/**
	 * Returns the service that creates accounts during registration.
	 *
	 * @return registration account service
	 */
	@NotNull RegistrationAccountService getRegistrationAccountService();

	/**
	 * Returns the service for account lookup and lifecycle operations.
	 *
	 * @return account service
	 */
	@NotNull AccountService getAccountService();

	/**
	 * Returns the service for the identities of the connections on this proxy.
	 *
	 * @return identity service
	 */
	@NotNull IdentityService getIdentityService();

	/**
	 * Returns the service for finding, opening and closing sessions.
	 *
	 * @return session service
	 */
	@NotNull SessionService getSessionService();

	/**
	 * Returns the service for account migrations between providers.
	 *
	 * @return migration service
	 */
	@NotNull MigrationService getMigrationService();

	/**
	 * Returns the replication system shared by the proxies of a network.
	 *
	 * @return replication system
	 */
	@NotNull ReplicationSystem getReplicationSystem();

	/**
	 * Returns the registry of pipeline extensions.
	 *
	 * @return pipeline extension registry
	 */
	@NotNull PipelineExtensionRegistry getPipelineExtensionRegistry();

	/**
	 * Returns the configured base Configura instance. Addons derive their own configuration from it and keep
	 * Identica's file format and modules without bundling a library of their own.
	 *
	 * @return base Configura instance
	 */
	@NotNull Configura getConfigura();

	/**
	 * Returns the catalog of loaded configuration documents, such as every messages document of core, the
	 * features and the loaded providers.
	 *
	 * @return config catalog
	 */
	@NotNull ConfigCatalog getConfigCatalog();

	/**
	 * Returns the service that shows players Identica's messages, where addons register other ways of showing them,
	 * such as dialogs.
	 *
	 * @return message service
	 */
	@NotNull MessageService getMessageService();

	/**
	 * Returns the service that tells whether a connection still has to authenticate, register or migrate.
	 *
	 * @return pending scenario service
	 */
	@NotNull PendingScenarioService getPendingScenarioService();

	/**
	 * Returns the registry of the places where a connection waits while one of its steps waits for the player. An
	 * addon registers a {@link Checkpoint} here to hold such connections itself, for example to ask the player on
	 * the loading screen, and unregisters it when the addon is disabled.
	 *
	 * @return checkpoint registry
	 */
	@NotNull Checkpoints getCheckpoints();

	/**
	 * Returns the registry of components that {@code /identica admin reload} refreshes. An addon registers a
	 * {@link Reloadable} here to be reloaded after every configuration document, and unregisters it when the
	 * addon is disabled.
	 *
	 * @return reloadable registry
	 */
	@NotNull Registry<Reloadable> getReloadables();

	/**
	 * Finds a service that has no getter here, such as the API of a feature:
	 *
	 * <pre>{@code
	 * identica.findService(VerificationService.class).ifPresent(verification -> ...);
	 * }</pre>
	 *
	 * @param type service interface
	 * @param <T> service type
	 * @return the service, or empty when Identica offers none of that type
	 */
	@NotNull <T> Optional<T> findService(@NotNull Class<T> type);
}
