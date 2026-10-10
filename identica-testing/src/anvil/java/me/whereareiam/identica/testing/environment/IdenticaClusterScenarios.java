package me.whereareiam.identica.testing.environment;

import com.redis.testcontainers.RedisContainer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.anvil.integration.junit.AnvilScenarioFactory;
import me.whereareiam.anvil.integration.junit.ScenarioResources;
import me.whereareiam.identica.common.config.defaults.ReplicationDefaults;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.config.persistence.external.PostgresPersistence;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.JourneyPolicy;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Builds the two-proxy network an {@link IdenticaCluster} declaration describes. The scenario owns the database
 * and Redis containers, so they stop with the network.
 */
public final class IdenticaClusterScenarios implements AnvilScenarioFactory<IdenticaCluster> {
	private static final DockerImageName POSTGRES = DockerImageName.parse("postgres:17-alpine");
	private static final DockerImageName REDIS = DockerImageName.parse("redis:7.2-alpine");

	@Override
	public @NotNull AnvilScenario create(@NotNull IdenticaCluster cluster, @NotNull ScenarioResources resources) {
		assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "An Identica cluster needs Docker for its database and Redis");

		YggdrasilMock yggdrasil = resources.own(YggdrasilMock.start());
		PostgreSQLContainer database = resources.own(started(new PostgreSQLContainer(POSTGRES)));
		RedisContainer redis = cluster.replication() ? resources.own(started(new RedisContainer(REDIS))) : null;

		Map<String, Map<String, String>> proxies = new LinkedHashMap<>();
		for (String proxy : List.of(IdenticaNetwork.PROXY_A, IdenticaNetwork.PROXY_B)) {
			Map<String, String> configuration = IdenticaConfiguration.files(JourneyMode.INTERACTIVE, JourneyPolicy.PREFER,
					EnumSet.of(Provider.CREDENTIAL), false, true, yggdrasil);
			configuration.put("persistence.yml", IdenticaConfiguration.write(persistence(database)));
			if (redis != null)
				configuration.put("replication.yml", IdenticaConfiguration.write(replication(proxy, redis)));
			proxies.put(proxy, configuration);
		}

		return IdenticaNetwork.velocity(redis == null ? "identica-cluster-database" : "identica-cluster-replicated",
				proxies, yggdrasil.sessionServer());
	}

	private static <C extends GenericContainer<?>> C started(C container) {
		container.start();
		return container;
	}

	private static PostgresPersistence persistence(PostgreSQLContainer database) {
		PostgresPersistence persistence = new PostgresPersistence();
		persistence.setHost(database.getHost());
		persistence.setPort(database.getFirstMappedPort());
		persistence.setDatabase(database.getDatabaseName());
		persistence.setUsername(database.getUsername());
		persistence.setPassword(database.getPassword());

		return persistence;
	}

	/**
	 * Each proxy is its own replication peer on the shared Redis.
	 */
	private static Replication replication(String proxy, RedisContainer redis) {
		Replication replication = new ReplicationDefaults().supply(new Replication());
		replication.setEnabled(true);
		replication.setServerId(proxy);
		replication.getRedis().setHost(redis.getHost());
		replication.getRedis().setPort(redis.getFirstMappedPort());

		return replication;
	}
}
