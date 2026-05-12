package me.whereareiam.identica.common;

import com.google.inject.*;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import lombok.RequiredArgsConstructor;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.pipeline.completion.CompletionPendingStore;
import me.whereareiam.identica.common.completion.DefaultCompletionPendingStore;
import me.whereareiam.identica.common.adapter.ConnectionDecisionApplier;
import me.whereareiam.identica.common.adapter.HandshakeDecisionProcessor;
import me.whereareiam.identica.common.adapter.ProfileRewriteProcessor;
import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.common.identity.account.DefaultAccountService;
import me.whereareiam.identica.identity.account.RegistrationAccountService;
import me.whereareiam.identica.common.handshake.DefaultHandshakeStore;
import me.whereareiam.identica.common.identity.account.DefaultRegistrationAccountService;
import me.whereareiam.identica.common.migration.DefaultMigrationService;
import me.whereareiam.identica.common.replication.DefaultReplicationAdapter;
import me.whereareiam.identica.common.replication.DefaultReplicationSystem;
import me.whereareiam.identica.common.replication.NoopReplicationAdapter;
import me.whereareiam.identica.common.replication.event.DefaultReplicatedEventRegistry;
import me.whereareiam.identica.common.replication.event.ReplicatedEventBridge;
import me.whereareiam.identica.common.config.IdenticaModule;
import me.whereareiam.identica.common.config.provider.*;
import me.whereareiam.identica.common.config.resolver.FileSystemConfigurationTypeResolver;
import me.whereareiam.identica.common.conflict.ConflictPrepareLifecycle;
import me.whereareiam.identica.common.conflict.DefaultConflictService;
import me.whereareiam.identica.common.conflict.type.UsernameConflictType;
import me.whereareiam.identica.conflict.ConflictGuard;
import me.whereareiam.identica.common.provider.DefaultProviderAttemptStore;
import me.whereareiam.identica.common.event.EventController;
import me.whereareiam.identica.common.identity.DefaultReservationCache;
import me.whereareiam.identica.common.listener.DefaultDynamicListenerRegistry;
import me.whereareiam.identica.common.listener.SessionClosedDisconnectListener;
import me.whereareiam.identica.common.listener.SessionReplacedListener;
import me.whereareiam.identica.common.identity.DefaultIdentityService;
import me.whereareiam.identica.common.provider.DefaultProviderManager;
import me.whereareiam.identica.common.provider.DefaultProviderOperations;
import me.whereareiam.identica.common.provider.ProviderEntrypointSelectionLifecycle;
import me.whereareiam.identica.common.provider.SerializerEngineProvider;
import me.whereareiam.identica.common.prepare.DefaultPrepareStateStore;
import me.whereareiam.identica.common.provider.reader.DefaultProviderDescriptorReader;
import me.whereareiam.identica.common.registry.ReloadableRegistry;
import me.whereareiam.identica.common.sentinel.DefaultSentinelService;
import me.whereareiam.identica.common.sentinel.ConnectionAttemptSentinelLifecycle;
import me.whereareiam.identica.common.sentinel.SentinelRegistry;
import me.whereareiam.identica.common.sentinel.ResumeSpamSentinelDefinition;
import me.whereareiam.identica.common.verification.DefaultVerificationRegistry;
import me.whereareiam.identica.common.verification.DefaultVerificationService;
import me.whereareiam.identica.common.verification.challenge.VerificationChallengeStore;
import me.whereareiam.identica.common.verification.enrollment.VerificationEnrollmentStore;
import me.whereareiam.identica.common.verification.codec.VerificationStateCodec;
import me.whereareiam.identica.common.verification.type.totp.TotpVerificationMethod;
import me.whereareiam.identica.common.routing.DefaultRoutingCoordinator;
import me.whereareiam.identica.common.routing.DefaultRoutingIntentStore;
import me.whereareiam.identica.common.routing.RoutingPlanner;
import me.whereareiam.identica.common.routing.RoutingRetryCoordinator;
import me.whereareiam.identica.common.routing.RoutingTargetMissingListener;
import me.whereareiam.identica.common.identity.session.DefaultSessionService;
import me.whereareiam.identica.common.identity.session.SessionRefreshCoordinator;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.replication.ReplicationAdapter;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.config.ConfigurationTypeResolver;
import me.whereareiam.identica.conflict.ConflictService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.ReservationCache;
import me.whereareiam.identica.listener.DynamicListenerRegistry;
import me.whereareiam.identica.logging.BannerContributor;
import me.whereareiam.identica.model.config.*;
import me.whereareiam.identica.model.config.persistence.Persistence;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.provider.ProviderDescriptorReader;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.replication.event.ReplicatedEventRegistry;
import me.whereareiam.identica.sentinel.SentinelService;
import me.whereareiam.identica.sentinel.SentinelDefinition;
import me.whereareiam.identica.routing.RoutingAttemptService;
import me.whereareiam.identica.routing.RoutingCoordinator;
import me.whereareiam.identica.routing.RoutingIntentStore;
import me.whereareiam.identica.verification.VerificationService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.service.MigrationService;
import me.whereareiam.identica.util.EventUtil;
import me.whereareiam.identica.verification.VerificationMethod;
import me.whereareiam.identica.verification.VerificationRegistry;
import me.whereareiam.keystone.serializer.SerializerEngine;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.provider.ProviderAttemptStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

