package me.whereareiam.identica.provider.capability.recognition.store;

import me.whereareiam.identica.Registry;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionCompletedEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionTerminatedEvent;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.provider.capability.recognition.config.RecognitionSettings;
import me.whereareiam.identica.replication.ReplicationChannel;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.LocalCache;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.cache.base.ReplicationCacheBuilder;
import me.whereareiam.identica.replication.codec.SnapshotCodec;
import me.whereareiam.identica.replication.codec.SnapshotCodecFactory;
import me.whereareiam.identica.replication.store.participant.ConnectionCompletedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionTerminatedParticipant;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Default Recognized Connection Store")
class DefaultRecognizedConnectionStoreTest {
	@DisplayName("Uses configured namespace and TTL")
	@Test
	void usesConfiguredNamespaceAndTtl() {
		TestReplicationSystem replicationSystem = new TestReplicationSystem();
			DefaultRecognizedConnectionStore store = new DefaultRecognizedConnectionStore(
					replicationSystem,
					() -> settings(Duration.ofSeconds(45), "recognition:test:connections"),
					() -> replication(false),
					noopCompletedRegistry(),
					noopTerminatedRegistry()
			);
		UUID connectionUniqueId = UUID.randomUUID();

		store.markRecognized(connectionUniqueId);

		assertTrue(store.isRecognized(connectionUniqueId));
		assertTrue(store.consumeRecognized(connectionUniqueId));
		assertEquals("recognition:test:connections", replicationSystem.lastCacheName);
		assertEquals(45_000L, replicationSystem.lastDefaultTtlMs);
		assertEquals(45_000L, replicationSystem.lastWriteTtlMs);
	}

	@DisplayName("Requires a configured recognized connection namespace")
	@Test
	void requiresConfiguredRecognizedConnectionNamespace() {
		IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
					new DefaultRecognizedConnectionStore(
							new TestReplicationSystem(),
							() -> settings(Duration.ofSeconds(30), " "),
							() -> replication(true),
							noopCompletedRegistry(),
							noopTerminatedRegistry()
					));

