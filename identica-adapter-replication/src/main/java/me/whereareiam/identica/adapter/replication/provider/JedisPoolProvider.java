package me.whereareiam.identica.adapter.replication.provider;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.Optional;

/**
 * The Redis pool replication talks through. It connects in {@link StartupPhase#PERSISTENCE} when replication is
 * enabled, so an unreachable Redis stops the startup, and closes when Identica stops.
 */
@Singleton
public class JedisPoolProvider implements Provider<JedisPool>, Reloadable, RuntimeLifecycle {
	private final Provider<Replication> configProvider;
	private JedisPool jedisPool;

	@Inject
	public JedisPoolProvider(
			Provider<Replication> configProvider,
			Registry<Reloadable> reloadableRegistry
	) {
		this.configProvider = configProvider;

		reloadableRegistry.register(this);
	}

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.PERSISTENCE;
	}

	@Override
	public void initialize() {
		Replication replication = configProvider.get();
		if (replication != null && replication.isEnabled())
			get();
	}

	@Override
	public void shutdown() {
		close();
	}

	@Override
	public JedisPool get() {
		if (jedisPool != null)
			return jedisPool;

		Replication replication = configProvider.get();
		if (replication == null || !replication.isEnabled())
			throw new RuntimeException("Replication settings are missing or disabled");

		Replication.Redis redis = replication.getRedis();

		try {
			JedisPoolConfig poolConfig = new JedisPoolConfig();
			String password = redis.getPassword();
			if (password.isBlank())
				password = null;

			jedisPool = new JedisPool(
					poolConfig,
					redis.getHost(),
					redis.getPort(),
					redis.getTimeout(),
					password,
					redis.isSsl()
			);

			// Test connection
			jedisPool.getResource().close();

			return jedisPool;
		} catch (JedisConnectionException e) {
			throw new RuntimeException("Failed to connect to Redis", e);
		}
	}

	public synchronized void close() {
		if (jedisPool != null) {
			jedisPool.close();
			jedisPool = null;
		}
	}

	public Optional<JedisPool> getOptional() {
		try {
			return Optional.of(get());
		} catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}

	@Override
	public void reload() {
		close();

		Replication replication = configProvider.get();
		if (replication == null || !replication.isEnabled()) return;

		jedisPool = get();
	}
}
