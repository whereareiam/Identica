package me.whereareiam.identica.platform.bungeecord;

import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.OptionalBinder;
import lombok.RequiredArgsConstructor;
import me.whereareiam.attache.LibraryManager;
import me.whereareiam.identica.integration.statistics.StatisticsConfiguration;
import me.whereareiam.identica.integration.statistics.StatisticsPlatform;
import me.whereareiam.identica.platform.common.PlatformConfiguration;
import me.whereareiam.identica.listener.ListenerRegistrar;
import me.whereareiam.identica.logging.LoggingHelper;
import me.whereareiam.identica.platform.bungeecord.adapter.BungeeCordPlatformHandshakeApplierRegistry;
import me.whereareiam.identica.platform.bungeecord.adapter.auth.BungeeCordHandshakeDecisionAdapter;
import me.whereareiam.identica.platform.bungeecord.adapter.auth.BungeeCordLoginDecisionAdapter;
import me.whereareiam.identica.platform.bungeecord.adapter.auth.BungeeCordResumeDecisionAdapter;
import me.whereareiam.identica.platform.bungeecord.adapter.routing.BungeeCordForcedHostAdapter;
import me.whereareiam.identica.platform.bungeecord.api.handshake.BungeeCordHandshakeContext;
import me.whereareiam.identica.platform.bungeecord.adapter.profile.BungeeCordProfilePrepareAdapter;
import me.whereareiam.identica.platform.bungeecord.delivery.BungeeCordDeliveryCoordinator;
import me.whereareiam.identica.platform.bungeecord.listener.BungeeCordListenerRegistrar;
import me.whereareiam.identica.platform.bungeecord.listener.routing.BungeeCordRoutingIntentListener;
import me.whereareiam.identica.platform.bungeecord.logging.BungeeCordLoggingHelper;
import me.whereareiam.identica.platform.bungeecord.mapper.CommandSourceMapper;
import me.whereareiam.identica.service.Scheduler;
import me.whereareiam.keystone.Actor;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.PluginManager;
import org.incendo.cloud.CommandManager;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

@RequiredArgsConstructor
public class BungeeCordConfiguration extends PlatformConfiguration<BungeeCordHandshakeContext> {
	private final ProxyServer proxyServer;
	private final BungeeCordIdentica plugin;
	private final Path dataPath;
	private final BungeeAudiences audiences;
	private final LibraryManager libraryManager;

	@Override
	protected void configurePlatform() {
		bind(Path.class).toInstance(dataPath);
		bind(LibraryManager.class).toInstance(libraryManager);
		bind(ProxyServer.class).toInstance(proxyServer);
		bind(PluginManager.class).toInstance(proxyServer.getPluginManager());
		bind(BungeeCordIdentica.class).toInstance(plugin);
		bind(BungeeAudiences.class).toInstance(audiences);
		bind(LoggingHelper.class).to(BungeeCordLoggingHelper.class);
		bind(ListenerRegistrar.class).to(BungeeCordListenerRegistrar.class);

		bind(BungeeCordRoutingIntentListener.class).asEagerSingleton();
		bind(CommandSourceMapper.class).asEagerSingleton();
		OptionalBinder.newOptionalBinder(binder(), Scheduler.class)
				.setBinding()
				.to(BungeeCordScheduler.class)
				.asEagerSingleton();

		bind(new TypeLiteral<CommandManager<Actor>>() {}).toProvider(BungeeCordCommandManagerProvider.class);

		bind(StatisticsPlatform.class).to(BungeeCordStatisticsPlatform.class);
		install(new StatisticsConfiguration());
	}

	@Override
	protected @NotNull Class<BungeeCordDeliveryCoordinator> delivery() {
		return BungeeCordDeliveryCoordinator.class;
	}

	@Override
	protected @NotNull Class<BungeeCordForcedHostAdapter> forcedHost() {
		return BungeeCordForcedHostAdapter.class;
	}

	@Override
	protected @NotNull Class<BungeeCordPlatformHandshakeApplierRegistry> handshakeApplierRegistry() {
		return BungeeCordPlatformHandshakeApplierRegistry.class;
	}

	@Override
	protected @NotNull Class<BungeeCordHandshakeDecisionAdapter> handshakeDecision() {
		return BungeeCordHandshakeDecisionAdapter.class;
	}

	@Override
	protected @NotNull Class<BungeeCordLoginDecisionAdapter> loginDecision() {
		return BungeeCordLoginDecisionAdapter.class;
	}

	@Override
	protected @NotNull Class<BungeeCordResumeDecisionAdapter> resumeDecision() {
		return BungeeCordResumeDecisionAdapter.class;
	}

	@Override
	protected @NotNull Class<BungeeCordProfilePrepareAdapter> profile() {
		return BungeeCordProfilePrepareAdapter.class;
	}
}
