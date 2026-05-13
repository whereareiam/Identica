package me.whereareiam.identica.platform.velocity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Constants;
import me.whereareiam.identica.integration.bstats.TelemetryRegistrar;
import me.whereareiam.identica.integration.bstats.chart.type.Chart;
import me.whereareiam.identica.logging.Logger;
import org.bstats.velocity.Metrics;

import java.util.Set;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class VelocityMetrics implements TelemetryRegistrar {
	private final Metrics.Factory factory;
	private final VelocityIdentica plugin;
	private final Set<Chart> charts;
	private boolean registered;

	@Override
	public synchronized void register() {
		if (registered) return;
		if (Constants.BStats.VELOCITY_ID <= 0) {
			Logger.info("Skipping bStats registration because Constants.BStats.VELOCITY_ID is not configured yet.");
			return;
		}

		Metrics metrics = factory.make(plugin, Constants.BStats.VELOCITY_ID);
		charts.stream()
				.map(Chart::getChart)
				.forEach(metrics::addCustomChart);
		registered = true;
	}
}
