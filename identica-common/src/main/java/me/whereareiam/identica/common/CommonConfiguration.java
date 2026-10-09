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
import me.whereareiam.identica.common.config.IdenticaModule;
import me.whereareiam.identica.common.config.resolver.FileSystemConfigurationTypeResolver;
import me.whereareiam.identica.common.conflict.ConflictConfiguration;
import me.whereareiam.identica.common.connection.ConnectionStateConfiguration;
import me.whereareiam.identica.common.event.EventController;
import me.whereareiam.identica.common.identity.IdentityConfiguration;
import me.whereareiam.identica.common.identity.session.SessionConfiguration;
import me.whereareiam.identica.common.listener.ListenerConfiguration;
import me.whereareiam.identica.common.messaging.MessagingConfiguration;
import me.whereareiam.identica.common.prepare.PipelineStateConfiguration;
import me.whereareiam.identica.common.provider.ProviderConfiguration;
import me.whereareiam.identica.common.provider.SerializerEngineProvider;
import me.whereareiam.identica.common.registry.RegistryConfiguration;
import me.whereareiam.identica.common.replication.ReplicationConfiguration;
import me.whereareiam.identica.common.routing.RoutingConfiguration;
import me.whereareiam.identica.config.ConfigurationTypeResolver;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.logging.BannerContributor;
import me.whereareiam.identica.util.EventUtil;
import me.whereareiam.keystone.serializer.SerializerEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
		Multibinder.newSetBinder(binder(), me.whereareiam.identica.lifecycle.RuntimeLifecycle.class);
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
	void initializeConfigura(Configura configura) {
		Config.setConfigured(configura);
	}

	@Provides
	@Singleton
	@Named("dataPath")
	Path provideDataPath() {
		return ensureDirectory(dataPath, "data");
	}

	@Provides
	@Singleton
	Configura provideConfigura(@Named("dataPath") Path dataPath) {
		Format format = new FileSystemConfigurationTypeResolver(dataPath).getConfigurationType();
		return Config.builder()
				.format(format)
				.module(new IdenticaModule())
				.build();
	}

	@Provides
	@Singleton
	@Named("providersPath")
	Path provideProvidersPath(@Named("dataPath") Path dataPath) {
		return ensureDirectory(dataPath.resolve("providers"), "providers");
	}

	@Provides
	@Singleton
	@Named("featuresPath")
	Path provideFeaturesPath(@Named("dataPath") Path dataPath) {
		return ensureDirectory(dataPath.resolve("features"), "features");
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
