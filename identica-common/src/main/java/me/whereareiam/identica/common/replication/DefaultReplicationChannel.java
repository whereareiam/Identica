package me.whereareiam.identica.common.replication;

import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.replication.ReplicationAdapter;
import me.whereareiam.identica.replication.ReplicationChannel;
import me.whereareiam.identica.model.replication.ReplicationEnvelope;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.replication.codec.SnapshotCodec;
import me.whereareiam.identica.replication.codec.SnapshotCodecFactory;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

@RequiredArgsConstructor
public final class DefaultReplicationChannel<T, S> implements ReplicationChannel<T> {
	private final String channel;
	private final ReplicationAdapter adapter;
	private final ReplicationType<T, S> type;
	private final SnapshotCodecFactory codecFactory;

	@Override
	public @NotNull CompletableFuture<Void> publish(@NotNull T message) {
		if (adapter == null || !adapter.isAvailable()) return CompletableFuture.completedFuture(null);

		SnapshotCodec<S> codec = resolveCodec();
		S snapshot = type.mapper().toSnapshot(message);
		byte[] payload = codec.encode(snapshot);
		byte[] envelope = ReplicationEnvelope.encode(type.version(), 0L, payload);

		return adapter.publish(channel, envelope).exceptionally(failure -> {
			Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
			Logger.warn("Could not publish to the replication channel %s: %s", channel, cause.getMessage());
			return null;
		});
	}

	@Override
	public void subscribe(@NotNull Consumer<T> handler) {
		if (adapter == null || !adapter.isAvailable()) return;

		adapter.subscribe(channel, payload -> {
			ReplicationEnvelope envelope = ReplicationEnvelope.decode(payload);
			if (envelope == null || envelope.getVersion() != type.version()) return;

			SnapshotCodec<S> codec = resolveCodec();
			S snapshot;
			try {
				snapshot = codec.decode(envelope.getPayload());
			} catch (Exception ignored) {
				return;
			}
			if (snapshot == null) return;

			type.mapper().fromSnapshot(snapshot)
					.thenAccept(resolved -> {
						if (resolved != null) handler.accept(resolved);
					});
		});
	}

	private SnapshotCodec<S> resolveCodec() {
		SnapshotCodec<S> override = type.codecOverride();
		if (override != null) return override;
		return codecFactory.codecFor(type.snapshotType());
	}
}
