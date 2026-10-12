package me.whereareiam.identica.testing.environment;

import com.redis.testcontainers.RedisContainer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.anvil.integration.junit.AnvilScenarioFactory;
import me.whereareiam.anvil.integration.junit.ScenarioResources;
import me.whereareiam.identica.adapter.database.testing.DatabaseContainers;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.config.persistence.external.PostgresPersistence;
import me.whereareiam.identica.type.pipeline.journey.JourneyChoice;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Builds the two-proxy network an {@link IdenticaCluster} declaration describes. The scenario owns the database
 * and Redis containers, so they stop with the network.
 */
public final class IdenticaClusterScenarios implements AnvilScenarioFactory<IdenticaCluster> {
	private static final DockerImageName REDIS = DockerImageName.parse("redis:7.2-alpine");

	@Override
	public @NotNull AnvilScenario create(@NotNull IdenticaCluster cluster, @NotNull ScenarioResources resources) {
		assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "An Identica cluster needs Docker for its database and Redis");

		YggdrasilMock yggdrasil = resources.own(YggdrasilMock.start());
		PostgreSQLContainer database = resources.own(started(DatabaseContainers.postgres("postgres:17-alpine")));
		RedisContainer redis = cluster.replication() ? resources.own(started(new RedisContainer(REDIS))) : null;

		Map<String, String> stepServers = cluster.ownStepServers() ? Map.of(IdenticaNetwork.PROXY_B, IdenticaNetwork.AUTH_B) : Map.of();
		Map<String, ProxyConfiguration> proxies = new LinkedHashMap<>();
		for (String proxy : List.of(IdenticaNetwork.PROXY_A, IdenticaNetwork.PROXY_B)) {
			ProxyConfiguration configuration = new ProxyConfiguration(proxy, Platform.VELOCITY, JourneyChoice.PROMPTED,
					EnumSet.of(Provider.CREDENTIAL), false, step(cluster, stepServers, proxy), cluster.complete(), yggdrasil);
			configuration.put("persistence.yml", persistence(database));
			if (redis != null)
				replicate(configuration.replication(), proxy, redis);
			for (Class<? extends Consumer<ProxyConfiguration>> change : cluster.configure())
				IdenticaScenarios.instantiate(change).accept(configuration);
			proxies.put(proxy, configuration);
		}

		String name = Stream.of("identica-cluster", redis == null ? "database" : "replicated",
						cluster.ownStepServers() ? "own-step-servers" : "",
						IdenticaScenarios.routing(cluster.step(), cluster.complete()),
						Arrays.stream(cluster.configure()).map(Class::getSimpleName).collect(Collectors.joining("+")))
				.filter(part -> !part.isEmpty())
				.collect(Collectors.joining("-"))
				.toLowerCase(Locale.ROOT);
		return IdenticaNetwork.of(name, Platform.VELOCITY, proxies, stepServers, yggdrasil.sessionServer(), Set.of(),
				MinecraftVersion.V1_21_11);
	}

	/**
	 * Returns the step target of one proxy: its own step server when it has one and the cluster routes waiting
	 * players to {@code auth}.
	 */
	private static String step(IdenticaCluster cluster, Map<String, String> stepServers, String proxy) {
		if (!IdenticaNetwork.AUTH.equals(cluster.step())) return cluster.step();

		return stepServers.getOrDefault(proxy, cluster.step());
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
	private static void replicate(Replication replication, String proxy, RedisContainer redis) {
		replication.setEnabled(true);
		replication.setServerId(proxy);
		replication.getRedis().setHost(redis.getHost());
		replication.getRedis().setPort(redis.getFirstMappedPort());
	}
}
