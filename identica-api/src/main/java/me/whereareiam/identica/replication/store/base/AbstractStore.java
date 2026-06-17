package me.whereareiam.identica.replication.store.base;

import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.LocalCache;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.store.StateStore;
import org.jetbrains.annotations.NotNull;

/**
 * Shared base for concrete Identica state stores backed by the replication
 * system.
 */
public abstract class AbstractStore implements StateStore {
	private final @NotNull ReplicationSystem replicationSystem;

	protected AbstractStore(@NotNull ReplicationSystem replicationSystem) {
		this.replicationSystem = replicationSystem;
	}

	protected final <T> @NotNull LocalCache<T> localCache(@NotNull String namespace) {
		return replicationSystem.cache(namespace).local();
	}

	protected final <T> @NotNull LocalCache<T> localCache(@NotNull String namespace, long defaultTtlMs) {
		return replicationSystem.cache(namespace)
				.defaultTtl(defaultTtlMs)
				.local();
	}

	protected final <T, S> @NotNull ReplicatedCache<T> replicatedCache(
			@NotNull String namespace,
			@NotNull ReplicationType<T, S> type
	) {
		return replicationSystem.cache(namespace).replicated(type);
	}

	protected final <T, S> @NotNull ReplicatedCache<T> replicatedCache(
			@NotNull String namespace,
			long defaultTtlMs,
			@NotNull ReplicationType<T, S> type
	) {
		return replicationSystem.cache(namespace)
				.defaultTtl(defaultTtlMs)
				.replicated(type);
	}
}
