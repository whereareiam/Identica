package me.whereareiam.identica.provider.premium.profile;

import me.whereareiam.identica.Registry;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.model.replication.ReplicationPage;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.provider.premium.config.PremiumSettings;
import me.whereareiam.identica.replication.ReplicationChannel;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.LocalCache;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.cache.base.ReplicationCacheBuilder;
import me.whereareiam.identica.replication.codec.SnapshotCodecFactory;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Premium Profile Store")
class PremiumProfileStoreTest {
	@DisplayName("Stores, loads, and clears premium profiles by username")
	@Test
	void saveFindAndClearByUsernameOnly() {
			PremiumProfileStore store = new PremiumProfileStore(
					this::settings,
					new TestReplicationSystem(),
					noopRegistry()
			);

		store.save("PremiumUser", "profile-id");

		PremiumProfileSnapshot found = store.find("PremiumUser");
		assertNotNull(found);
		assertEquals("profile-id", found.getProfileId());

		store.clear("PremiumUser");
		assertNull(store.find("PremiumUser"));
	}

	private PremiumSettings settings() {
		PremiumSettings settings = new PremiumSettings();
		settings.setProfileSnapshotTtl(Duration.ofMinutes(1));
		return settings;
	}

	private static final class TestReplicationSystem implements ReplicationSystem {
		private final Map<String, TestReplicatedCache<?>> caches = new ConcurrentHashMap<>();

		@Override
		public @NotNull ReplicationCacheBuilder cache(@NotNull String name) {
			return new ReplicationCacheBuilder() {
				@Override
				public @NotNull ReplicationCacheBuilder defaultTtl(long ttlMs) {
					return this;
				}

				@Override
				public @NotNull <T> LocalCache<T> local() {
					return new TestReplicatedCache<>();
				}

				@SuppressWarnings("unchecked")
				@Override
				public @NotNull <T, S> ReplicatedCache<T> replicated(@NotNull ReplicationType<T, S> type) {
					return (ReplicatedCache<T>) caches.computeIfAbsent(name, ignored -> new TestReplicatedCache<>());
				}
			};
		}

		@Override
		public @NotNull <T, S> ReplicationChannel<T> channel(@NotNull String name, @NotNull ReplicationType<T, S> type) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setDefaultCodecFactory(@NotNull SnapshotCodecFactory factory) {
		}

		@Override
		public @NotNull SnapshotCodecFactory getDefaultCodecFactory() {
			return new SnapshotCodecFactory() {
				@Override
				public @NotNull <S> me.whereareiam.identica.replication.codec.SnapshotCodec<S> codecFor(@NotNull Class<S> snapshotType) {
					return me.whereareiam.identica.replication.codec.SnapshotCodec.json(snapshotType);
				}
			};
		}
	}

	@DisplayName("Account lifecycle clears the cached profile")
	@Test
	void accountLifecycleClearsCachedProfile() {
		PremiumProfileStore store = new PremiumProfileStore(this::settings, new TestReplicationSystem(), noopRegistry());
		store.save("PremiumUser", "profile-id");

		store.onAccountLifecycle(new AccountLifecycleEvent(new me.whereareiam.identica.identity.actor.ConnectionIdentity(
				java.util.UUID.randomUUID(),
				"PremiumUser",
				null
		)));

		assertNull(store.find("PremiumUser"));
	}

	private static Registry<AccountLifecycleParticipant> noopRegistry() {
		return new Registry<>() {
			@Override
			public void register(AccountLifecycleParticipant value) {
			}

			@Override
			public void unregister(AccountLifecycleParticipant value) {
			}

			@Override
			public java.util.Set<AccountLifecycleParticipant> values() {
				return java.util.Set.of();
			}
		};
	}

	private static final class TestReplicatedCache<T> implements ReplicatedCache<T>, LocalCache<T> {
		private final Map<String, T> values = new ConcurrentHashMap<>();

		@Override
		public @NotNull CompletableFuture<Optional<T>> get(String key) {
			return CompletableFuture.completedFuture(Optional.ofNullable(values.get(key)));
		}

		@Override
		public @NotNull CompletableFuture<Void> put(String key, T value, long ttlMs) {
			if (key != null && value != null)
				values.put(key, value);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public @NotNull CompletableFuture<Void> invalidate(String key) {
			if (key != null)
				values.remove(key);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public @NotNull CompletableFuture<Optional<T>> consume(String key) {
			T removed = key != null ? values.remove(key) : null;
			return CompletableFuture.completedFuture(Optional.ofNullable(removed));
		}

		@Override
		public @NotNull CompletableFuture<ReplicationPage> listKeys(int page, int pageSize) {
			return CompletableFuture.completedFuture(ReplicationPage.empty(page, pageSize));
		}
	}
}
