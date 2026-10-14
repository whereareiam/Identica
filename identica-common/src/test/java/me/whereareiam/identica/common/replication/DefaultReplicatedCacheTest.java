package me.whereareiam.identica.common.replication;

import me.whereareiam.identica.common.replication.cache.DefaultReplicatedCache;
import me.whereareiam.identica.common.replication.cache.InMemoryLocalCache;
import me.whereareiam.identica.model.replication.ReplicationEnvelope;
import me.whereareiam.identica.model.replication.ReplicationPage;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.replication.codec.SnapshotCodec;
import me.whereareiam.identica.replication.cache.Versioned;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Default Replicated Cache")
class DefaultReplicatedCacheTest {
	@DisplayName("Returns a local hit without contacting the adapter")
	@Test
	void getReturnsLocalHitWithoutAdapterCall() {
		InMemoryLocalCache<String> local = new InMemoryLocalCache<>();
		local.put("key", "value", 500).join();

		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		DefaultReplicatedCache<String, String> cache = new DefaultReplicatedCache<>(
				"namespace",
				local,
				adapter,
				ReplicationTestFixtures.stringType(),
				ReplicationTestFixtures.stringCodecFactory(),
				0L
		);

		assertEquals(Optional.of("value"), cache.get("key").join());
		assertEquals(0, adapter.getCalls);
	}

	@DisplayName("Returns empty from getFresh when the key is null")
	@Test
	void getFreshReturnsEmptyOnNullKey() {
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		DefaultReplicatedCache<String, String> cache = new DefaultReplicatedCache<>(
				"namespace",
				new InMemoryLocalCache<>(),
				adapter,
				ReplicationTestFixtures.stringType(),
				ReplicationTestFixtures.stringCodecFactory(),
				0L
		);

		assertEquals(Optional.empty(), cache.getFresh(null).join());
	}

	@DisplayName("Falls back to the local cache when the adapter is unavailable")
	@Test
	void getFreshFallsBackToLocalWhenAdapterUnavailable() {
		InMemoryLocalCache<String> local = new InMemoryLocalCache<>();
		local.put("key", "value", 500).join();

		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		adapter.available = false;

		DefaultReplicatedCache<String, String> cache = new DefaultReplicatedCache<>(
				"namespace",
				local,
				adapter,
				ReplicationTestFixtures.stringType(),
				ReplicationTestFixtures.stringCodecFactory(),
				0L
		);

		assertEquals(Optional.of("value"), cache.getFresh("key").join());
		assertEquals(0, adapter.getCalls);
	}

	@DisplayName("Populates the local cache from a fresh remote value")
	@Test
	void remoteHitPopulatesLocalCache() {
		InMemoryLocalCache<String> local = new InMemoryLocalCache<>();
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		ReplicationType<String, String> type = ReplicationTestFixtures.stringType();

		long expiresAt = System.currentTimeMillis() + 500;
		byte[] envelope = ReplicationEnvelope.encode(type.version(), expiresAt, SnapshotCodec.string().encode("remote"));
		adapter.nextGet = Optional.of(envelope);

		DefaultReplicatedCache<String, String> cache = new DefaultReplicatedCache<>(
				"namespace",
				local,
				adapter,
				type,
				ReplicationTestFixtures.stringCodecFactory(),
				0L
		);

		assertEquals(Optional.of("remote"), cache.getFresh("key").join());
		assertEquals(Optional.of("remote"), cache.get("key").join());
		assertEquals(1, adapter.getCalls);
	}

	@DisplayName("Invalidates local state when the remote envelope is expired")
	@Test
	void remoteHitExpiredEnvelopeInvalidatesLocal() {
		InMemoryLocalCache<String> local = new InMemoryLocalCache<>();
		local.put("key", "local", 500).join();

		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		ReplicationType<String, String> type = ReplicationTestFixtures.stringType();

		byte[] envelope = ReplicationEnvelope.encode(type.version(), System.currentTimeMillis() - 5, SnapshotCodec.string().encode("remote"));
		adapter.nextGet = Optional.of(envelope);

		DefaultReplicatedCache<String, String> cache = new DefaultReplicatedCache<>(
				"namespace",
				local,
				adapter,
				type,
				ReplicationTestFixtures.stringCodecFactory(),
				0L
		);

		assertEquals(Optional.empty(), cache.getFresh("key").join());
		assertEquals(Optional.empty(), local.get("key").join());
		assertTrue(adapter.invalidateCalls > 0);
	}

	@DisplayName("Invalidates local state when the remote envelope version does not match")
	@Test
	void remoteHitVersionMismatchInvalidatesLocal() {
		InMemoryLocalCache<String> local = new InMemoryLocalCache<>();
		local.put("key", "local", 500).join();

		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		ReplicationType<String, String> type = ReplicationTestFixtures.stringType();

		byte[] envelope = ReplicationEnvelope.encode(type.version() + 1, System.currentTimeMillis() + 200, SnapshotCodec.string().encode("remote"));
		adapter.nextGet = Optional.of(envelope);

		DefaultReplicatedCache<String, String> cache = new DefaultReplicatedCache<>(
				"namespace",
				local,
				adapter,
				type,
				ReplicationTestFixtures.stringCodecFactory(),
				0L
		);

		assertEquals(Optional.empty(), cache.getFresh("key").join());
		assertEquals(Optional.empty(), local.get("key").join());
		assertTrue(adapter.invalidateCalls > 0);
	}