@RequiredArgsConstructor
public class CommonConfiguration extends AbstractModule {
	private final Path dataPath;

	@Override
	protected void configure() {
		requestInjection(this);

		// Configuration core
		bind(ConfigurationTypeResolver.class)
				.to(FileSystemConfigurationTypeResolver.class)
				.asEagerSingleton();

		// Configuration providers
		bind(Settings.class).toProvider(SettingsProvider.class);
		bind(Messages.class).toProvider(MessagesProvider.class);
		bind(Commands.class).toProvider(CommandsProvider.class);
		bind(Providers.class).toProvider(ProvidersProvider.class);
		bind(Verification.class).toProvider(VerificationProvider.class);
		bind(Persistence.class).toProvider(PersistenceProvider.class);
		bind(Replication.class).toProvider(ReplicationProvider.class);

		// Registries & reloadables
		bind(new TypeLiteral<Registry<Reloadable>>() {})
				.to(ReloadableRegistry.class)
				.asEagerSingleton();
		bind(new TypeLiteral<Set<Reloadable>>() {})
				.annotatedWith(Names.named("reloadables"))
				.toProvider(ReloadableRegistry.class)
				.asEagerSingleton();
		bind(ResumeSpamSentinelDefinition.class).asEagerSingleton();
		bind(new TypeLiteral<Registry<SentinelDefinition>>() {})
				.to(SentinelRegistry.class)
				.asEagerSingleton();
		bind(HandshakeStore.class).to(DefaultHandshakeStore.class).asEagerSingleton();
		bind(ProviderAttemptStore.class).to(DefaultProviderAttemptStore.class).asEagerSingleton();
		bind(PrepareStateStore.class).to(DefaultPrepareStateStore.class).asEagerSingleton();
		bind(CompletionPendingStore.class).to(DefaultCompletionPendingStore.class).asEagerSingleton();
		bind(VerificationEnrollmentStore.class).asEagerSingleton();
		bind(VerificationChallengeStore.class).asEagerSingleton();
		bind(VerificationStateCodec.class).asEagerSingleton();

		// Replication
		OptionalBinder.newOptionalBinder(binder(), Key.get(ReplicationAdapter.class, Names.named("replicationAdapter")))
				.setDefault()
				.to(NoopReplicationAdapter.class)
				.asEagerSingleton();

		bind(ReplicationAdapter.class).to(DefaultReplicationAdapter.class).asEagerSingleton();
		bind(ReplicationSystem.class).to(DefaultReplicationSystem.class).asEagerSingleton();
		bind(ReplicatedEventRegistry.class).to(DefaultReplicatedEventRegistry.class).asEagerSingleton();
		bind(ReplicatedEventBridge.class).asEagerSingleton();
		bind(ReservationCache.class).to(DefaultReservationCache.class).asEagerSingleton();
		bind(SentinelService.class).to(DefaultSentinelService.class).asEagerSingleton();

		// Account + presence
		bind(AccountService.class).to(DefaultAccountService.class).asEagerSingleton();
		bind(RegistrationAccountService.class).to(DefaultRegistrationAccountService.class).asEagerSingleton();
		bind(MigrationService.class).to(DefaultMigrationService.class).asEagerSingleton();
		bind(VerificationService.class).to(DefaultVerificationService.class).asEagerSingleton();
		bind(IdentityService.class).to(DefaultIdentityService.class).asEagerSingleton();
		bind(VerificationRegistry.class).to(DefaultVerificationRegistry.class).asEagerSingleton();
		Multibinder.newSetBinder(binder(), VerificationMethod.class)
				.addBinding()
				.to(TotpVerificationMethod.class);

		// Session lifecycle
		bind(SessionService.class).to(DefaultSessionService.class).asEagerSingleton();
		bind(SessionRefreshCoordinator.class).asEagerSingleton();

		// Platform adaptation helpers
		bind(ConnectionDecisionApplier.class).asEagerSingleton();
		bind(HandshakeDecisionProcessor.class).asEagerSingleton();
		bind(ProfileRewriteProcessor.class).asEagerSingleton();

		// Routing
		bind(RoutingIntentStore.class).to(DefaultRoutingIntentStore.class).asEagerSingleton();
		bind(RoutingPlanner.class).asEagerSingleton();
		bind(DefaultRoutingCoordinator.class).asEagerSingleton();
		bind(RoutingCoordinator.class).to(DefaultRoutingCoordinator.class);
		bind(RoutingAttemptService.class).to(DefaultRoutingCoordinator.class);
		bind(RoutingRetryCoordinator.class).asEagerSingleton();
		bind(RoutingTargetMissingListener.class).asEagerSingleton();

		// Conflict resolution
		Multibinder.newSetBinder(binder(), ConflictGuard.class);
		bind(ConflictService.class).to(DefaultConflictService.class).asEagerSingleton();
		bind(UsernameConflictType.class).asEagerSingleton();

		// Event listeners
		bind(SessionClosedDisconnectListener.class).asEagerSingleton();
		bind(SessionReplacedListener.class).asEagerSingleton();
		bind(ConflictPrepareLifecycle.class).asEagerSingleton();
		bind(ProviderEntrypointSelectionLifecycle.class).asEagerSingleton();
		bind(ConnectionAttemptSentinelLifecycle.class).asEagerSingleton();
		bind(DynamicListenerRegistry.class).to(DefaultDynamicListenerRegistry.class).asEagerSingleton();

		// Provider system
		bind(ProviderDescriptorReader.class).to(DefaultProviderDescriptorReader.class).asEagerSingleton();
		bind(ProviderManager.class).to(DefaultProviderManager.class).asEagerSingleton();
		bind(ProviderOperations.class).to(DefaultProviderOperations.class).asEagerSingleton();

		// Core services
		bind(EventManager.class).to(EventController.class);
		bind(SerializerEngine.class).toProvider(SerializerEngineProvider.class);
		Multibinder.newSetBinder(binder(), BannerContributor.class);
		bind(Identica.class).asEagerSingleton();
	}

	@Inject
	void initializeSerializationHelper(Provider<SerializerEngine> serializerProvider) {
		Serializer.initialize(serializerProvider);
	}

	@Inject
	void initializeEventUtil(EventManager eventManager) {
		EventUtil.initialize(eventManager);
	}

	@Inject
	void initializeConfigura() {
		Path configuredDataPath = ensureDirectory(dataPath, "data");
		Format format = new FileSystemConfigurationTypeResolver(configuredDataPath).getConfigurationType();
		Config config = Config.builder()
				.format(format)
				.module(new IdenticaModule())
				.build();
		Config.setDefaults(config);
	}

	@Provides
	@Singleton
	@Named("dataPath")
	Path provideDataPath() {
		return ensureDirectory(dataPath, "data");
	}

	@Provides
	@Singleton
	@Named("providersPath")
	Path provideProvidersPath(@Named("dataPath") Path dataPath) {
		return ensureDirectory(dataPath.resolve("providers"), "providers");
	}

	private Path ensureDirectory(Path path, String label) {
		try {
			Files.createDirectories(path);
			return path;
		} catch (IOException e) {
			throw new RuntimeException("Failed to create " + label + " directory", e);
		}
	}
}
