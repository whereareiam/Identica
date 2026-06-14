package me.whereareiam.identica.common.completion;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.completion.CompletionPendingState;
import me.whereareiam.identica.pipeline.completion.CompletionPendingStore;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.LocalCache;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class DefaultCompletionPendingStore implements CompletionPendingStore {
	private static final String NAMESPACE = "identica:completion-pending:connection";

	private final LocalCache<CompletionPendingState> cache;
	private final Provider<Engine> engineProvider;

	@Inject
	public DefaultCompletionPendingStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<Engine> engineProvider
	) {
		this.cache = replicationSystem.cache(NAMESPACE).local();
		this.engineProvider = engineProvider;
	}

	@Override
	public void put(@NotNull UUID connectionUniqueId, @NotNull CompletionPendingState pendingState) {
		cache.put(connectionUniqueId.toString(), pendingState, ttlMs()).join();
	}

	@Override
	public @NotNull Optional<CompletionPendingState> peek(@NotNull UUID connectionUniqueId) {
		return cache.get(connectionUniqueId.toString()).join();
	}

	@Override
	public @NotNull Optional<CompletionPendingState> consume(@NotNull UUID connectionUniqueId) {
		return cache.consume(connectionUniqueId.toString()).join();
	}

	@Override
	public boolean clear(@NotNull UUID connectionUniqueId) {
		return consume(connectionUniqueId).isPresent();
	}

	private long ttlMs() {
		return engineProvider.get().getBehavior().bridgeTtlMillis();
	}
}
