package me.whereareiam.identica.common.prepare;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.LocalCache;
import me.whereareiam.identica.replication.store.base.AbstractDisconnectScopedStore;
import me.whereareiam.identica.replication.store.participant.ConnectionDisconnectedParticipant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class DefaultPrepareStateStore extends AbstractDisconnectScopedStore implements PrepareStateStore {
	private static final String CONNECTION_NAMESPACE = "identica:prepare-state:connection";
	private static final String UNIQUE_ID_NAMESPACE = "identica:prepare-state:unique-id";
	private static final String UNIQUE_ID_INDEX_NAMESPACE = "identica:prepare-state:index";

	private final LocalCache<PrepareDecision> byConnectionKey;
	private final LocalCache<PrepareDecision> byUniqueId;
	private final LocalCache<String> keysByUniqueId;
	private final Provider<Engine> engineProvider;

	@Inject
	public DefaultPrepareStateStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<Engine> engineProvider,
			@NotNull Registry<ConnectionDisconnectedParticipant> participants
	) {
		super(replicationSystem, participants);
		this.byConnectionKey = localCache(CONNECTION_NAMESPACE);
		this.byUniqueId = localCache(UNIQUE_ID_NAMESPACE);
		this.keysByUniqueId = localCache(UNIQUE_ID_INDEX_NAMESPACE);
		this.engineProvider = engineProvider;
	}

	@Override
	public void put(@NotNull String connectionKey, @NotNull PrepareDecision decision) {
		byConnectionKey.put(connectionKey, decision, ttlMs()).join();
	}

	@Override
	public void put(
			@NotNull UUID uniqueId,
			@Nullable String connectionKey,
			@NotNull PrepareDecision decision
	) {
		byUniqueId.put(uniqueId, decision, ttlMs()).join();
		if (connectionKey != null && !connectionKey.isBlank())
			keysByUniqueId.put(uniqueId, connectionKey, ttlMs()).join();
		if (connectionKey != null && !connectionKey.isBlank())
			put(connectionKey, decision);
	}

	@Override
	public @NotNull Optional<PrepareDecision> peek(@NotNull String connectionKey) {
		return byConnectionKey.get(connectionKey).join();
	}

	@Override
	public @NotNull Optional<PrepareDecision> peek(@NotNull UUID uniqueId) {
		return byUniqueId.get(uniqueId).join();
	}

	@Override
	public boolean clear(@NotNull UUID uniqueId) {
		boolean removed = byUniqueId.consume(uniqueId).join().isPresent();

		String connectionKey = keysByUniqueId.consume(uniqueId).join().orElse(null);
		if (connectionKey != null) removed = byConnectionKey.consume(connectionKey).join().isPresent() || removed;

		return removed;
	}

	@Override
	public void onConnectionDisconnected(@NotNull ConnectionDisconnectedEvent event) {
		clear(event.getConnectionUniqueId());
	}

	private long ttlMs() {
		return engineProvider.get().getBehavior().bridgeTtlMillis();
	}
}
