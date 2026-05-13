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
import me.whereareiam.identica.engine.EngineConfiguration;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.lifecycle.IdenticaBootstrappedEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaReadyEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
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

		Injector injector = Guice.createInjector(
				new CommonConfiguration(dataPath),
				new EngineConfiguration(),
				new VelocityConfiguration(proxyServer, this, pluginContainer, dataPath, logger),
				new CommandConfiguration(),
				new DatabaseConfiguration(),
				new ReplicationConfiguration()
		);
		EventManager eventManager = injector.getInstance(EventManager.class);
		this.eventManager = eventManager;
		eventManager.call(new IdenticaBootstrappedEvent());
		eventManager.call(new IdenticaReadyEvent());
	}

	@Subscribe
	public void onProxyShutdown(ProxyShutdownEvent event) {
		if (eventManager == null) return;
		eventManager.call(new IdenticaShutdownEvent());
	}
}
