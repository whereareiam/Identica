package me.whereareiam.identica.adapter.replication;

import me.whereareiam.identica.adapter.replication.provider.JedisPoolProvider;
import me.whereareiam.identica.model.config.Replication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Redis Replication Adapter")
class RedisReplicationAdapterTest {
	@DisplayName("Fails every call while replication is disabled instead of answering as if Redis held nothing")
	@Test
	void disabledReplicationFailsCalls() {
		Replication replication = new Replication();
		replication.setEnabled(false);
		JedisPoolProvider poolProvider = new JedisPoolProvider(() -> replication, RedisTestFixtures.reloadableRegistry());
		RedisReplicationAdapter adapter = new RedisReplicationAdapter(poolProvider, () -> replication);

		assertFalse(adapter.isAvailable());
		assertFailsWith(IllegalStateException.class, adapter.get("ns", "key"));
		assertFailsWith(IllegalStateException.class, adapter.consume("ns", "key"));
		assertFailsWith(IllegalStateException.class, adapter.put("ns", "key", new byte[] {1}, 5));
		assertFailsWith(IllegalStateException.class, adapter.compareAndPut("ns", "key", null, new byte[] {1}, 5));
		assertFailsWith(IllegalStateException.class, adapter.compareAndInvalidate("ns", "key", new byte[] {1}));
		assertFailsWith(IllegalStateException.class, adapter.invalidate("ns", "key"));
		assertFailsWith(IllegalStateException.class, adapter.listKeys("ns", 1, 10));
		assertFailsWith(IllegalStateException.class, adapter.publish("channel", new byte[] {1}));
	}

	private static void assertFailsWith(Class<? extends Throwable> expected, CompletableFuture<?> call) {
		CompletionException failure = assertThrows(CompletionException.class, call::join);
		assertInstanceOf(expected, failure.getCause());
	}
}
