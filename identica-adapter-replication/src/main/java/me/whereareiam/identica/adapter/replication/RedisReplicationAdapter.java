package me.whereareiam.identica.adapter.replication;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.adapter.replication.provider.JedisPoolProvider;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.replication.ReplicationAdapter;
import me.whereareiam.identica.model.replication.ReplicationPage;
import me.whereareiam.identica.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisNoScriptException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Singleton
@SuppressWarnings("resource")
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RedisReplicationAdapter implements ReplicationAdapter {
	private static final String KV_PREFIX = "identica";
	/**
	 * Writes the value only while the key holds the expected bytes ({@code ARGV[1]} is {@code 1}) or nothing
	 * ({@code ARGV[1]} is {@code 0}), and lists the key in the namespace index as {@link #put} does. A script runs
	 * as one step in Redis, so no other client changes the key between the comparison and the write.
	 */
	private static final RedisScript COMPARE_AND_PUT = RedisScript.of("""
			local current = redis.call('GET', KEYS[1])
			if ARGV[1] == '1' then
				if current ~= ARGV[2] then return 0 end
			elseif current then
				return 0
			end
			local ttl = tonumber(ARGV[4])
			if ttl > 0 then
				redis.call('PSETEX', KEYS[1], ttl, ARGV[3])
				redis.call('ZADD', KEYS[2], ARGV[5], ARGV[6])
			else
				redis.call('SET', KEYS[1], ARGV[3])
			end
			return 1
			""");
	/** Removes the key and its index entry only while the key holds the expected bytes. */
	private static final RedisScript COMPARE_AND_DELETE = RedisScript.of("""
			if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
			redis.call('DEL', KEYS[1])
			redis.call('ZREM', KEYS[2], ARGV[2])
			return 1
			""");

	private final JedisPoolProvider poolProvider;
	private final Provider<Replication> replicationProvider;

	@Override
	public boolean isAvailable() {
		return replicationProvider.get().isEnabled();
	}

	@Override
	public @NotNull CompletableFuture<Optional<byte[]>> get(@NotNull String namespace, @NotNull String key) {
		if (!isAvailable()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		return CompletableFuture.supplyAsync(() -> {
			JedisPool pool = poolProvider.getOptional().orElse(null);
			if (pool == null) {
				return Optional.empty();
			}
			byte[] rawKey = buildKey(namespace, key);
			try (Jedis jedis = pool.getResource()) {
				return Optional.ofNullable(jedis.get(rawKey));
			} catch (Exception ignored) {
				return Optional.empty();
			}
		});
	}

	@Override
	public @NotNull CompletableFuture<Optional<byte[]>> consume(@NotNull String namespace, @NotNull String key) {
		if (!isAvailable()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		return CompletableFuture.supplyAsync(() -> {
			JedisPool pool = poolProvider.getOptional().orElse(null);
			if (pool == null) return Optional.empty();

			byte[] rawKey = buildKey(namespace, key);
			try (Jedis jedis = pool.getResource()) {
				byte[] payload = jedis.getDel(rawKey);
				if (payload != null) jedis.zrem(buildIndexKey(namespace), key);

				return Optional.ofNullable(payload);
			} catch (Exception ignored) {
				return Optional.empty();
			}
		});
	}

	@Override
	public @NotNull CompletableFuture<Void> put(@NotNull String namespace, @NotNull String key, byte[] value, long ttlMs) {
		if (!isAvailable()) return CompletableFuture.completedFuture(null);

		return CompletableFuture.runAsync(() -> {
			JedisPool pool = poolProvider.getOptional().orElse(null);
			if (pool == null) {
				return;
			}
			byte[] rawKey = buildKey(namespace, key);
			try (Jedis jedis = pool.getResource()) {
				if (ttlMs > 0) {
					jedis.psetex(rawKey, ttlMs, value);
					long expiresAt = System.currentTimeMillis() + ttlMs;
					jedis.zadd(buildIndexKey(namespace), expiresAt, key);
				} else {
					jedis.set(rawKey, value);
				}
			} catch (Exception ignored) {
				// ignore failures for cache
			}
		});
	}

	@Override
	public @NotNull CompletableFuture<Boolean> compareAndPut(
			@NotNull String namespace,
			@NotNull String key,
			byte @Nullable [] expected,
			byte @NotNull [] value,
			long ttlMs
	) {
		long expiresAt = System.currentTimeMillis() + ttlMs;
		return evaluate(COMPARE_AND_PUT, namespace, key, List.of(
				bytes(expected != null ? "1" : "0"),
				expected != null ? expected : new byte[0],
				value,
				bytes(Long.toString(ttlMs)),
				bytes(Long.toString(expiresAt)),
				bytes(key)
		));
	}

	@Override
	public @NotNull CompletableFuture<Boolean> compareAndInvalidate(@NotNull String namespace, @NotNull String key, byte @NotNull [] expected) {
		return evaluate(COMPARE_AND_DELETE, namespace, key, List.of(expected, bytes(key)));
	}

	private @NotNull CompletableFuture<Boolean> evaluate(RedisScript script, String namespace, String key, List<byte[]> arguments) {
		if (!isAvailable()) return CompletableFuture.completedFuture(false);

		return CompletableFuture.supplyAsync(() -> {
			JedisPool pool = poolProvider.getOptional().orElse(null);
			if (pool == null) return false;

			List<byte[]> keys = List.of(buildKey(namespace, key), bytes(buildIndexKey(namespace)));
			try (Jedis jedis = pool.getResource()) {
				return Long.valueOf(1L).equals(script.run(jedis, keys, arguments));
			} catch (Exception failure) {
				Logger.debug("Conditional write of %s:%s failed: %s", namespace, key, failure.getMessage());
				return false;
			}
		});
	}

	@Override
	public @NotNull CompletableFuture<Void> invalidate(@NotNull String namespace, @NotNull String key) {
		if (!isAvailable()) return CompletableFuture.completedFuture(null);

		return CompletableFuture.runAsync(() -> {
			JedisPool pool = poolProvider.getOptional().orElse(null);
			if (pool == null) {
				return;
			}
			byte[] rawKey = buildKey(namespace, key);
			try (Jedis jedis = pool.getResource()) {
				jedis.del(rawKey);
				jedis.zrem(buildIndexKey(namespace), key);
			} catch (Exception ignored) {
				// ignore failures for sync
			}
		});
	}

	@Override
	public @NotNull CompletableFuture<ReplicationPage> listKeys(
			@NotNull String namespace,
			int page,
			int pageSize
	) {
		int safePage = Math.max(1, page);
		int safeSize = Math.max(1, pageSize);

		if (!isAvailable()) {
			return CompletableFuture.completedFuture(new ReplicationPage(List.of(), safePage, safeSize, 0));
		}

		return CompletableFuture.supplyAsync(() -> {
			JedisPool pool = poolProvider.getOptional().orElse(null);
			if (pool == null) {
				return new ReplicationPage(List.of(), safePage, safeSize, 0);
			}
			long now = System.currentTimeMillis();
			String indexKey = buildIndexKey(namespace);

			try (Jedis jedis = pool.getResource()) {
				jedis.zremrangeByScore(indexKey, 0, now);
				long total = jedis.zcard(indexKey);

				int fromIndex = Math.min((safePage - 1) * safeSize, (int) total);
				int toIndex = Math.min(fromIndex + safeSize - 1, (int) total - 1);

				if (total == 0 || fromIndex > toIndex) {
					return new ReplicationPage(List.of(), safePage, safeSize, (int) total);
				}

				List<String> members = jedis.zrange(indexKey, fromIndex, toIndex);
				return new ReplicationPage(members, safePage, safeSize, (int) total);
			} catch (Exception ignored) {
				return new ReplicationPage(List.of(), safePage, safeSize, 0);
			}
		});
	}

	@Override
	public @NotNull CompletableFuture<Void> publish(@NotNull String channel, byte[] payload) {
		if (!isAvailable() || channel.isBlank() || payload == null) return CompletableFuture.completedFuture(null);
		return CompletableFuture.runAsync(() -> {
			JedisPool pool = poolProvider.getOptional().orElse(null);
			if (pool == null) return;

			byte[] rawChannel = channel.getBytes(StandardCharsets.UTF_8);
			try (Jedis jedis = pool.getResource()) {
				jedis.publish(rawChannel, payload);
			} catch (Exception ignored) {
				// ignore failures for sync
			}
		});
	}

	@Override
	public void subscribe(@NotNull String channel, java.util.function.@NotNull Consumer<byte[]> handler) {
		if (!isAvailable() || channel.isBlank()) return;

		JedisPool pool = poolProvider.getOptional().orElse(null);
		if (pool == null) return;

		byte[] rawChannel = channel.getBytes(StandardCharsets.UTF_8);
		Thread thread = new Thread(() -> {
			try (Jedis jedis = pool.getResource()) {
				jedis.subscribe(new BinaryJedisPubSub() {
					@Override
					public void onMessage(byte[] channel, byte[] message) {
						handler.accept(message);
					}
				}, rawChannel);
			} catch (Exception ignored) {
				// ignore failures for sync
			}
		}, "identica-redis-sub-" + channel);
		thread.setDaemon(true);
		thread.start();
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private byte[] buildKey(String namespace, String key) {
		StringBuilder builder = new StringBuilder(KV_PREFIX);
		if (namespace != null && !namespace.isBlank())
			builder.append(':').append(namespace);

		if (key != null && !key.isBlank())
			builder.append(':').append(key);

		return builder.toString().getBytes(StandardCharsets.UTF_8);
	}

	private String buildIndexKey(String namespace) {
		StringBuilder builder = new StringBuilder(KV_PREFIX).append(":index");
		if (namespace != null && !namespace.isBlank())
			builder.append(':').append(namespace);

		return builder.toString();
	}

	/**
	 * A Lua script run by its SHA1 digest, so a call sends the digest instead of the script. Redis keeps a script
	 * it ran until it restarts or its script cache is flushed; a call it answers with {@code NOSCRIPT} loads the
	 * script and runs it again.
	 */
	private record RedisScript(byte[] source, byte[] digest) {
		private static @NotNull RedisScript of(@NotNull String source) {
			byte[] bytes = bytes(source);
			try {
				byte[] sha = MessageDigest.getInstance("SHA-1").digest(bytes);
				return new RedisScript(bytes, bytes(HexFormat.of().formatHex(sha)));
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException("SHA-1 is not available", e);
			}
		}

		private Object run(@NotNull Jedis jedis, @NotNull List<byte[]> keys, @NotNull List<byte[]> arguments) {
			try {
				return jedis.evalsha(digest, keys, arguments);
			} catch (JedisNoScriptException missing) {
				jedis.scriptLoad(source);
				return jedis.evalsha(digest, keys, arguments);
			}
		}
	}
}
