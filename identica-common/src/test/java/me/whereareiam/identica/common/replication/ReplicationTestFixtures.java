package me.whereareiam.identica.common.replication;

import me.whereareiam.identica.model.replication.ReplicationPage;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.replication.ReplicationAdapter;
import me.whereareiam.identica.replication.codec.SnapshotCodec;
import me.whereareiam.identica.replication.codec.SnapshotCodecFactory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class ReplicationTestFixtures {
	public static ReplicationType<String, String> stringType() {
		return ReplicationType.identity(String.class)
				.withCodec(SnapshotCodec.string());
	}

	@SuppressWarnings("unchecked")
	public static SnapshotCodecFactory stringCodecFactory() {
		return new SnapshotCodecFactory() {
			@Override
			public @NotNull <S> SnapshotCodec<S> codecFor(@NotNull Class<S> snapshotType) {
				return (SnapshotCodec<S>) SnapshotCodec.string();
			}
		};
	}

	public static final class TestReplicationAdapter implements ReplicationAdapter {
		public boolean available = true;
		public Optional<byte[]> nextGet = Optional.empty();
		public Optional<byte[]> nextConsume = Optional.empty();
		public ReplicationPage nextListKeys;
		/**
		 * Keeps written values and answers reads from them, like a shared Redis, instead of answering
		 * {@link #nextGet}.
		 */
		public boolean storing;
		private final Map<String, byte[]> stored = new HashMap<>();

		public int getCalls;
		public int consumeCalls;
		public int putCalls;
		public int invalidateCalls;
		public int listKeysCalls;
		public int publishCalls;

		public String lastNamespace;
		public String lastKey;
		public byte[] lastValue;
		public long lastTtlMs;
		public String lastPublishChannel;
		public byte[] lastPublishPayload;

		private final List<Consumer<byte[]>> subscribers = new ArrayList<>();

		@Override
		public boolean isAvailable() {
			return available;
		}

		@Override
		public @NotNull CompletableFuture<Optional<byte[]>> get(
				@NotNull String namespace,
				@NotNull String key
		) {
			getCalls++;
			lastNamespace = namespace;
			lastKey = key;
			if (storing) return CompletableFuture.completedFuture(Optional.ofNullable(stored.get(namespace + "|" + key)));
			return CompletableFuture.completedFuture(nextGet);
		}

		@Override
		public @NotNull CompletableFuture<Optional<byte[]>> consume(
				@NotNull String namespace,
				@NotNull String key
		) {
			consumeCalls++;
			lastNamespace = namespace;
			lastKey = key;
			return CompletableFuture.completedFuture(nextConsume);
		}

		@Override
		public @NotNull CompletableFuture<Void> put(
				@NotNull String namespace,
				@NotNull String key,
				byte[] value,
				long ttlMs
		) {
			putCalls++;
			lastNamespace = namespace;
			lastKey = key;
			lastValue = value;
			lastTtlMs = ttlMs;
			if (storing) stored.put(namespace + "|" + key, value);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public @NotNull CompletableFuture<Void> invalidate(
				@NotNull String namespace,
				@NotNull String key
		) {
			invalidateCalls++;
			lastNamespace = namespace;
			lastKey = key;
			if (storing) stored.remove(namespace + "|" + key);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public @NotNull CompletableFuture<ReplicationPage> listKeys(
				@NotNull String namespace,
				int page,
				int pageSize
		) {
			listKeysCalls++;
			if (nextListKeys != null) return CompletableFuture.completedFuture(nextListKeys);
			int safePage = Math.max(1, page);
			int safeSize = Math.max(1, pageSize);
			return CompletableFuture.completedFuture(ReplicationPage.empty(safePage, safeSize));
		}

		@Override
		public @NotNull CompletableFuture<Void> publish(@NotNull String channel, byte[] payload) {
			publishCalls++;
			lastPublishChannel = channel;
			lastPublishPayload = payload;
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void subscribe(@NotNull String channel, @NotNull Consumer<byte[]> handler) {
			this.subscribers.add(handler);
		}

		public void emit(byte[] payload) {
			for (Consumer<byte[]> subscriber : subscribers)
				subscriber.accept(payload);
		}
	}
}
