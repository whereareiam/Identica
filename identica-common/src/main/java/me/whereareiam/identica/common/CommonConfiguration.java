package me.whereareiam.identica.common;

import com.google.inject.*;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import lombok.RequiredArgsConstructor;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.common.config.ConfigBindings;
import me.whereareiam.identica.common.config.migration.ConfigMigrations;
import me.whereareiam.identica.common.config.ConfigurationLifecycle;
import me.whereareiam.identica.common.config.IdenticaModule;
import me.whereareiam.identica.common.config.resolver.FileSystemConfigurationTypeResolver;
import me.whereareiam.identica.common.conflict.ConflictConfiguration;
import me.whereareiam.identica.common.connection.ConnectionStateConfiguration;
import me.whereareiam.identica.common.event.EventController;
import me.whereareiam.identica.common.identity.IdentityConfiguration;
import me.whereareiam.identica.common.identity.session.SessionConfiguration;
import me.whereareiam.identica.common.listener.ListenerConfiguration;
import me.whereareiam.identica.common.logging.LoggingLifecycle;
import me.whereareiam.identica.common.messaging.MessagingConfiguration;
import me.whereareiam.identica.common.prepare.PipelineStateConfiguration;
import me.whereareiam.identica.common.provider.ProviderConfiguration;
import me.whereareiam.identica.common.provider.SerializerEngineProvider;
import me.whereareiam.identica.common.registry.RegistryConfiguration;
import me.whereareiam.identica.common.replication.ReplicationConfiguration;
import me.whereareiam.identica.common.routing.RoutingConfiguration;
import me.whereareiam.identica.config.ConfigurationTypeResolver;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.logging.BannerContributor;
import me.whereareiam.identica.util.EventUtil;
import me.whereareiam.keystone.serializer.SerializerEngine;
import me.whereareiam.strata.MigrationStream;
import me.whereareiam.strata.Strata;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import me.whereareiam.strata.adapter.configura.StrataFeature;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Set;

@RequiredArgsConstructor
public class CommonConfiguration extends AbstractModule {
	private final Path dataPath;

	@Override
	protected void configure() {
		requestInjection(this);

		// Startup, in binding order within each phase
		Multibinder<RuntimeLifecycle> lifecycles = Multibinder.newSetBinder(binder(), RuntimeLifecycle.class);
		lifecycles.addBinding().to(ConfigurationLifecycle.class);
		lifecycles.addBinding().to(LoggingLifecycle.class);
		lifecycles.addBinding().to(ApiLifecycle.class);

		// Configuration core
		bind(ConfigurationTypeResolver.class)
				.to(FileSystemConfigurationTypeResolver.class)
				.asEagerSingleton();

		// The configuration migrations of the other owners of documents, which the features add to
		Multibinder.newSetBinder(binder(), new TypeLiteral<MigrationStream<ConfigContext>>() {});

		install(new ConfigBindings());
		install(new RegistryConfiguration());
		install(new ConnectionStateConfiguration());
		install(new PipelineStateConfiguration());
		install(new MessagingConfiguration());
		install(new ReplicationConfiguration());
		install(new IdentityConfiguration());
		install(new SessionConfiguration());
		install(new RoutingConfiguration());
		install(new ConflictConfiguration());
		install(new ProviderConfiguration());
		install(new ListenerConfiguration());

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

	@Provides
	@Singleton
	@NotNull Strata provideStrata(Configura configura, @Named("dataPath") Path dataPath, Set<MigrationStream<ConfigContext>> owners) {
		return new Strata(ConfigMigrations.inOrder(ConfigMigrations.on(configura, dataPath), owners));
	}

	@Provides
	@Singleton
	@Named("dataPath")
	Path provideDataPath() {
		return dataPath;
	}

	@Provides
	@Singleton
	Configura provideConfigura(@Named("dataPath") Path dataPath) {
		Format format = new FileSystemConfigurationTypeResolver(dataPath).getConfigurationType();
		return Config.builder()
				.format(format)
				.module(new IdenticaModule())
				.feature(new StrataFeature())
				.build();
	}

	@Provides
	@Singleton
	@Named("providersPath")
	Path provideProvidersPath(@Named("dataPath") Path dataPath) {
		return dataPath.resolve("providers");
	}

	@Provides
	@Singleton
	@Named("usernamePath")
	Path provideUsernamePath(@Named("dataPath") Path dataPath) {
		return dataPath.resolve("identity/username");
	}

	@Provides
	@Singleton
	@Named("featuresPath")
	Path provideFeaturesPath(@Named("dataPath") Path dataPath) {
		return dataPath.resolve("features");
	}
}
