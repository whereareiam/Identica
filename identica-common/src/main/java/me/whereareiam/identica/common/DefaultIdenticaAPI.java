package me.whereareiam.identica.common;

import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.IdenticaAPI;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.checkpoint.Checkpoints;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.config.ConfigCatalog;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.identity.account.RegistrationAccountService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.pipeline.PendingScenarioService;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.service.MigrationService;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Resolves each service from the core injector when it is asked for, so a service that starts late, such as
 * the command service, is the one that runs.
 */
@Singleton
public class DefaultIdenticaAPI implements IdenticaAPI {
	private static final Key<Registry<Reloadable>> RELOADABLES = Key.get(new TypeLiteral<>() {});

	private final Injector injector;

	@Inject
	public DefaultIdenticaAPI(@NotNull Injector injector) {
		this.injector = injector;
	}

	@Override
	public @NotNull EventManager getEventManager() {
		return injector.getInstance(EventManager.class);
	}

	@Override
	public @NotNull ConnectionCoordinator getConnectionCoordinator() {
		return injector.getInstance(ConnectionCoordinator.class);
	}

	@Override
	public @NotNull ProviderManager getProviderManager() {
		return injector.getInstance(ProviderManager.class);
	}

	@Override
	public @NotNull ProviderOperations getProviderOperations() {
		return injector.getInstance(ProviderOperations.class);
	}

	@Override
	public @NotNull CommandService getCommandService() {
		return injector.getInstance(CommandService.class);
	}

	@Override
	public @NotNull RegistrationAccountService getRegistrationAccountService() {
		return injector.getInstance(RegistrationAccountService.class);
	}

	@Override
	public @NotNull AccountService getAccountService() {
		return injector.getInstance(AccountService.class);
	}

	@Override
	public @NotNull IdentityService getIdentityService() {
		return injector.getInstance(IdentityService.class);
	}

	@Override
	public @NotNull SessionService getSessionService() {
		return injector.getInstance(SessionService.class);
	}

	@Override
	public @NotNull MigrationService getMigrationService() {
		return injector.getInstance(MigrationService.class);
	}

	@Override
	public @NotNull ReplicationSystem getReplicationSystem() {
		return injector.getInstance(ReplicationSystem.class);
	}

	@Override
	public @NotNull PipelineExtensionRegistry getPipelineExtensionRegistry() {
		return injector.getInstance(PipelineExtensionRegistry.class);
	}

	@Override
	public @NotNull Configura getConfigura() {
		return injector.getInstance(Configura.class);
	}

	@Override
	public @NotNull ConfigCatalog getConfigCatalog() {
		return injector.getInstance(ConfigCatalog.class);
	}

	@Override
	public @NotNull PendingScenarioService getPendingScenarioService() {
		return injector.getInstance(PendingScenarioService.class);
	}

	@Override
	public @NotNull Checkpoints getCheckpoints() {
		return injector.getInstance(Checkpoints.class);
	}

	@Override
	public @NotNull Registry<Reloadable> getReloadables() {
		return injector.getInstance(RELOADABLES);
	}

	/**
	 * Returns only explicitly bound services; asking for an unbound class must not make Guice construct one.
	 */
	@Override
	public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) {
		Binding<T> binding = injector.getExistingBinding(Key.get(type));
		if (binding == null) return Optional.empty();

		return Optional.of(binding.getProvider().get());
	}
}