	@DisplayName("Invalidates local state when the remote payload cannot be decoded")
	@Test
	void remoteHitDecodeFailureInvalidatesLocal() {
		InMemoryLocalCache<String> local = new InMemoryLocalCache<>();
		local.put("key", "local", 500).join();

		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		SnapshotCodec<String> throwingCodec = new SnapshotCodec<>() {
			@Override
			public byte @NotNull [] encode(String snapshot) {
				return SnapshotCodec.string().encode(snapshot);
			}

			@Override
			public String decode(byte[] payload) {
				throw new RuntimeException("boom");
			}
		};

		ReplicationType<String, String> type = ReplicationType.identity(String.class).withCodec(throwingCodec);
		byte[] envelope = ReplicationEnvelope.encode(type.version(), System.currentTimeMillis() + 500, throwingCodec.encode("remote"));
		adapter.nextGet = Optional.of(envelope);

		DefaultReplicatedCache<String, String> cache = new DefaultReplicatedCache<>(
				"namespace",
				local,
				adapter,
				type,
				ReplicationTestFixtures.stringCodecFactory(),
				0L
		);

		assertEquals(Optional.empty(), cache.getFresh("key").join());
		assertEquals(Optional.empty(), local.get("key").join());
		assertTrue(adapter.invalidateCalls > 0);
	}

	@DisplayName("Stores values both locally and remotely when a TTL is provided")
	@Test
	void putWithTtlStoresLocallyAndRemotely() {
		InMemoryLocalCache<String> local = new InMemoryLocalCache<>();
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		ReplicationType<String, String> type = ReplicationTestFixtures.stringType();

		DefaultReplicatedCache<String, String> cache = new DefaultReplicatedCache<>(
				"namespace",
				local,
				adapter,
				type,
				ReplicationTestFixtures.stringCodecFactory(),
				0L
		);

		cache.put("key", "value", 400).join();

		assertEquals(Optional.of("value"), local.get("key").join());
		assertEquals(1, adapter.putCalls);
		assertEquals("namespace", adapter.lastNamespace);
		assertEquals("key", adapter.lastKey);

		ReplicationEnvelope envelope = ReplicationEnvelope.decode(adapter.lastValue);
		assertEquals(type.version(), envelope.getVersion());
		assertEquals("value", SnapshotCodec.string().decode(envelope.getPayload()));
	}

	@DisplayName("Treats a zero TTL as a remote invalidation")
	@Test
	void putWithZeroTtlInvalidatesRemote() {
		InMemoryLocalCache<String> local = new InMemoryLocalCache<>();
		local.put("key", "value", 500).join();

		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		ReplicationType<String, String> type = ReplicationTestFixtures.stringType();

		DefaultReplicatedCache<String, String> cache = new DefaultReplicatedCache<>(
				"namespace",
				local,
				adapter,
				type,
				ReplicationTestFixtures.stringCodecFactory(),
				0L
		);

		cache.put("key", "value", 0).join();

		assertEquals(Optional.empty(), local.get("key").join());
		assertEquals(1, adapter.invalidateCalls);
		assertEquals("namespace", adapter.lastNamespace);
		assertEquals("key", adapter.lastKey);
	}

	@DisplayName("Consumes a remote value and clears the local copy")
	@Test
	void consumeReturnsMappedValueAndInvalidatesLocal() {
		InMemoryLocalCache<String> local = new InMemoryLocalCache<>();
		local.put("key", "local", 500).join();

		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		ReplicationType<String, String> type = ReplicationTestFixtures.stringType();

		byte[] envelope = ReplicationEnvelope.encode(type.version(), System.currentTimeMillis() + 500, SnapshotCodec.string().encode("remote"));
		adapter.nextConsume = Optional.of(envelope);

		DefaultReplicatedCache<String, String> cache = new DefaultReplicatedCache<>(
				"namespace",
				local,
				adapter,
				type,
				ReplicationTestFixtures.stringCodecFactory(),
				0L
		);

		assertEquals(Optional.of("remote"), cache.consume("key").join());
		assertEquals(Optional.empty(), local.get("key").join());
		assertEquals(1, adapter.consumeCalls);
	}

	@DisplayName("Delegates listKeys to the adapter when replication is available")
	@Test
	void listKeysDelegatesToAdapterWhenAvailable() {
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		adapter.nextListKeys = new ReplicationPage(List.of("a"), 1, 10, 1);

		DefaultReplicatedCache<String, String> cache = new DefaultReplicatedCache<>(
				"namespace",
				new InMemoryLocalCache<>(),
				adapter,
				ReplicationTestFixtures.stringType(),
				ReplicationTestFixtures.stringCodecFactory(),
				0L
		);

		ReplicationPage page = cache.listKeys(1, 10).join();
		assertEquals(List.of("a"), page.getEntries());
		assertEquals(1, adapter.listKeysCalls);
	}

