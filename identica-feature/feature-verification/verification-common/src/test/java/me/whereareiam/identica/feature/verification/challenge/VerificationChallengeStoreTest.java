package me.whereareiam.identica.feature.verification.challenge;

import me.whereareiam.identica.feature.verification.model.challenge.PendingVerificationChallenge;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.model.replication.ReplicationPage;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.replication.ReplicationChannel;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.LocalCache;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.cache.base.ReplicationCacheBuilder;
import me.whereareiam.identica.replication.codec.SnapshotCodecFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("Verification Challenge Store")
class VerificationChallengeStoreTest {
	@DisplayName("Clearing by unique id removes only that account's active and verified state")
	@Test
	void clearByUniqueIdRemovesOnlyIndexedAccountState() {
		VerificationChallengeStore store = new VerificationChallengeStore(
				new TestReplicationSystem(),
				this::settings
		);
		UUID clearedUniqueId = UUID.randomUUID();
		UUID retainedUniqueId = UUID.randomUUID();
		PendingVerificationChallenge clearedActive = record("challenge-active-cleared", clearedUniqueId, "auth", "login");
		PendingVerificationChallenge clearedVerified = record("challenge-verified-cleared", clearedUniqueId, "premium", "migration");
		PendingVerificationChallenge retainedActive = record("challenge-active-retained", retainedUniqueId, "auth", "login");
		PendingVerificationChallenge retainedVerified = record("challenge-verified-retained", retainedUniqueId, "premium", "migration");

		store.put(clearedActive);
		store.put(clearedActive.toBuilder().statePayload("updated").build());
		store.put(clearedVerified);
		store.markVerified(clearedVerified);
		store.put(retainedActive);
		store.put(retainedVerified);
		store.markVerified(retainedVerified);

		store.clearByUniqueId(clearedUniqueId);

		assertTrue(store.find(clearedActive.getChallengeId()).isEmpty());
		assertTrue(store.findActive(clearedUniqueId, "auth", "login").isEmpty());
		assertFalse(store.consumeVerified(clearedUniqueId, "premium", "migration"));
		assertEquals(
				retainedActive.getChallengeId(),
				store.findActive(retainedUniqueId, "auth", "login").orElseThrow().getChallengeId()
		);
		assertTrue(store.consumeVerified(retainedUniqueId, "premium", "migration"));
	}

	private @NotNull PendingVerificationChallenge record(
			@NotNull String challengeId,
			@NotNull UUID uniqueId,
			@Nullable String providerId,
			@Nullable String purpose
	) {
		long now = System.currentTimeMillis();
		return PendingVerificationChallenge.builder()
				.challengeId(challengeId)
				.uniqueId(uniqueId)
				.methodId("totp")
				.providerId(providerId)
				.purpose(purpose)
				.required(true)
				.stateType("test")
				.statePayload("payload")
				.createdAt(now)
				.expiresAt(now + settings().challengeTtlMillis())
				.build();
	}

	private @NotNull VerificationSettings settings() {
		VerificationSettings settings = new VerificationSettings();
		settings.setChallengeTtl(Duration.ofMinutes(5));
		settings.setEnrollmentTtl(Duration.ofMinutes(5));
		return settings;
	}

	private static final class TestReplicationSystem implements ReplicationSystem {
		private final Map<String, LocalCache<?>> caches = new HashMap<>();
		private SnapshotCodecFactory codecFactory = mock(SnapshotCodecFactory.class);

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
		public @NotNull <T, S> ReplicationChannel<T> channel(
				@NotNull String name,
				@NotNull ReplicationType<T, S> type
		) {
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
		private final Map<String, T> values = new HashMap<>();
		private final long defaultTtlMs;

		private TestLocalCache(long defaultTtlMs) {
			this.defaultTtlMs = defaultTtlMs;
		}

		@Override
		public @NotNull CompletableFuture<Optional<T>> get(@Nullable String key) {
			return CompletableFuture.completedFuture(Optional.ofNullable(values.get(key)));
		}

		@Override
		public @NotNull CompletableFuture<Void> put(@Nullable String key, @Nullable T value, long ttlMs) {
			if (key != null) values.put(key, value);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public long defaultTtlMs() {
			return defaultTtlMs;
		}

		@Override
		public @NotNull CompletableFuture<Void> invalidate(@Nullable String key) {
			values.remove(key);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public @NotNull CompletableFuture<ReplicationPage> listKeys(
				int page,
				int pageSize
		) {
			int safePage = Math.max(1, page);
			int safeSize = Math.max(1, pageSize);
			List<String> keys = new ArrayList<>(values.keySet());
			int fromIndex = Math.min((safePage - 1) * safeSize, keys.size());
			int toIndex = Math.min(fromIndex + safeSize, keys.size());

			return CompletableFuture.completedFuture(
					new ReplicationPage(
							keys.subList(fromIndex, toIndex),
							safePage,
							safeSize,
							keys.size()
					)
			);
		}
	}
}
