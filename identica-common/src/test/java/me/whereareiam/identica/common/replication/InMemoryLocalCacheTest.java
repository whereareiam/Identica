package me.whereareiam.identica.common.replication;

import me.whereareiam.identica.common.replication.cache.InMemoryLocalCache;
import me.whereareiam.identica.model.replication.ReplicationPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("In-Memory Local Cache")
class InMemoryLocalCacheTest {
	private InMemoryLocalCache<String> cache;

	@BeforeEach
	void setUp() {
		cache = new InMemoryLocalCache<>();
	}

	@DisplayName("Returns stored values while their TTL is still valid")
	@Test
	void putGetReturnsValueWhenTtlPositive() {
		cache.put("key", "value", 500).join();
		assertEquals(Optional.of("value"), cache.get("key").join());
	}

	@DisplayName("Removing a value via a zero TTL clears the cache entry")
	@Test
	void putWithZeroTtlRemovesEntry() {
		cache.put("key", "value", 500).join();
		cache.put("key", "value", 0).join();
		assertEquals(Optional.empty(), cache.get("key").join());
	}

	@DisplayName("Invalidating a key removes its cached value")
	@Test
	void invalidateRemovesEntry() {
		cache.put("key", "value", 500).join();
		cache.invalidate("key").join();
		assertEquals(Optional.empty(), cache.get("key").join());
	}

	@DisplayName("Consuming a key returns the value and removes it from the cache")
	@Test
	void consumeReturnsAndRemovesEntry() {
		cache.put("key", "value", 500).join();
		assertEquals(Optional.of("value"), cache.consume("key").join());
		assertEquals(Optional.empty(), cache.get("key").join());
	}

	@DisplayName("Expired entries are evicted on read")
	@Test
	void expiredEntryIsRemoved() {
		cache.put("key", "value", 30).join();
		sleep(50);
		assertEquals(Optional.empty(), cache.get("key").join());
	}

	@DisplayName("Listing keys sorts entries and applies pagination")
	@Test
	void listKeysSortsAndPaginates() {
		cache.put("b", "value", 500).join();
		cache.put("a", "value", 500).join();
		cache.put("c", "value", 500).join();

		ReplicationPage page1 = cache.listKeys(1, 2).join();
		ReplicationPage page2 = cache.listKeys(2, 2).join();

		assertEquals(List.of("a", "b"), page1.getEntries());
		assertEquals(3, page1.getTotal());
		assertEquals(List.of("c"), page2.getEntries());
		assertEquals(3, page2.getTotal());
	}

	@DisplayName("Listing keys also clears expired entries")
	@Test
	void listKeysCleansExpiredEntries() {
		cache.put("alive", "value", 500).join();
		cache.put("expired", "value", 20).join();
		sleep(40);

		ReplicationPage page = cache.listKeys(1, 10).join();
		assertTrue(page.getEntries().contains("alive"));
		assertEquals(1, page.getTotal());
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@DisplayName("Removes a value that matches the condition")
	@Test
	void consumeIfRemovesMatchingValue() {
		cache.put("key", "value", 500).join();

		assertEquals(Optional.of("value"), cache.consumeIf("key", "value"::equals).join());
		assertEquals(Optional.empty(), cache.get("key").join());
	}

	@DisplayName("Keeps a value that does not match the condition")
	@Test
	void consumeIfKeepsOtherValue() {
		cache.put("key", "value", 500).join();

		assertEquals(Optional.empty(), cache.consumeIf("key", "other"::equals).join());
		assertEquals(Optional.of("value"), cache.get("key").join());
		assertEquals(Optional.empty(), cache.consumeIf("missing", value -> true).join());
	}
}