		assertTrue(exception.getMessage().contains("providers.capabilities.recognition.settings.replication.recognizedConnectionNamespace"));
	}

	private @NotNull RecognitionSettings settings(@NotNull Duration ttl, @NotNull String namespace) {
		RecognitionSettings settings = new RecognitionSettings();
		settings.setWindow(ttl);

		RecognitionSettings.Replication replication = new RecognitionSettings.Replication();
		replication.setRecognizedConnectionNamespace(namespace);
		replication.setSnapshotNamespace("recognition:test:snapshots");
		settings.setReplication(replication);
		return settings;
	}

	private @NotNull Replication replication(boolean enabled) {
		Replication replication = new Replication();
		replication.setEnabled(enabled);
		return replication;
	}

	@DisplayName("Completion and termination boundaries consume recognized state")
	@Test
	void completionAndTerminationBoundariesConsumeRecognizedState() {
		DefaultRecognizedConnectionStore completedStore = new DefaultRecognizedConnectionStore(
				new TestReplicationSystem(),
				() -> settings(Duration.ofSeconds(45), "recognition:test:completed"),
				() -> replication(false),
				noopCompletedRegistry(),
				noopTerminatedRegistry()
		);
		UUID completedConnection = UUID.randomUUID();
		completedStore.markRecognized(completedConnection);
		completedStore.onConnectionCompleted(new ConnectionCompletedEvent(completedConnection, null, null));
		assertFalse(completedStore.isRecognized(completedConnection));

		DefaultRecognizedConnectionStore terminatedStore = new DefaultRecognizedConnectionStore(
				new TestReplicationSystem(),
				() -> settings(Duration.ofSeconds(45), "recognition:test:terminated"),
				() -> replication(false),
				noopCompletedRegistry(),
				noopTerminatedRegistry()
		);
		UUID terminatedConnection = UUID.randomUUID();
		terminatedStore.markRecognized(terminatedConnection);
		terminatedStore.onConnectionTerminated(new ConnectionTerminatedEvent(terminatedConnection, null, null));
		assertFalse(terminatedStore.isRecognized(terminatedConnection));
	}

	private static Registry<ConnectionCompletedParticipant> noopCompletedRegistry() {
		return new Registry<>() {
			@Override
			public void register(ConnectionCompletedParticipant value) {
			}

			@Override
			public void unregister(ConnectionCompletedParticipant value) {
			}

			@Override
			public java.util.Set<ConnectionCompletedParticipant> values() {
				return java.util.Set.of();
			}
		};
	}

	private static Registry<ConnectionTerminatedParticipant> noopTerminatedRegistry() {
		return new Registry<>() {
			@Override
			public void register(ConnectionTerminatedParticipant value) {
			}

			@Override
			public void unregister(ConnectionTerminatedParticipant value) {
			}

			@Override
			public java.util.Set<ConnectionTerminatedParticipant> values() {
				return java.util.Set.of();
			}
		};
	}

	private static final class TestReplicationSystem implements ReplicationSystem {
		private final Map<String, Object> values = new ConcurrentHashMap<>();
		private SnapshotCodecFactory codecFactory = new SnapshotCodecFactory() {
			@Override
			public @NotNull <S> SnapshotCodec<S> codecFor(@NotNull Class<S> snapshotType) {
				return SnapshotCodec.json(snapshotType);
			}
		};

		private String lastCacheName;
		private long lastDefaultTtlMs;
		private long lastWriteTtlMs;

		@Override
		public @NotNull ReplicationCacheBuilder cache(@NotNull String name) {
			lastCacheName = name;

			return new ReplicationCacheBuilder() {
				private long defaultTtlMs;

				@Override
				public @NotNull ReplicationCacheBuilder defaultTtl(long ttlMs) {
					defaultTtlMs = ttlMs;
					lastDefaultTtlMs = ttlMs;
					return this;
				}

				@Override
				public @NotNull <T> LocalCache<T> local() {
					return new TestCache<>(name, values, defaultTtlMs, TestReplicationSystem.this::recordWriteTtl);
				}

				@Override
				public @NotNull <T, S> ReplicatedCache<T> replicated(@NotNull ReplicationType<T, S> type) {
					return new TestCache<>(name, values, defaultTtlMs, TestReplicationSystem.this::recordWriteTtl);
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
			codecFactory = factory;
		}

		@Override
		public @NotNull SnapshotCodecFactory getDefaultCodecFactory() {
			return codecFactory;
		}

		private void recordWriteTtl(long ttlMs) {
			lastWriteTtlMs = ttlMs;
		}
	}

	private static final class TestCache<T> implements LocalCache<T>, ReplicatedCache<T> {
		private final String namespace;
		private final Map<String, Object> values;
		private final long defaultTtlMs;
		private final Consumer<Long> writeTtlConsumer;

		private TestCache(
				@NotNull String namespace,
				@NotNull Map<String, Object> values,
				long defaultTtlMs,
				@NotNull Consumer<Long> writeTtlConsumer
		) {
			this.namespace = namespace;
			this.values = values;
			this.defaultTtlMs = defaultTtlMs;
			this.writeTtlConsumer = writeTtlConsumer;
		}

		@Override
		public @NotNull CompletableFuture<Optional<T>> get(String key) {
			return CompletableFuture.completedFuture(Optional.ofNullable(value(key)));
		}

		@Override
		public @NotNull CompletableFuture<Void> put(String key, T value, long ttlMs) {
			if (key != null && value != null) {
				values.put(storageKey(key), value);
				writeTtlConsumer.accept(ttlMs);
			}

			return CompletableFuture.completedFuture(null);
		}

		@Override
		public long defaultTtlMs() {
			return defaultTtlMs;
		}

		@Override
		public @NotNull CompletableFuture<Void> invalidate(String key) {
			if (key != null)
				values.remove(storageKey(key));

			return CompletableFuture.completedFuture(null);
		}

		private @SuppressWarnings("unchecked") T value(String key) {
			if (key == null) return null;
			return (T) values.get(storageKey(key));
		}

		private @NotNull String storageKey(@NotNull String key) {
			return namespace + ":" + key;
		}
	}
}
