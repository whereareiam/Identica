package me.whereareiam.identica.platform.bungeecord;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.common.platform.PlatformAdapterBindings;
import me.whereareiam.identica.handshake.HandshakeApplierRegistry;
import me.whereareiam.identica.integration.bstats.BStatsBootstrap;
import me.whereareiam.identica.integration.bstats.TelemetryRegistrar;
import me.whereareiam.identica.integration.bstats.chart.provider.ProviderUsageChart;
import me.whereareiam.identica.integration.bstats.chart.provider.ProvidersChart;
import me.whereareiam.identica.integration.bstats.chart.system.AccountCountChart;
import me.whereareiam.identica.integration.bstats.chart.system.PersistenceTypeChart;
import me.whereareiam.identica.integration.bstats.chart.system.ReplicationTypeChart;
import me.whereareiam.identica.integration.bstats.chart.system.UniqueIdModeChart;
import me.whereareiam.identica.integration.bstats.chart.type.Chart;
import me.whereareiam.identica.integration.bstats.chart.verification.VerificationMethodsChart;
import me.whereareiam.identica.listener.ListenerRegistrar;
import me.whereareiam.identica.logging.LoggingHelper;
import me.whereareiam.identica.platform.adapter.*;
import me.whereareiam.identica.platform.bungeecord.adapter.BungeeCordPlatformHandshakeApplierRegistry;
import me.whereareiam.identica.platform.bungeecord.adapter.BungeeCordRoutingAdapter;
import me.whereareiam.identica.platform.bungeecord.adapter.auth.BungeeCordHandshakeDecisionAdapter;
import me.whereareiam.identica.platform.bungeecord.adapter.auth.BungeeCordLoginDecisionAdapter;
import me.whereareiam.identica.platform.bungeecord.adapter.auth.BungeeCordResumeDecisionAdapter;
import me.whereareiam.identica.platform.bungeecord.adapter.profile.BungeeCordProfilePrepareAdapter;
import me.whereareiam.identica.platform.bungeecord.api.handshake.BungeeCordHandshakeContext;
import me.whereareiam.identica.platform.bungeecord.delivery.BungeeCordDeliveryCoordinator;
import me.whereareiam.identica.platform.bungeecord.listener.BungeeCordListenerRegistrar;
import me.whereareiam.identica.platform.bungeecord.logging.BungeeCordLoggingHelper;
import me.whereareiam.identica.platform.bungeecord.mapper.CommandSourceMapper;
import me.whereareiam.identica.service.PlatformDeliveryAdapter;
import me.whereareiam.identica.service.Scheduler;
import me.whereareiam.identica.type.platform.PlatformAdapterRole;
import me.whereareiam.keystone.Actor;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.PluginManager;
import org.incendo.cloud.CommandManager;

import java.nio.file.Path;

@RequiredArgsConstructor
public class BungeeCordConfiguration extends AbstractModule {
	private final ProxyServer proxyServer;
	private final BungeeCordIdentica plugin;
	private final Path dataPath;
	private final BungeeAudiences audiences;

	@Override
	protected void configure() {
		bind(Path.class).toInstance(dataPath);
		bind(ProxyServer.class).toInstance(proxyServer);
		bind(PluginManager.class).toInstance(proxyServer.getPluginManager());
		bind(BungeeCordIdentica.class).toInstance(plugin);
		bind(BungeeAudiences.class).toInstance(audiences);
		bind(LoggingHelper.class).to(BungeeCordLoggingHelper.class);
		bind(ListenerRegistrar.class).to(BungeeCordListenerRegistrar.class);
		configurePlatformAdapters();

		bind(CommandSourceMapper.class).asEagerSingleton();
		OptionalBinder.newOptionalBinder(binder(), Scheduler.class)
				.setBinding()
				.to(BungeeCordScheduler.class)
				.asEagerSingleton();

		bind(new TypeLiteral<CommandManager<Actor>>() {}).toProvider(BungeeCordCommandManagerProvider.class);

		configureBStats();
	}

	private void configurePlatformAdapters() {
		bind(new TypeLiteral<PlatformHandshakeDecisionAdapter<PreLoginEvent>>() {})
				.to(BungeeCordHandshakeDecisionAdapter.class);
		bind(new TypeLiteral<PlatformLoginDecisionAdapter<PostLoginEvent>>() {})
				.to(BungeeCordLoginDecisionAdapter.class);
		bind(new TypeLiteral<PlatformResumeDecisionAdapter<ServerSwitchEvent>>() {})
				.to(BungeeCordResumeDecisionAdapter.class);
		bind(new TypeLiteral<PlatformProfileAdapter<LoginEvent>>() {})
				.to(BungeeCordProfilePrepareAdapter.class);
		bind(PlatformDeliveryAdapter.class).to(BungeeCordDeliveryCoordinator.class);
		bind(PlatformRoutingAdapter.class).to(BungeeCordRoutingAdapter.class);
		bind(new TypeLiteral<PlatformHandshakeApplierRegistry<BungeeCordHandshakeContext>>() {})
				.to(BungeeCordPlatformHandshakeApplierRegistry.class)
				.in(Singleton.class);
		bind(new TypeLiteral<HandshakeApplierRegistry<BungeeCordHandshakeContext>>() {})
				.to(BungeeCordPlatformHandshakeApplierRegistry.class)
				.in(Singleton.class);
		Multibinder.newSetBinder(
				binder(),
				new TypeLiteral<PlatformHandshakeApplierContributor<BungeeCordHandshakeContext>>() {}
		);

		var adapters = PlatformAdapterBindings.configure(binder(), "BungeeCord");
		adapters.addBinding(PlatformAdapterRole.HANDSHAKE_APPLIER_REGISTRY).to(BungeeCordPlatformHandshakeApplierRegistry.class);
		adapters.addBinding(PlatformAdapterRole.HANDSHAKE_DECISION).to(BungeeCordHandshakeDecisionAdapter.class);
		adapters.addBinding(PlatformAdapterRole.LOGIN_DECISION).to(BungeeCordLoginDecisionAdapter.class);
		adapters.addBinding(PlatformAdapterRole.RESUME_DECISION).to(BungeeCordResumeDecisionAdapter.class);
		adapters.addBinding(PlatformAdapterRole.PROFILE).to(BungeeCordProfilePrepareAdapter.class);
		adapters.addBinding(PlatformAdapterRole.DELIVERY).to(BungeeCordDeliveryCoordinator.class);
		adapters.addBinding(PlatformAdapterRole.ROUTING).to(BungeeCordRoutingAdapter.class);
	}

	private void configureBStats() {
		bind(TelemetryRegistrar.class).to(BungeeCordMetrics.class);
		bind(BStatsBootstrap.class).asEagerSingleton();

		Multibinder<Chart> charts = Multibinder.newSetBinder(binder(), Chart.class);
		charts.addBinding().to(ProvidersChart.class);
		charts.addBinding().to(ProviderUsageChart.class);
		charts.addBinding().to(VerificationMethodsChart.class);
		charts.addBinding().to(PersistenceTypeChart.class);
		charts.addBinding().to(ReplicationTypeChart.class);
		charts.addBinding().to(UniqueIdModeChart.class);
		charts.addBinding().to(AccountCountChart.class);
	}
}
