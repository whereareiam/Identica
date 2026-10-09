package me.whereareiam.identica.feature.recognition.store;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.base.Cache;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class DefaultRecognizedConnectionStore implements RecognizedConnectionStore {
	private final Cache<Boolean> cache;
	private final Provider<RecognitionSettings> settingsProvider;

	@Inject
	public DefaultRecognizedConnectionStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<RecognitionSettings> settingsProvider,
			@NotNull Provider<Replication> replicationProvider
	) {
		this.settingsProvider = settingsProvider;
		RecognitionSettings settings = settingsProvider.get();
		long ttlMs = settings.windowMillis();

		String namespace = resolveNamespace(settings);
		this.cache = replicationProvider.get().isEnabled()
				? replicationSystem.cache(namespace)
						.defaultTtl(ttlMs)
						.replicated(ReplicationType.identity(Boolean.class))
				: replicationSystem.cache(namespace)
						.defaultTtl(ttlMs)
						.local();
	}

	@Override
	public void markRecognized(@NotNull UUID connectionUniqueId) {
		cache.put(connectionUniqueId.toString(), Boolean.TRUE, settingsProvider.get().windowMillis()).join();
	}

	@Override
	public boolean isRecognized(@NotNull UUID connectionUniqueId) {
		Optional<Boolean> stored = cache.get(connectionUniqueId.toString()).join();
		return stored.orElse(false);
	}

	@Override
	public boolean consumeRecognized(@NotNull UUID connectionUniqueId) {
		Optional<Boolean> stored = cache.consume(connectionUniqueId.toString()).join();
		return stored.orElse(false);
	}

	private static @NotNull String resolveNamespace(@NotNull RecognitionSettings settings) {
		String namespace = settings.getReplication().getRecognizedConnectionNamespace();
		if (namespace.isBlank())
			throw new IllegalStateException("features.recognition.settings.replication.recognizedConnectionNamespace is missing");

		return namespace;
	}
}
