package me.whereareiam.identica.adapter.replication;

import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.adapter.replication.provider.JedisPoolProvider;
import me.whereareiam.identica.logging.Logger;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * One subscription to a Redis channel that stays subscribed. It listens on its own daemon thread; when the
 * connection drops it subscribes again after a pause that doubles from {@link #MIN_RETRY_DELAY_MS} up to
 * {@link #MAX_RETRY_DELAY_MS}. A handler that throws is logged and the subscription goes on with the next message.
 */
@RequiredArgsConstructor
final class RedisSubscription {
	private static final long MIN_RETRY_DELAY_MS = 500;
	private static final long MAX_RETRY_DELAY_MS = 30_000;
	private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(2);

	private final @NotNull JedisPoolProvider poolProvider;
	private final @NotNull String channel;
	private final @NotNull Consumer<byte[]> handler;

	private volatile boolean closed;
	private volatile BinaryJedisPubSub listener;
	private volatile Thread thread;
	/** Touched only by the listening thread. */
	private long retryDelayMs = MIN_RETRY_DELAY_MS;

	void start() {
		thread = Thread.ofPlatform()
				.name("identica-redis-sub-" + channel)
				.daemon()
				.start(this::listen);
	}

	/**
	 * Unsubscribes and waits briefly for the listening thread to end, so it does not use the pool after it closes.
	 */
	void close() {
		closed = true;
		unsubscribe();

		Thread current = thread;
		if (current == null) return;

		current.interrupt();
		try {
			current.join(CLOSE_TIMEOUT);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private void listen() {
		while (!closed) {
			try (Jedis jedis = poolProvider.get().getResource()) {
				subscribe(jedis);
			} catch (RuntimeException failure) {
				if (closed) return;

				Logger.warn("Lost the Redis subscription to %s, subscribing again in %d ms: %s",
						channel, retryDelayMs, failure.getMessage());
			}
			if (closed || !pause(retryDelayMs)) return;

			retryDelayMs = Math.min(retryDelayMs * 2, MAX_RETRY_DELAY_MS);
		}
	}

	private void subscribe(@NotNull Jedis jedis) {
		BinaryJedisPubSub current = new BinaryJedisPubSub() {
			@Override
			public void onSubscribe(byte[] channel, int subscribedChannels) {
				retryDelayMs = MIN_RETRY_DELAY_MS;
				// A close that came before the subscription was confirmed found nothing to end.
				if (closed) unsubscribe();
			}

			@Override
			public void onMessage(byte[] channel, byte[] message) {
				deliver(message);
			}
		};
		listener = current;
		if (closed) return;

		jedis.subscribe(current, channel.getBytes(StandardCharsets.UTF_8));
	}

	private void deliver(byte[] message) {
		try {
			handler.accept(message);
		} catch (RuntimeException failure) {
			Logger.warn(failure, "A handler of the Redis channel %s failed", channel);
		}
	}

	private void unsubscribe() {
		BinaryJedisPubSub current = listener;
		if (current == null || !current.isSubscribed()) return;

		try {
			current.unsubscribe();
		} catch (JedisException failure) {
			// The connection is already gone, which ends the subscription as well.
			Logger.debug("Unsubscribing from %s found the connection closed: %s", channel, failure.getMessage());
		}
	}

	private static boolean pause(long delayMs) {
		try {
			Thread.sleep(delayMs);
			return true;
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
