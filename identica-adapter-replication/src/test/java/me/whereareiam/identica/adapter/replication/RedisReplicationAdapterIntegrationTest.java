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
import redis.clients.jedis.Jedis;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		// The container is shared by the tests; each starts from an empty Redis.
		try (Jedis jedis = new Jedis(REDIS.getHost(), REDIS.getFirstMappedPort())) {
			jedis.flushAll();
		}
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

	@DisplayName("A conditional write stores only over the expected value or, when none is expected, over nothing")
	@Test
	void compareAndPutChecksTheStoredValue() {
		assertTrue(adapter.compareAndPut("ns", "key", null, bytes("v1"), 5_000).join());
		assertFalse(adapter.compareAndPut("ns", "key", null, bytes("v2"), 5_000).join());
		assertFalse(adapter.compareAndPut("ns", "key", bytes("other"), bytes("v2"), 5_000).join());
		assertTrue(adapter.compareAndPut("ns", "key", bytes("v1"), bytes("v2"), 5_000).join());

		assertEquals("v2", new String(adapter.get("ns", "key").join().orElseThrow(), StandardCharsets.UTF_8));
		assertEquals(1, adapter.listKeys("ns", 1, 10).join().getTotal());
	}

	@DisplayName("A conditional write keeps the lifetime and the key index of a plain write")
	@Test
	void compareAndPutExpiresLikeAPut() {
		assertTrue(adapter.compareAndPut("ns", "key", null, bytes("v1"), 120).join());
		assertTrue(adapter.listKeys("ns", 1, 10).join().getEntries().contains("key"));
		sleep(180);

		assertEquals(Optional.empty(), adapter.get("ns", "key").join());
		assertEquals(0, adapter.listKeys("ns", 1, 10).join().getTotal());
		assertTrue(adapter.compareAndPut("ns", "key", null, bytes("v2"), 5_000).join());
	}

	@DisplayName("A conditional removal removes the entry and its index entry only while it holds the expected value")
	@Test
	void compareAndInvalidateChecksTheStoredValue() {
		adapter.put("ns", "key", bytes("v1"), 5_000).join();

		assertFalse(adapter.compareAndInvalidate("ns", "key", bytes("other")).join());
		assertTrue(adapter.get("ns", "key").join().isPresent());
		assertTrue(adapter.compareAndInvalidate("ns", "key", bytes("v1")).join());

		assertEquals(Optional.empty(), adapter.get("ns", "key").join());
		assertEquals(0, adapter.listKeys("ns", 1, 10).join().getTotal());
		assertFalse(adapter.compareAndInvalidate("ns", "key", bytes("v1")).join());
	}

	@DisplayName("Runs conditional writes by the script's digest and loads the script once when Redis does not know it")
	@Test
	void conditionalWritesRunByDigest() {
		try (Jedis jedis = new Jedis(REDIS.getHost(), REDIS.getFirstMappedPort())) {
			jedis.scriptFlush();
			jedis.configResetStat();

			assertTrue(adapter.compareAndPut("ns", "key", null, bytes("v1"), 5_000).join());
			assertTrue(adapter.compareAndPut("ns", "key", bytes("v1"), bytes("v2"), 5_000).join());
			assertTrue(adapter.compareAndInvalidate("ns", "key", bytes("v2")).join());

			Map<String, String> calls = commandCalls(jedis);
			assertFalse(calls.containsKey("eval"), "no conditional write sends its script");
			// Each script is unknown on its first call, is loaded once and runs by its digest from then on.
			assertEquals("2", calls.get("script|load"));
			assertEquals("5", calls.get("evalsha"));
		}
	}

	@DisplayName("A conditional write still succeeds after Redis forgot its scripts")
	@Test
	void conditionalWriteAfterScriptFlush() {
		assertTrue(adapter.compareAndPut("ns", "key", null, bytes("v1"), 5_000).join());
		try (Jedis jedis = new Jedis(REDIS.getHost(), REDIS.getFirstMappedPort())) {
			jedis.scriptFlush();
		}

		assertTrue(adapter.compareAndPut("ns", "key", bytes("v1"), bytes("v2"), 5_000).join());
		assertEquals("v2", new String(adapter.get("ns", "key").join().orElseThrow(), StandardCharsets.UTF_8));
	}

	@DisplayName("Of many clients writing over the same value at once, exactly one succeeds")
	@Test
	void concurrentWritersOverOneValueOnlyOneWins() throws Exception {
		adapter.put("ns", "key", bytes("start"), 5_000).join();
		int writers = 32;

		List<Boolean> results = concurrently(writers, writer ->
				adapter.compareAndPut("ns", "key", bytes("start"), bytes("writer-" + writer), 5_000).join());

		assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
	}

	@DisplayName("Of many clients creating the same missing entry at once, exactly one succeeds")
	@Test
	void concurrentCreatorsOnlyOneWins() throws Exception {
		List<Boolean> results = concurrently(32, writer ->
				adapter.compareAndPut("ns", "fresh", null, bytes("writer-" + writer), 5_000).join());

		assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
	}

	@DisplayName("Clients that read, change and write back with a conditional write lose no change")
	@Test
	void readChangeWriteLosesNoUpdate() throws Exception {
		adapter.put("ns", "counter", bytes("0"), 30_000).join();
		int clients = 8;
		int increments = 25;

		concurrently(clients, client -> {
			for (int increment = 0; increment < increments; increment++) {
				while (true) {
					byte[] read = adapter.get("ns", "counter").join().orElseThrow();
					byte[] next = bytes(Integer.toString(Integer.parseInt(new String(read, StandardCharsets.UTF_8)) + 1));
					if (adapter.compareAndPut("ns", "counter", read, next, 30_000).join()) break;
				}
			}
			return true;
		});

		assertEquals(Integer.toString(clients * increments),
				new String(adapter.get("ns", "counter").join().orElseThrow(), StandardCharsets.UTF_8));
	}

	/**
	 * Runs one task per client on its own thread, all released at the same moment.
	 */
	private static <T> List<T> concurrently(int clients, IntFunction<T> task) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(clients);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (int client = 0; client < clients; client++) {
				int id = client;
				futures.add(executor.submit(() -> {
					start.await();
					return task.apply(id);
				}));
			}
			start.countDown();

			List<T> results = new ArrayList<>();
			for (Future<T> future : futures)
				results.add(future.get(30, TimeUnit.SECONDS));
			return results;
		} finally {
			executor.shutdownNow();
		}
	}

	/**
	 * Reads how often Redis ran each command since its statistics were last reset, by lowercase command name.
	 */
	private static Map<String, String> commandCalls(Jedis jedis) {
		Map<String, String> calls = new HashMap<>();
		for (String line : jedis.info("commandstats").split("\r?\n")) {
			if (!line.startsWith("cmdstat_")) continue;

			String command = line.substring("cmdstat_".length(), line.indexOf(':'));
			String count = line.substring(line.indexOf("calls=") + "calls=".length(), line.indexOf(',', line.indexOf("calls=")));
			calls.put(command, count);
		}
		return calls;
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
