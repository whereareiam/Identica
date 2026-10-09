package me.whereareiam.identica.platform.velocity;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import jakarta.inject.Inject;
import me.whereareiam.attache.platform.velocity.VelocityLibraryManager;
import me.whereareiam.attache.type.VerbosityMode;
import me.whereareiam.identica.Constants;
import me.whereareiam.identica.adapter.command.CommandConfiguration;
import me.whereareiam.identica.adapter.database.DatabaseConfiguration;
import me.whereareiam.identica.adapter.replication.ReplicationConfiguration;
import me.whereareiam.identica.common.CommonConfiguration;
import me.whereareiam.identica.common.Identica;
import me.whereareiam.identica.engine.EngineConfiguration;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.lifecycle.IdenticaBootstrappedEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaReadyEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
import me.whereareiam.identica.common.feature.FeatureRuntime;
import me.whereareiam.identica.feature.BuiltinFeatures;
import me.whereareiam.identica.trait.authoritative.username.UsernameConfiguration;
import com.google.inject.Module;
import java.util.ArrayList;
import java.util.List;
import me.whereareiam.identica.platform.velocity.logging.VelocityLoggingHelper;
import me.whereareiam.identica.type.PluginType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
		id = "identica",
		name = Constants.NAME,
		version = Constants.VERSION,
		authors = {"whereareiam"}
)
public class VelocityIdentica {
	private final ProxyServer proxyServer;
	private final PluginContainer pluginContainer;
	private final Path dataPath;
	private final Logger logger;
	private @Nullable EventManager eventManager;
	private @Nullable FeatureRuntime featureRuntime;

	@Inject
	public VelocityIdentica(
			ProxyServer proxyServer,
			PluginContainer pluginContainer,
			@DataDirectory Path dataPath,
			Logger logger
	) {
		this.proxyServer = proxyServer;
		this.pluginContainer = pluginContainer;
		this.dataPath = dataPath;
		this.logger = logger;
	}

	@Subscribe
	public void onProxyInitialization(ProxyInitializeEvent event) {
		PluginType.setPluginType(PluginType.VELOCITY);
		VelocityLoggingHelper.setLogger(logger);

		VelocityLibraryManager libraryManager = new VelocityLibraryManager(proxyServer, pluginContainer, logger, dataPath, ".libraries");
		libraryManager.setVerbosityMode(VerbosityMode.SUMMARY);
		libraryManager.loadDescriptors();

		featureRuntime = BuiltinFeatures.runtime(dataPath);
		List<Module> modules = new ArrayList<>(List.of(
				new CommonConfiguration(dataPath),
				new UsernameConfiguration(dataPath.resolve("identity/username")),
				new EngineConfiguration(),
				new VelocityConfiguration(proxyServer, this, pluginContainer, dataPath, logger),
				new CommandConfiguration(),
				new DatabaseConfiguration(),
				new ReplicationConfiguration()
		));
		modules.addAll(featureRuntime.modules());
		Injector injector;
		try {
			injector = Guice.createInjector(modules);
		} catch (RuntimeException | Error failure) {
			try {
				featureRuntime.shutdown();
			} catch (RuntimeException | Error cleanupFailure) {
				failure.addSuppressed(cleanupFailure);
			}
			throw failure;
		}
		EventManager eventManager = injector.getInstance(EventManager.class);
		this.eventManager = eventManager;
		try {
			injector.getInstance(Identica.class).bootstrap();
			featureRuntime.initialize(injector);
			eventManager.call(new IdenticaBootstrappedEvent());
			eventManager.call(new IdenticaReadyEvent());
		} catch (RuntimeException | Error failure) {
			eventManager.call(new IdenticaShutdownEvent());
			throw failure;
		}
	}

	@Subscribe
	public void onProxyShutdown(ProxyShutdownEvent event) {
		try {
			if (eventManager != null) eventManager.call(new IdenticaShutdownEvent());
		} finally {
			if (featureRuntime != null) featureRuntime.shutdown();
		}
	}
}
