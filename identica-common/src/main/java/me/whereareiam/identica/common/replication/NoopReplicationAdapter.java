package me.whereareiam.identica.common.replication;

import me.whereareiam.identica.replication.ReplicationAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * No-op replication adapter used when no backend is configured.
 */
public class NoopReplicationAdapter implements ReplicationAdapter {
	@Override
	public boolean isAvailable() {
		return false;
	}

	@Override
	public @NotNull CompletableFuture<Optional<byte[]>> get(@NotNull String namespace, @NotNull String key) {
		return CompletableFuture.completedFuture(Optional.empty());
	}

	@Override
	public @NotNull CompletableFuture<Optional<byte[]>> consume(@NotNull String namespace, @NotNull String key) {
		return CompletableFuture.completedFuture(Optional.empty());
	}

	@Override
	public @NotNull CompletableFuture<Void> put(@NotNull String namespace, @NotNull String key, byte[] value, long ttlMs) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public @NotNull CompletableFuture<Boolean> compareAndPut(
			@NotNull String namespace,
			@NotNull String key,
			byte @Nullable [] expected,
			byte @NotNull [] value,
			long ttlMs
	) {
		return CompletableFuture.completedFuture(false);
	}

	@Override
	public @NotNull CompletableFuture<Boolean> compareAndInvalidate(@NotNull String namespace, @NotNull String key, byte @NotNull [] expected) {
		return CompletableFuture.completedFuture(false);
	}

	@Override
	public @NotNull CompletableFuture<Void> invalidate(@NotNull String namespace, @NotNull String key) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public @NotNull CompletableFuture<Void> publish(@NotNull String channel, byte[] payload) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void subscribe(@NotNull String channel, @NotNull Consumer<byte[]> handler) {
	}
}
