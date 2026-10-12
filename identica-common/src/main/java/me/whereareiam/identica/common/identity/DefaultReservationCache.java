package me.whereareiam.identica.common.identity;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.identity.ReservationCache;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.codec.SnapshotCodec;
import me.whereareiam.identica.util.UniqueIdUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Singleton
public class DefaultReservationCache implements ReservationCache {
	private final Supplier<ReplicatedCache<String>> cache;

	@Inject
	public DefaultReservationCache(
			ReplicationSystem replicationSystem,
			Provider<Replication> replicationProvider
	) {
		this.cache = Suppliers.memoize(() -> replicationSystem.cache(resolveNamespace(replicationProvider))
				.replicated(ReplicationType.identity(String.class).withCodec(SnapshotCodec.string())));
	}

	@Override
	public @NotNull CompletableFuture<Optional<UUID>> get(String key) {
		if (key == null || key.isBlank()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		return cache.get().get(key)
				.thenApply(value -> value.flatMap(UniqueIdUtil::parseOptionalUniqueId));
	}

	@Override
	public @NotNull CompletableFuture<Void> put(String key, UUID uuid, long ttlMs) {
		if (key == null || key.isBlank() || uuid == null) {
			return CompletableFuture.completedFuture(null);
		}
		return cache.get().put(key, uuid.toString(), ttlMs);
	}

	@Override
	public @NotNull CompletableFuture<Void> invalidate(String key) {
		if (key == null || key.isBlank()) {
			return CompletableFuture.completedFuture(null);
		}
		return cache.get().invalidate(key);
	}

	private static String resolveNamespace(Provider<Replication> replicationProvider) {
		Replication replication = replicationProvider.get();
		if (replication == null)
			throw new IllegalStateException("replication is missing");

		String namespace = replication.getCache().getReservations();
		if (namespace.isBlank())
			throw new IllegalStateException("replication.cache.reservations is missing");

		return namespace;
	}
}
