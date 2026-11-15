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

/**
 * The Redis pool replication talks through. It connects in {@link StartupPhase#PERSISTENCE} when replication is
 * enabled, so an unreachable Redis stops the startup, and closes when Identica stops.
 */
@Singleton
public class JedisPoolProvider implements Provider<JedisPool>, Reloadable, RuntimeLifecycle {
	/** The most connections the pool opens at once. */
	public static final int MAX_CONNECTIONS = 8;

	private final Provider<Replication> configProvider;
	private volatile JedisPool jedisPool;

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

	/**
	 * Returns the pool, connecting it first when it is not connected yet.
	 *
	 * @throws IllegalStateException when replication is disabled or Redis cannot be reached
	 */
	@Override
	public @NotNull JedisPool get() {
		JedisPool pool = jedisPool;
		if (pool != null) return pool;

		return connect();
	}

	public synchronized void close() {
		if (jedisPool == null) return;

		jedisPool.close();
		jedisPool = null;
	}

	@Override
	public synchronized void reload() {
		close();

		Replication replication = configProvider.get();
		if (replication == null || !replication.isEnabled()) return;

		connect();
	}

	private synchronized @NotNull JedisPool connect() {
		if (jedisPool != null) return jedisPool;

		Replication replication = configProvider.get();
		if (replication == null || !replication.isEnabled())
			throw new IllegalStateException("Replication settings are missing or disabled");

		JedisPool pool = createPool(replication.getRedis());
		try {
			pool.getResource().close();
		} catch (JedisConnectionException e) {
			pool.close();
			throw new IllegalStateException("Failed to connect to Redis", e);
		}

		jedisPool = pool;
		return pool;
	}

	private static @NotNull JedisPool createPool(@NotNull Replication.Redis redis) {
		String password = redis.getPassword();
		JedisPoolConfig config = new JedisPoolConfig();
		config.setMaxTotal(MAX_CONNECTIONS);

		return new JedisPool(
				config,
				redis.getHost(),
				redis.getPort(),
				redis.getTimeout(),
				password.isBlank() ? null : password,
				redis.isSsl()
		);
	}
}
