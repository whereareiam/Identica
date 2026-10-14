package me.whereareiam.identica.adapter.replication;

import com.redis.testcontainers.RedisContainer;
import me.whereareiam.identica.adapter.replication.provider.JedisPoolProvider;
import me.whereareiam.identica.model.replication.ReplicationPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Redis Replication Adapter Integration")
@Tag("integration")
class RedisReplicationAdapterIntegrationTest {
	@Container
	private static final RedisContainer REDIS = new RedisContainer(
			DockerImageName.parse("redis:7.2-alpine")
	);

	private JedisPoolProvider poolProvider;
	private RedisReplicationAdapter adapter;

	@BeforeEach
	void setUp() {
		var replication = RedisTestFixtures.enabledReplication(REDIS.getHost(), REDIS.getFirstMappedPort());
		poolProvider = new JedisPoolProvider(() -> replication, RedisTestFixtures.reloadableRegistry());
		adapter = new RedisReplicationAdapter(poolProvider, () -> replication);
	}

	@AfterEach
	void tearDown() {
		poolProvider.close();
	}

	@DisplayName("Stores a payload in Redis and reads it back")
	@Test
	void putAndGetRoundTrip() {
		adapter.put("ns", "key", "value".getBytes(StandardCharsets.UTF_8), 500).join();

		Optional<byte[]> payload = adapter.get("ns", "key").join();
		assertTrue(payload.isPresent());
		assertEquals("value", new String(payload.get(), StandardCharsets.UTF_8));
	}

	@DisplayName("Lists stored keys from the Redis index")
	@Test
	void listKeysIncludesStoredKey() {
		adapter.put("ns", "key", "value".getBytes(StandardCharsets.UTF_8), 500).join();

		ReplicationPage page = adapter.listKeys("ns", 1, 10).join();
		assertTrue(page.getEntries().contains("key"));
		assertEquals(1, page.getTotal());
	}

	@DisplayName("Removes expired entries from both the cache and the key index")
	@Test
	void expiredEntriesAreRemovedFromIndex() {
		adapter.put("ns", "key", "value".getBytes(StandardCharsets.UTF_8), 120).join();
		sleep(180);

		assertEquals(Optional.empty(), adapter.get("ns", "key").join());
		ReplicationPage page = adapter.listKeys("ns", 1, 10).join();
		assertEquals(0, page.getTotal());
	}

	@DisplayName("Consumes an entry and removes it from Redis")
	@Test
	void consumeRemovesEntryAndIndex() {
		adapter.put("ns", "key", "value".getBytes(StandardCharsets.UTF_8), 500).join();
		Optional<byte[]> payload = adapter.consume("ns", "key").join();

		assertTrue(payload.isPresent());
		assertEquals(Optional.empty(), adapter.get("ns", "key").join());
		ReplicationPage page = adapter.listKeys("ns", 1, 10).join();
		assertEquals(0, page.getTotal());
	}

	@DisplayName("Invalidates an entry and removes it from the key index")
	@Test
	void invalidateRemovesEntryAndIndex() {
		adapter.put("ns", "key", "value".getBytes(StandardCharsets.UTF_8), 500).join();
		adapter.invalidate("ns", "key").join();

		assertEquals(Optional.empty(), adapter.get("ns", "key").join());
		ReplicationPage page = adapter.listKeys("ns", 1, 10).join();
		assertEquals(0, page.getTotal());
	}

	@DisplayName("Delivers published payloads to subscribers")
	@Test
	void publishAndSubscribeDeliversPayload() throws Exception {
		String channel = "channel-test";
		CountDownLatch latch = new CountDownLatch(1);
		CompletableFuture<String> received = new CompletableFuture<>();

		adapter.subscribe(channel, payload -> {
			received.complete(new String(payload, StandardCharsets.UTF_8));
			latch.countDown();
		});

		sleep(80);
		adapter.publish(channel, "hello".getBytes(StandardCharsets.UTF_8)).join();

		assertTrue(latch.await(1, TimeUnit.SECONDS));
		assertEquals("hello", received.get(1, TimeUnit.SECONDS));
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
