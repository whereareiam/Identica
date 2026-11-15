package me.whereareiam.identica.adapter.replication;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.adapter.replication.provider.JedisPoolProvider;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.replication.ReplicationPage;
import me.whereareiam.identica.replication.ReplicationAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Replication over Redis. An entry of a namespace is stored under {@code identica:<namespace>:<key>} and listed in
 * the sorted set {@code identica:index:<namespace>}, scored by the moment it expires. Every write changes the entry
 * and its index member in one atomic step.
 * <p>
 * Calls run on a few daemon threads, one for each connection of the pool, so a call waits in a queue instead of
 * holding a thread while it waits for a connection. A failure of Redis completes the returned future exceptionally;
 * deciding what a failure means is left to the caller.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RedisReplicationAdapter implements ReplicationAdapter, RuntimeLifecycle {
	private static final String PREFIX = "identica";
	/** The namespace part of the index keys, which no namespace may use, so an entry never names an index. */
	private static final String INDEX = "index";

	private final JedisPoolProvider poolProvider;
	private final Provider<Replication> replicationProvider;
	private final Executor executor = commandExecutor();
	private final Set<RedisSubscription> subscriptions = ConcurrentHashMap.newKeySet();

	@Override
	public boolean isAvailable() {
		return replicationProvider.get().isEnabled();
	}

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.PERSISTENCE;
	}

	@Override
	public void initialize() {
		// Subscriptions start when they are asked for.
	}

	@Override
	public void shutdown() {
		subscriptions.forEach(RedisSubscription::close);
		subscriptions.clear();
	}

	@Override
	public @NotNull CompletableFuture<Optional<byte[]>> get(@NotNull String namespace, @NotNull String key) {
		return call(jedis -> Optional.ofNullable(jedis.get(entryKey(namespace, key))));
	}

	@Override
	public @NotNull CompletableFuture<Optional<byte[]>> consume(@NotNull String namespace, @NotNull String key) {
		return call(jedis -> {
			byte[] entry = entryKey(namespace, key);
			try (Transaction transaction = jedis.multi()) {
				Response<byte[]> payload = transaction.getDel(entry);
				transaction.zrem(indexKey(namespace), key);
				transaction.exec();

				return Optional.ofNullable(payload.get());
			}
		});
	}

	@Override
	public @NotNull CompletableFuture<Void> put(@NotNull String namespace, @NotNull String key, byte[] value, long ttlMs) {
		return run(jedis -> RedisScript.PUT.run(jedis, keys(namespace, key), List.of(
				bytes(key),
				value,
				bytes(Long.toString(ttlMs))
		)));
	}

	@Override
	public @NotNull CompletableFuture<Boolean> compareAndPut(
			@NotNull String namespace,
			@NotNull String key,
			byte @Nullable [] expected,
			byte @NotNull [] value,
			long ttlMs
	) {
		return call(jedis -> RedisScript.COMPARE_AND_PUT.run(jedis, keys(namespace, key), List.of(
				bytes(key),
				value,
				bytes(Long.toString(ttlMs)),
				bytes(expected != null ? "1" : "0"),
				expected != null ? expected : new byte[0]
		)));
	}

	@Override
	public @NotNull CompletableFuture<Boolean> compareAndInvalidate(@NotNull String namespace, @NotNull String key, byte @NotNull [] expected) {
		return call(jedis -> RedisScript.COMPARE_AND_DELETE.run(jedis, keys(namespace, key), List.of(bytes(key), expected)));
	}

	@Override
	public @NotNull CompletableFuture<Void> invalidate(@NotNull String namespace, @NotNull String key) {
		return run(jedis -> {
			byte[] entry = entryKey(namespace, key);
			try (Transaction transaction = jedis.multi()) {
				transaction.del(entry);
				transaction.zrem(indexKey(namespace), key);
				transaction.exec();
			}
		});
	}

	/**
	 * Lists the keys of a namespace that have not expired by the clock of Redis, in the order they expire. The
	 * expired members are removed from the index, and the page is counted and read in the same atomic step.
	 */
	@Override
	public @NotNull CompletableFuture<ReplicationPage> listKeys(@NotNull String namespace, int page, int pageSize) {
		int safePage = Math.max(1, page);
		int safeSize = Math.max(1, pageSize);
		long from = (long) (safePage - 1) * safeSize;

		return call(jedis -> {
			String index = indexKey(namespace);
			long now = now(jedis);
			try (Transaction transaction = jedis.multi()) {
				transaction.zremrangeByScore(index, 0, now);
				Response<Long> total = transaction.zcard(index);
				Response<List<String>> members = transaction.zrange(index, from, from + safeSize - 1);
				transaction.exec();

				return new ReplicationPage(members.get(), safePage, safeSize, Math.toIntExact(total.get()));
			}
		});
	}

	@Override
	public @NotNull CompletableFuture<Void> publish(@NotNull String channel, byte[] payload) {
		return run(jedis -> jedis.publish(bytes(channel), payload));
	}

	/**
	 * Subscribes on a thread of its own that subscribes again whenever the connection drops, until Identica stops.
	 */
	@Override
	public void subscribe(@NotNull String channel, @NotNull Consumer<byte[]> handler) {
		RedisSubscription subscription = new RedisSubscription(poolProvider, channel, handler);
		subscriptions.add(subscription);
		subscription.start();
	}

	private <T> @NotNull CompletableFuture<T> call(@NotNull Function<Jedis, T> command) {
		return CompletableFuture.supplyAsync(() -> {
			try (Jedis jedis = poolProvider.get().getResource()) {
				return command.apply(jedis);
			}
		}, executor);
	}

	private @NotNull CompletableFuture<Void> run(@NotNull Consumer<Jedis> command) {
		return call(jedis -> {
			command.accept(jedis);
			return null;
		});
	}

	/**
	 * Builds the threads calls run on. They are platform threads: the pool creates connections while it holds a
	 * monitor, which on Java 21 pins a virtual thread to its carrier, and enough of them waiting for that monitor
	 * would leave none for the thread that holds it. Idle threads end, so a stopped Identica leaves none behind.
	 */
	private static @NotNull Executor commandExecutor() {
		ThreadPoolExecutor executor = new ThreadPoolExecutor(
				JedisPoolProvider.MAX_CONNECTIONS,
				JedisPoolProvider.MAX_CONNECTIONS,
				30,
				TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(),
				Thread.ofPlatform().name("identica-redis-", 0).daemon().factory()
		);
		executor.allowCoreThreadTimeOut(true);

		return executor;
	}

	/**
	 * Reads the clock of Redis, which also decides when an entry expires, in milliseconds.
	 */
	private static long now(@NotNull Jedis jedis) {
		List<String> time = jedis.time();
		return Long.parseLong(time.get(0)) * 1000 + Long.parseLong(time.get(1)) / 1000;
	}

	private static @NotNull List<byte[]> keys(@NotNull String namespace, @NotNull String key) {
		return List.of(entryKey(namespace, key), bytes(indexKey(namespace)));
	}

	private static byte @NotNull [] entryKey(@NotNull String namespace, @NotNull String key) {
		if (key.isBlank()) throw new IllegalArgumentException("A replication key must not be blank");

		return bytes(PREFIX + ':' + checkedNamespace(namespace) + ':' + key);
	}

	private static @NotNull String indexKey(@NotNull String namespace) {
		return PREFIX + ':' + INDEX + ':' + checkedNamespace(namespace);
	}

	/**
	 * Rejects a namespace that would make two different entries, or an entry and an index, share one Redis key: a
	 * blank one, one with {@code :}, which separates the parts of a key, and {@link #INDEX}.
	 */
	private static @NotNull String checkedNamespace(@NotNull String namespace) {
		if (namespace.isBlank() || namespace.indexOf(':') >= 0 || namespace.equals(INDEX))
			throw new IllegalArgumentException("A replication namespace must be a name without ':' other than '" + INDEX + "': '" + namespace + "'");

		return namespace;
	}

	private static byte @NotNull [] bytes(@NotNull String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}
}
