package me.whereareiam.identica.common.migration.confirmation;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.LocalCache;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Singleton
public final class MigrationConfirmationStore {
	private static final String NAMESPACE = "identica:migration-confirmation:connection";
	// Retain entries well past confirmation TTL so the service can still report EXPIRED instead of NO_PENDING.
	private static final long CACHE_RETENTION_TTL_MS = Duration.ofDays(3650).toMillis();

	private final LocalCache<PendingConfirmationMigration> cache;
	private final Provider<Commands> commandsProvider;

	@Inject
	public MigrationConfirmationStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<Commands> commandsProvider
	) {
		this.cache = replicationSystem.cache(NAMESPACE).local();
		this.commandsProvider = commandsProvider;
	}

	public void put(@NotNull PendingConfirmationMigration pendingMigration) {
		cache.put(key(pendingMigration.getConnectionUniqueId()), pendingMigration, CACHE_RETENTION_TTL_MS).join();
	}

	public @NotNull Optional<PendingConfirmationMigration> find(@NotNull UUID connectionUniqueId) {
		return cache.get(key(connectionUniqueId)).join();
	}

	public @NotNull Optional<PendingConfirmationMigration> consume(@NotNull UUID connectionUniqueId) {
		return cache.consume(key(connectionUniqueId)).join();
	}

	public boolean clear(@NotNull UUID connectionUniqueId) {
		return consume(connectionUniqueId).isPresent();
	}

	public boolean isExpired(@NotNull PendingConfirmationMigration pendingMigration) {
		long ttlMs = commandsProvider.get().getBehavior().getMigration().getConfirmTtl().toMillis();
		return ttlMs > 0 && pendingMigration.getRequestedAt() + ttlMs < System.currentTimeMillis();
	}

	private @NotNull String key(@NotNull UUID connectionUniqueId) {
		return connectionUniqueId.toString();
	}
}
