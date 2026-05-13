package me.whereareiam.identica.platform.velocity;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
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
import me.whereareiam.identica.platform.velocity.adapter.VelocityHandshakeApplierRegistry;
import me.whereareiam.identica.platform.velocity.api.handshake.VelocityHandshakeContext;
import me.whereareiam.identica.platform.velocity.listener.VelocityListenerRegistrar;
import me.whereareiam.identica.platform.velocity.listener.routing.VelocityRoutingIntentListener;
import me.whereareiam.identica.platform.velocity.logging.VelocityLoggingHelper;
import me.whereareiam.identica.platform.velocity.mapper.CommandSourceMapper;
import me.whereareiam.identica.service.Scheduler;
import me.whereareiam.keystone.Actor;
import org.incendo.cloud.CommandManager;
import org.slf4j.Logger;

import java.nio.file.Path;

@RequiredArgsConstructor
public class VelocityConfiguration extends AbstractModule {
	private final ProxyServer proxyServer;
	private final VelocityIdentica plugin;
	private final PluginContainer pluginContainer;
	private final Path dataPath;
	private final Logger logger;

	@Override
	protected void configure() {
		bind(Path.class).toInstance(dataPath);
		bind(ProxyServer.class).toInstance(proxyServer);
		bind(EventManager.class).toInstance(proxyServer.getEventManager());
		bind(VelocityIdentica.class).toInstance(plugin);
		bind(PluginContainer.class).toInstance(pluginContainer);
		bind(Logger.class).toInstance(logger);
		bind(LoggingHelper.class).to(VelocityLoggingHelper.class);
		bind(ListenerRegistrar.class).to(VelocityListenerRegistrar.class);
		bind(VelocityRoutingIntentListener.class).asEagerSingleton();
		bind(new TypeLiteral<HandshakeApplierRegistry<VelocityHandshakeContext>>() {})
				.to(VelocityHandshakeApplierRegistry.class)
				.asEagerSingleton();

		bind(CommandSourceMapper.class).asEagerSingleton();
		OptionalBinder.newOptionalBinder(binder(), Scheduler.class)
				.setBinding()
				.to(VelocityScheduler.class)
				.asEagerSingleton();

		bind(new TypeLiteral<CommandManager<Actor>>() {}).toProvider(VelocityCommandManagerProvider.class);

		configureBStats();
	}

	private void configureBStats() {
		bind(TelemetryRegistrar.class).to(VelocityMetrics.class);
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
