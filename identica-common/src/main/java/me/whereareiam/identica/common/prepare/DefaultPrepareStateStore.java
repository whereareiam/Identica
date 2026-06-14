package me.whereareiam.identica.common.prepare;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.LocalCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class DefaultPrepareStateStore implements PrepareStateStore {
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
			@NotNull Provider<Engine> engineProvider
	) {
		this.byConnectionKey = replicationSystem.cache(CONNECTION_NAMESPACE).local();
		this.byUniqueId = replicationSystem.cache(UNIQUE_ID_NAMESPACE).local();
		this.keysByUniqueId = replicationSystem.cache(UNIQUE_ID_INDEX_NAMESPACE).local();
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
		byUniqueId.put(uniqueId.toString(), decision, ttlMs()).join();
		if (connectionKey != null && !connectionKey.isBlank())
			keysByUniqueId.put(uniqueId.toString(), connectionKey, ttlMs()).join();
		if (connectionKey != null && !connectionKey.isBlank())
			put(connectionKey, decision);
	}

	@Override
	public @NotNull Optional<PrepareDecision> peek(@NotNull String connectionKey) {
		return byConnectionKey.get(connectionKey).join();
	}

	@Override
	public @NotNull Optional<PrepareDecision> peek(@NotNull UUID uniqueId) {
		return byUniqueId.get(uniqueId.toString()).join();
	}

	@Override
	public boolean clear(@NotNull UUID uniqueId) {
		String uniqueIdKey = uniqueId.toString();
		boolean removed = byUniqueId.consume(uniqueIdKey).join().isPresent();

		String connectionKey = keysByUniqueId.consume(uniqueIdKey).join().orElse(null);
		if (connectionKey != null) removed = byConnectionKey.consume(connectionKey).join().isPresent() || removed;

		return removed;
	}

	private long ttlMs() {
		return engineProvider.get().getBehavior().bridgeTtlMillis();
	}
}
