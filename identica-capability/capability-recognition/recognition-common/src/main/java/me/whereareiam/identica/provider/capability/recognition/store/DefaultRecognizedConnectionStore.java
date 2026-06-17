package me.whereareiam.identica.provider.capability.recognition.store;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionCompletedEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionTerminatedEvent;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.provider.capability.recognition.config.RecognitionSettings;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.base.Cache;
import me.whereareiam.identica.replication.store.base.AbstractFinalizedConnectionScopedStore;
import me.whereareiam.identica.replication.store.participant.ConnectionCompletedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionTerminatedParticipant;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class DefaultRecognizedConnectionStore extends AbstractFinalizedConnectionScopedStore implements RecognizedConnectionStore {
	private final Cache<Boolean> cache;
	private final Provider<RecognitionSettings> settingsProvider;

	@Inject
	public DefaultRecognizedConnectionStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<RecognitionSettings> settingsProvider,
			@NotNull Provider<Replication> replicationProvider,
			@NotNull Registry<ConnectionCompletedParticipant> completedParticipants,
			@NotNull Registry<ConnectionTerminatedParticipant> terminatedParticipants
	) {
		super(replicationSystem, completedParticipants, terminatedParticipants);
		this.settingsProvider = settingsProvider;
		RecognitionSettings settings = settingsProvider.get();
		long ttlMs = settings.windowMillis();

		String namespace = resolveNamespace(settings);
		this.cache = replicationProvider.get().isEnabled()
				? replicatedCache(namespace, ttlMs, ReplicationType.identity(Boolean.class))
				: localCache(namespace, ttlMs);
	}

	@Override
	public void markRecognized(@NotNull UUID connectionUniqueId) {
		cache.put(connectionUniqueId, Boolean.TRUE, settingsProvider.get().windowMillis()).join();
	}

	@Override
	public boolean isRecognized(@NotNull UUID connectionUniqueId) {
		Optional<Boolean> stored = cache.get(connectionUniqueId).join();
		return stored.orElse(false);
	}

	@Override
	public boolean consumeRecognized(@NotNull UUID connectionUniqueId) {
		Optional<Boolean> stored = cache.consume(connectionUniqueId).join();
		return stored.orElse(false);
	}

	@Override
	public void onConnectionCompleted(@NotNull ConnectionCompletedEvent event) {
		consumeRecognized(event.getConnectionUniqueId());
	}

	@Override
	public void onConnectionTerminated(@NotNull ConnectionTerminatedEvent event) {
		consumeRecognized(event.getConnectionUniqueId());
	}

	private static @NotNull String resolveNamespace(@NotNull RecognitionSettings settings) {
		String namespace = settings.getReplication().getRecognizedConnectionNamespace();
		if (namespace.isBlank())
			throw new IllegalStateException("providers.capabilities.recognition.settings.replication.recognizedConnectionNamespace is missing");

		return namespace;
	}
}
