package me.whereareiam.identica.platform.bungeecord;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import lombok.RequiredArgsConstructor;
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
import me.whereareiam.identica.platform.bungeecord.adapter.BungeeCordHandshakeApplierRegistry;
import me.whereareiam.identica.platform.bungeecord.api.handshake.BungeeCordHandshakeContext;
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
		bind(BungeeCordRoutingIntentListener.class).asEagerSingleton();
		bind(new TypeLiteral<HandshakeApplierRegistry<BungeeCordHandshakeContext>>() {})
				.to(BungeeCordHandshakeApplierRegistry.class)
				.asEagerSingleton();

		bind(CommandSourceMapper.class).asEagerSingleton();
		OptionalBinder.newOptionalBinder(binder(), Scheduler.class)
				.setBinding()
				.to(BungeeCordScheduler.class)
				.asEagerSingleton();

		bind(new TypeLiteral<CommandManager<Actor>>() {}).toProvider(BungeeCordCommandManagerProvider.class);

		configureBStats();
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
