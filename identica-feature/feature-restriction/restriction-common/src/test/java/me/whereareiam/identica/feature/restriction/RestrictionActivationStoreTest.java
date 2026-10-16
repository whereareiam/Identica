package me.whereareiam.identica.feature.restriction;

import me.whereareiam.identica.feature.restriction.config.RestrictionSettings;
import me.whereareiam.identica.feature.restriction.type.RestrictionType;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.cache.base.ReplicationCacheBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Restriction Activation Store")
class RestrictionActivationStoreTest {
	private static final RestrictionType JOIN = RestrictionType.of("join");

	@SuppressWarnings("unchecked")
	private final ReplicatedCache<Boolean> cache = mock(ReplicatedCache.class);
	private final RestrictionActivationStore store = new RestrictionActivationStore(replication(), RestrictionActivationStoreTest::settings);

	@DisplayName("Reads a restriction from the shared store, so a restriction another proxy lifted is lifted here too")
	@Test
	void readsTheSharedStore() {
		when(cache.getFresh("join:credential")).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

		assertFalse(store.isActive(JOIN, "Credential"));
		verify(cache, never()).get(anyString());
	}

	@DisplayName("Reports a restriction the shared store holds")
	@Test
	void reportsAnActiveRestriction() {
		when(cache.getFresh("join:credential")).thenReturn(CompletableFuture.completedFuture(Optional.of(true)));

		assertTrue(store.isActive(JOIN, "credential"));
	}

	@DisplayName("Keeps a restriction for toggleTtl and removes it from the shared store when lifted")
	@Test
	void enablesAndLiftsThroughTheSharedStore() {
		when(cache.put(anyString(), any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(CompletableFuture.completedFuture(null));
		when(cache.invalidate(anyString())).thenReturn(CompletableFuture.completedFuture(null));

		store.enable(JOIN, "credential");
		store.disable(JOIN, "credential");

		verify(cache).put("join:credential", true, Duration.ofDays(1).toMillis());
		verify(cache).invalidate("join:credential");
	}

	private ReplicationSystem replication() {
		ReplicationSystem system = mock(ReplicationSystem.class);
		ReplicationCacheBuilder builder = mock(ReplicationCacheBuilder.class);
		when(system.cache("restrictions")).thenReturn(builder);
		when(builder.replicated(any())).thenReturn((ReplicatedCache) cache);
		return system;
	}

	private static RestrictionSettings settings() {
		RestrictionSettings.Replication replication = new RestrictionSettings.Replication();
		replication.setToggleNamespace("restrictions");
		RestrictionSettings settings = new RestrictionSettings();
		settings.setToggleTtl(Duration.ofDays(1));
		settings.setReplication(replication);
		return settings;
	}
}