	@DisplayName("Falls back to local listKeys results when replication is unavailable")
	@Test
	void listKeysFallsBackToLocalWhenUnavailable() {
		InMemoryLocalCache<String> local = new InMemoryLocalCache<>();
		local.put("a", "value", 500).join();

		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		adapter.available = false;

		DefaultReplicatedCache<String, String> cache = new DefaultReplicatedCache<>(
				"namespace",
				local,
				adapter,
				ReplicationTestFixtures.stringType(),
				ReplicationTestFixtures.stringCodecFactory(),
				0L
		);

		ReplicationPage page = cache.listKeys(1, 10).join();
		assertEquals(List.of("a"), page.getEntries());
		assertEquals(0, adapter.listKeysCalls);
	}

	@DisplayName("A conditional write succeeds for the revision it read and fails for a stale one, in the shared store")
	@Test
	void conditionalWriteRefusesAStaleRevisionRemotely() {
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		adapter.storing = true;
		DefaultReplicatedCache<String, String> first = storingCache(adapter);
		DefaultReplicatedCache<String, String> second = storingCache(adapter);
		first.put("key", "v1", 500).join();

		Versioned<String> readByFirst = first.read("key").join().orElseThrow();
		Versioned<String> readBySecond = second.read("key").join().orElseThrow();

		assertTrue(second.write("key", readBySecond, "v2", 500).join());
		assertFalse(first.write("key", readByFirst, "v3", 500).join());
		assertEquals(Optional.of("v2"), first.getFresh("key").join());
		assertFalse(first.remove("key", readByFirst).join());
		assertTrue(first.remove("key", first.read("key").join().orElseThrow()).join());
		assertEquals(Optional.empty(), second.getFresh("key").join());
	}

	@DisplayName("A write that expects no entry succeeds once, in the shared store and locally")
	@Test
	void writeExpectingNoEntrySucceedsOnce() {
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		adapter.storing = true;
		DefaultReplicatedCache<String, String> remote = storingCache(adapter);
		DefaultReplicatedCache<String, String> local = localCache();

		for (DefaultReplicatedCache<String, String> cache : List.of(remote, local)) {
			assertTrue(cache.write("key", null, "first", 500).join());
			assertFalse(cache.write("key", null, "second", 500).join());
			assertEquals("first", cache.read("key").join().orElseThrow().getValue());
		}
	}

	@DisplayName("A conditional read removes an entry it cannot read back, so a writer expecting no entry can create one")
	@Test
	void conditionalReadDiscardsAnUnreadableEntry() {
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		adapter.storing = true;
		DefaultReplicatedCache<String, String> cache = storingCache(adapter);
		adapter.put("namespace", "key", new byte[]{1, 2, 3}, 500).join();

		assertTrue(cache.read("key").join().isEmpty());
		assertTrue(cache.write("key", null, "fresh", 500).join());
		assertEquals("fresh", cache.read("key").join().orElseThrow().getValue());
	}

	@DisplayName("Without the shared store, a conditional write compares and replaces the local entry atomically")
	@Test
	void conditionalWriteRefusesAStaleRevisionLocally() {
		DefaultReplicatedCache<String, String> cache = localCache();
		cache.put("key", "v1", 500).join();
		Versioned<String> stale = cache.read("key").join().orElseThrow();
		assertTrue(cache.write("key", cache.read("key").join().orElseThrow(), "v2", 500).join());

		assertFalse(cache.write("key", stale, "v3", 500).join());
		assertFalse(cache.remove("key", stale).join());
		assertEquals(Optional.of("v2"), cache.get("key").join());
	}

	@DisplayName("Of many local writers that read the same entry, exactly one writes it")
	@Test
	void concurrentLocalWritersOnlyOneWins() throws InterruptedException {
		DefaultReplicatedCache<String, String> cache = localCache();
		cache.put("key", "start", 5_000).join();
		Versioned<String> read = cache.read("key").join().orElseThrow();
		int writers = 16;
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger won = new AtomicInteger();
		List<Thread> threads = new ArrayList<>();
		for (int writer = 0; writer < writers; writer++) {
			String value = "writer-" + writer;
			Thread thread = new Thread(() -> {
				try {
					start.await();
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				}
				if (cache.write("key", read, value, 5_000).join()) won.incrementAndGet();
			});
			thread.start();
			threads.add(thread);
		}

		start.countDown();
		for (Thread thread : threads)
			thread.join();

		assertEquals(1, won.get());
	}

	private static DefaultReplicatedCache<String, String> storingCache(ReplicationTestFixtures.TestReplicationAdapter adapter) {
		return new DefaultReplicatedCache<>(
				"namespace",
				new InMemoryLocalCache<>(),
				adapter,
				ReplicationTestFixtures.stringType(),
				ReplicationTestFixtures.stringCodecFactory(),
				0L
		);
	}

	private static DefaultReplicatedCache<String, String> localCache() {
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		adapter.available = false;
		return storingCache(adapter);
	}
}
