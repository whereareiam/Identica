package me.whereareiam.identica.feature.recognition.store;

import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
import me.whereareiam.identica.feature.recognition.model.SessionRecognitionSnapshot;
import me.whereareiam.identica.replication.ReplicationChannel;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.LocalCache;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.cache.base.ReplicationCacheBuilder;
import me.whereareiam.identica.replication.codec.SnapshotCodec;
import me.whereareiam.identica.replication.codec.SnapshotCodecFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Default Session-Recognition Store")
class DefaultSessionRecognitionStoreTest {
	@DisplayName("Recognition snapshots are keyed by provider id and subject")
	@Test
	void snapshotsUseProviderIdAndSubjectKey() {
		DefaultSessionRecognitionStore store = new DefaultSessionRecognitionStore(
				this::settings,
				this::replication,
				new TestReplicationSystem()
		);

		store.save(SessionRecognitionSnapshot.builder()
				.providerId("premium")
				.providerSubject("subject-1")
				.providerUsername("PlayerOne")
				.lastIp("127.0.0.1")
				.capturedAt(System.currentTimeMillis())
				.build());

		SessionRecognitionSnapshot found = store.find("premium", "subject-1").orElse(null);
		assertNotNull(found);
		assertEquals("PlayerOne", found.getProviderUsername());
		assertEquals("127.0.0.1", found.getLastIp());
	}

	@DisplayName("Recognition snapshots do not leak across subjects")
	@Test
	void snapshotsRequireMatchingSubject() {
		DefaultSessionRecognitionStore store = new DefaultSessionRecognitionStore(
				this::settings,
				this::replication,
				new TestReplicationSystem()
		);

		store.save(SessionRecognitionSnapshot.builder()
				.providerId("premium")
				.providerSubject("subject-1")
				.providerUsername("PlayerOne")
				.capturedAt(System.currentTimeMillis())
				.build());

		assertTrue(store.find("premium", "subject-2").isEmpty());
	}

	private Replication replication() {
		Replication replication = new Replication();
		replication.setEnabled(true);
		return replication;
	}

	private RecognitionSettings settings() {
		RecognitionSettings settings = new RecognitionSettings();
		settings.setValidity(Duration.ofHours(12));
		RecognitionSettings.Replication replication = new RecognitionSettings.Replication();
		replication.setSnapshotNamespace("session-recognition:snapshot");
		settings.setReplication(replication);
		return settings;
	}

	private static final class TestReplicationSystem implements ReplicationSystem {
		private final Map<String, Object> values = new ConcurrentHashMap<>();
		private SnapshotCodecFactory codecFactory = new SnapshotCodecFactory() {
			@Override
			public @NotNull <S> SnapshotCodec<S> codecFor(@NotNull Class<S> snapshotType) {
				return SnapshotCodec.json(snapshotType);
			}
		};

		@Override
		public @NotNull ReplicationCacheBuilder cache(@NotNull String name) {
			return new ReplicationCacheBuilder() {
				private long defaultTtlMs;

				@Override
				public @NotNull ReplicationCacheBuilder defaultTtl(long ttlMs) {
					this.defaultTtlMs = ttlMs;
					return this;
				}

				@Override
				public @NotNull <T> LocalCache<T> local() {
					throw new UnsupportedOperationException("local cache is not used by this test");
				}

				@Override
				public @NotNull <T, S> ReplicatedCache<T> replicated(@NotNull ReplicationType<T, S> type) {
					return new TestReplicatedCache<>(name, values, defaultTtlMs);
				}
			};
		}

		@Override
		public @NotNull <T, S> ReplicationChannel<T> channel(
				@NotNull String name,
				@NotNull ReplicationType<T, S> type
		) {
			throw new UnsupportedOperationException("channels are not used by this test");
		}

		@Override
		public void setDefaultCodecFactory(@NotNull SnapshotCodecFactory factory) {
			this.codecFactory = factory;
		}

		@Override
		public @NotNull SnapshotCodecFactory getDefaultCodecFactory() {
			return codecFactory;
		}
	}

	private static final class TestReplicatedCache<T> implements ReplicatedCache<T> {
		private final String namespace;
		private final Map<String, Object> values;
		private final long defaultTtlMs;

		private TestReplicatedCache(String namespace, Map<String, Object> values, long defaultTtlMs) {
			this.namespace = namespace;
			this.values = values;
			this.defaultTtlMs = defaultTtlMs;
		}

		@Override
		public @NotNull CompletableFuture<Optional<T>> get(@Nullable String key) {
			return CompletableFuture.completedFuture(Optional.ofNullable(value(key)));
		}

		@Override
		public @NotNull CompletableFuture<Void> put(@Nullable String key, @Nullable T value, long ttlMs) {
			if (key != null && value != null)
				values.put(storageKey(key), value);

			return CompletableFuture.completedFuture(null);
		}

		@Override
		public long defaultTtlMs() {
			return defaultTtlMs;
		}

		@Override
		public @NotNull CompletableFuture<Void> invalidate(@Nullable String key) {
			if (key != null)
				values.remove(storageKey(key));

			return CompletableFuture.completedFuture(null);
		}

		@SuppressWarnings("unchecked")
		private @Nullable T value(@Nullable String key) {
			if (key == null) return null;
			return (T) values.get(storageKey(key));
		}

		private @NotNull String storageKey(@NotNull String key) {
			return namespace + ":" + key;
		}
	}
}
