package me.whereareiam.identica.integration.statistics;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.database.schema.SchemaInstaller;
import me.whereareiam.identica.integration.statistics.database.StatisticsLeaseEntity;
import me.whereareiam.identica.integration.statistics.database.StatisticsSchema;
import me.whereareiam.identica.integration.statistics.error.LoggedErrors;
import me.whereareiam.identica.integration.statistics.error.StatisticsErrors;
import me.whereareiam.identica.integration.statistics.metric.StatisticsMetrics;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.statistica.Statistica;
import me.whereareiam.statistica.adapter.jdbc.JdbcElection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;

/**
 * Starts Identica's statistics in {@link StartupPhase#INTEGRATIONS}, once the providers are loaded, and stops them
 * before the providers are unloaded and the database closes, so the last submission still describes the running
 * network and the lease is given up.
 * <p>
 * Statistics never keep Identica from running: when they cannot start, Identica logs why and
 * carries on without them.
 */
@Singleton
public final class StatisticsLifecycle implements RuntimeLifecycle {
	/** Identica's FastStats project. The token ships with every copy of Identica and is not a secret. */
	static final String FASTSTATS_TOKEN = "8ee4383c91584304940b0bc8ec7233ec";

	private final @NotNull StatisticsPlatform platform;
	private final @NotNull StatisticsMetrics metrics;
	private final @NotNull OfficialProviders officialProviders;
	private final @NotNull Provider<SchemaInstaller> schemaInstaller;
	private final @NotNull Provider<DataSource> dataSource;
	private @Nullable Statistica statistica;

	@Inject
	public StatisticsLifecycle(
			@NotNull StatisticsPlatform platform,
			@NotNull StatisticsMetrics metrics,
			@NotNull OfficialProviders officialProviders,
			@NotNull Provider<SchemaInstaller> schemaInstaller,
			@NotNull Provider<DataSource> dataSource
	) {
		this.platform = platform;
		this.metrics = metrics;
		this.officialProviders = officialProviders;
		this.schemaInstaller = schemaInstaller;
		this.dataSource = dataSource;
	}

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.INTEGRATIONS;
	}

	@Override
	public synchronized void initialize() {
		if (statistica != null) return;

		try {
			schemaInstaller.get().install(new StatisticsSchema());

			Statistica statistica = platform.builder()
					.faststats(FASTSTATS_TOKEN)
					.election(JdbcElection.of(dataSource.get(), StatisticsLeaseEntity.TABLE))
					.metrics(metrics.create())
					.errors(StatisticsErrors.policy(officialProviders))
					.build();
			statistica.start();

			Logger.setErrorListener(new LoggedErrors(statistica.errors()));
			this.statistica = statistica;
		} catch (RuntimeException | LinkageError failure) {
			Logger.warn("Usage statistics are off: %s", String.valueOf(failure.getMessage()));
		}
	}

	@Override
	public synchronized void shutdown() {
		if (statistica == null) return;

		Logger.setErrorListener(null);
		statistica.stop();
		statistica = null;
	}
}
