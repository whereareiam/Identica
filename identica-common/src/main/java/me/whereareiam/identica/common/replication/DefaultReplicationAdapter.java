package me.whereareiam.identica.common.replication;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.identica.replication.ReplicationAdapter;
import me.whereareiam.identica.model.replication.ReplicationPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Singleton
public class DefaultReplicationAdapter implements ReplicationAdapter {
	private final @NotNull ReplicationAdapter provider;

	@Inject
	public DefaultReplicationAdapter(
			@Named("replicationAdapter") @NotNull ReplicationAdapter provider
	) {
		this.provider = provider;
	}

	@Override
	public boolean isAvailable() {
		return provider.isAvailable();
	}

	@Override
	public @NotNull CompletableFuture<Optional<byte[]>> get(@NotNull String namespace, @NotNull String key) {
		if (!provider.isAvailable()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}

		return provider.get(namespace, key);
	}

	@Override
	public @NotNull CompletableFuture<Optional<byte[]>> consume(@NotNull String namespace, @NotNull String key) {
		if (!provider.isAvailable()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}

		return provider.consume(namespace, key);
	}

	@Override
	public @NotNull CompletableFuture<Void> put(
			@NotNull String namespace,
			@NotNull String key,
			byte[] value,
			long ttlMs
	) {
		if (!provider.isAvailable()) {
			return CompletableFuture.completedFuture(null);
		}

		return provider.put(namespace, key, value, ttlMs);
	}

	@Override
	public @NotNull CompletableFuture<Boolean> compareAndPut(
			@NotNull String namespace,
			@NotNull String key,
			byte @Nullable [] expected,
			byte @NotNull [] value,
			long ttlMs
	) {
		if (!provider.isAvailable()) return CompletableFuture.completedFuture(false);

		return provider.compareAndPut(namespace, key, expected, value, ttlMs);
	}

	@Override
	public @NotNull CompletableFuture<Boolean> compareAndInvalidate(@NotNull String namespace, @NotNull String key, byte @NotNull [] expected) {
		if (!provider.isAvailable()) return CompletableFuture.completedFuture(false);

		return provider.compareAndInvalidate(namespace, key, expected);
	}

	@Override
	public @NotNull CompletableFuture<Void> invalidate(@NotNull String namespace, @NotNull String key) {
		if (!provider.isAvailable()) {
			return CompletableFuture.completedFuture(null);
		}

		return provider.invalidate(namespace, key);
	}

	@Override
	public @NotNull CompletableFuture<ReplicationPage> listKeys(
			@NotNull String namespace,
			int page,
			int pageSize
	) {
		if (!provider.isAvailable()) {
			int safePage = Math.max(1, page);
			int safeSize = Math.max(1, pageSize);
			return CompletableFuture.completedFuture(ReplicationPage.empty(safePage, safeSize));
		}

		return provider.listKeys(namespace, page, pageSize);
	}

	@Override
	public @NotNull CompletableFuture<Void> publish(@NotNull String channel, byte @NotNull [] payload) {
		if (!provider.isAvailable()) {
			return CompletableFuture.completedFuture(null);
		}

		return provider.publish(channel, payload);
	}

	@Override
	public void subscribe(@NotNull String channel, @NotNull Consumer<byte[]> handler) {
		if (!provider.isAvailable()) return;
		provider.subscribe(channel, handler);
	}
}
