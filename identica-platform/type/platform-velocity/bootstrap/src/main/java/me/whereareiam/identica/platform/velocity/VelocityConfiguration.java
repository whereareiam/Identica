package me.whereareiam.identica.platform.velocity;

import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.OptionalBinder;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.RequiredArgsConstructor;
import me.whereareiam.attache.LibraryManager;
import me.whereareiam.identica.integration.statistics.StatisticsConfiguration;
import me.whereareiam.identica.integration.statistics.StatisticsPlatform;
import me.whereareiam.identica.platform.common.PlatformConfiguration;
import me.whereareiam.identica.listener.ListenerRegistrar;
import me.whereareiam.identica.logging.LoggingHelper;
import me.whereareiam.identica.platform.velocity.adapter.VelocityPlatformHandshakeApplierRegistry;
import me.whereareiam.identica.platform.velocity.adapter.auth.VelocityHandshakeDecisionAdapter;
import me.whereareiam.identica.platform.velocity.adapter.auth.VelocityLoginDecisionAdapter;
import me.whereareiam.identica.platform.velocity.adapter.auth.VelocityResumeDecisionAdapter;
import me.whereareiam.identica.platform.velocity.adapter.routing.VelocityForcedHostAdapter;
import me.whereareiam.identica.platform.velocity.api.handshake.VelocityHandshakeContext;
import me.whereareiam.identica.platform.velocity.adapter.profile.VelocityProfileRewriteAdapter;
import me.whereareiam.identica.platform.velocity.delivery.VelocityDeliveryCoordinator;
import me.whereareiam.identica.platform.velocity.listener.VelocityListenerRegistrar;
import me.whereareiam.identica.platform.velocity.listener.routing.VelocityRoutingIntentListener;
import me.whereareiam.identica.platform.velocity.logging.VelocityLoggingHelper;
import me.whereareiam.identica.platform.velocity.mapper.CommandSourceMapper;
import me.whereareiam.identica.service.Scheduler;
import me.whereareiam.keystone.Actor;
import org.incendo.cloud.CommandManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.nio.file.Path;

@RequiredArgsConstructor
public class VelocityConfiguration extends PlatformConfiguration<VelocityHandshakeContext> {
	private final ProxyServer proxyServer;
	private final VelocityIdentica plugin;
	private final PluginContainer pluginContainer;
	private final Path dataPath;
	private final Logger logger;
	private final LibraryManager libraryManager;

	@Override
	protected void configurePlatform() {
		bind(Path.class).toInstance(dataPath);
		bind(ProxyServer.class).toInstance(proxyServer);
		bind(EventManager.class).toInstance(proxyServer.getEventManager());
		bind(VelocityIdentica.class).toInstance(plugin);
		bind(PluginContainer.class).toInstance(pluginContainer);
		bind(Logger.class).toInstance(logger);
		bind(LibraryManager.class).toInstance(libraryManager);
		bind(LoggingHelper.class).to(VelocityLoggingHelper.class);
		bind(ListenerRegistrar.class).to(VelocityListenerRegistrar.class);

		bind(VelocityRoutingIntentListener.class).asEagerSingleton();
		bind(CommandSourceMapper.class).asEagerSingleton();
		OptionalBinder.newOptionalBinder(binder(), Scheduler.class)
				.setBinding()
				.to(VelocityScheduler.class)
				.asEagerSingleton();

		bind(new TypeLiteral<CommandManager<Actor>>() {}).toProvider(VelocityCommandManagerProvider.class);

		bind(StatisticsPlatform.class).to(VelocityStatisticsPlatform.class);
		install(new StatisticsConfiguration());
	}

	@Override
	protected @NotNull Class<VelocityDeliveryCoordinator> delivery() {
		return VelocityDeliveryCoordinator.class;
	}

	@Override
	protected @NotNull Class<VelocityForcedHostAdapter> forcedHost() {
		return VelocityForcedHostAdapter.class;
	}

	@Override
	protected @NotNull Class<VelocityPlatformHandshakeApplierRegistry> handshakeApplierRegistry() {
		return VelocityPlatformHandshakeApplierRegistry.class;
	}

	@Override
	protected @NotNull Class<VelocityHandshakeDecisionAdapter> handshakeDecision() {
		return VelocityHandshakeDecisionAdapter.class;
	}

	@Override
	protected @NotNull Class<VelocityLoginDecisionAdapter> loginDecision() {
		return VelocityLoginDecisionAdapter.class;
	}

	@Override
	protected @NotNull Class<VelocityResumeDecisionAdapter> resumeDecision() {
		return VelocityResumeDecisionAdapter.class;
	}

	@Override
	protected @NotNull Class<VelocityProfileRewriteAdapter> profile() {
		return VelocityProfileRewriteAdapter.class;
	}
}
