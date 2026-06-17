package me.whereareiam.identica.feature.verification.enrollment;

import me.whereareiam.identica.Registry;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.model.enrollment.PendingVerificationEnrollment;
import me.whereareiam.identica.model.replication.ReplicationPage;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.replication.ReplicationChannel;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.LocalCache;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.cache.base.ReplicationCacheBuilder;
import me.whereareiam.identica.replication.codec.SnapshotCodecFactory;
import me.whereareiam.identica.replication.store.participant.ConnectionDisconnectedParticipant;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Verification Enrollment Store")
class VerificationEnrollmentStoreTest {
	@DisplayName("Disconnect boundary clears pending enrollment by account unique id")
	@Test
	void disconnectBoundaryClearsPendingEnrollment() {
		VerificationEnrollmentStore store = new VerificationEnrollmentStore(
				new TestReplicationSystem(),
				this::settings,
				noopRegistry()
		);
		UUID uniqueId = UUID.randomUUID();
		store.put(uniqueId, PendingVerificationEnrollment.builder()
				.enrollmentId("enrollment")
				.uniqueId(uniqueId)
				.username("PlayerOne")
				.methodId("totp")
				.stateType("state")
				.statePayload("payload")
				.createdAt(System.currentTimeMillis())
				.expiresAt(System.currentTimeMillis() + settings().enrollmentTtlMillis())
				.build());

		assertEquals(Optional.of(uniqueId), store.peek(uniqueId).map(PendingVerificationEnrollment::getUniqueId));

		store.onConnectionDisconnected(new ConnectionDisconnectedEvent(UUID.randomUUID(), uniqueId, null));

		assertTrue(store.peek(uniqueId).isEmpty());
	}

	private @NotNull VerificationSettings settings() {
		VerificationSettings settings = new VerificationSettings();
		settings.setEnrollmentTtl(Duration.ofMinutes(5));
		settings.setChallengeTtl(Duration.ofMinutes(5));
		return settings;
	}

	private Registry<ConnectionDisconnectedParticipant> noopRegistry() {
		return new Registry<>() {
			@Override
			public void register(ConnectionDisconnectedParticipant value) {
			}

			@Override
			public void unregister(ConnectionDisconnectedParticipant value) {
			}

			@Override
			public Set<ConnectionDisconnectedParticipant> values() {
				return Set.of();
			}
		};
	}

	private static final class TestReplicationSystem implements ReplicationSystem {
		private final HashMap<String, LocalCache<?>> caches = new HashMap<>();
		private SnapshotCodecFactory codecFactory = org.mockito.Mockito.mock(SnapshotCodecFactory.class);

		@Override
		public @NotNull ReplicationCacheBuilder cache(@NotNull String name) {
			return new ReplicationCacheBuilder() {
				private long defaultTtlMs;

				@Override
				public @NotNull ReplicationCacheBuilder defaultTtl(long ttlMs) {
					defaultTtlMs = Math.max(0L, ttlMs);
					return this;
				}

				@Override
				@SuppressWarnings("unchecked")
				public @NotNull <T> LocalCache<T> local() {
					return (LocalCache<T>) caches.computeIfAbsent(name, ignored -> new TestLocalCache<>(defaultTtlMs));
				}

				@Override
				public @NotNull <T, S> ReplicatedCache<T> replicated(@NotNull ReplicationType<T, S> type) {
					throw new UnsupportedOperationException("replicated caches are not needed in this test");
				}
			};
		}

		@Override
		public @NotNull <T, S> ReplicationChannel<T> channel(@NotNull String name, @NotNull ReplicationType<T, S> type) {
			throw new UnsupportedOperationException("channels are not needed in this test");
		}

		@Override
		public void setDefaultCodecFactory(@NotNull SnapshotCodecFactory factory) {
			codecFactory = factory;
		}

		@Override
		public @NotNull SnapshotCodecFactory getDefaultCodecFactory() {
			return codecFactory;
		}
	}

	private static final class TestLocalCache<T> implements LocalCache<T> {
		private final HashMap<String, T> values = new HashMap<>();
		private final long defaultTtlMs;

		private TestLocalCache(long defaultTtlMs) {
			this.defaultTtlMs = defaultTtlMs;
		}

		@Override
		public @NotNull CompletableFuture<Optional<T>> get(String key) {
			return CompletableFuture.completedFuture(Optional.ofNullable(values.get(key)));
		}

		@Override
		public @NotNull CompletableFuture<Void> put(String key, T value, long ttlMs) {
			if (key != null) values.put(key, value);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public long defaultTtlMs() {
			return defaultTtlMs;
		}

		@Override
		public @NotNull CompletableFuture<Void> invalidate(String key) {
			values.remove(key);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public @NotNull CompletableFuture<ReplicationPage> listKeys(int page, int pageSize) {
			return CompletableFuture.completedFuture(ReplicationPage.empty(page, pageSize));
		}
	}
}
