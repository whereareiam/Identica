package me.whereareiam.identica.feature.recognition.store;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
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
	private final Provider<RecognitionSettings> settingsProvider;
	private final Supplier<Cache<Boolean>> cache;

	@Inject
	public DefaultRecognizedConnectionStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<RecognitionSettings> settingsProvider,
			@NotNull Provider<Replication> replicationProvider
	) {
		this.settingsProvider = settingsProvider;
		this.cache = Suppliers.memoize(() -> createCache(replicationSystem, settingsProvider.get(), replicationProvider.get()));
	}

	@Override
	public void markRecognized(@NotNull UUID connectionUniqueId) {
		cache.get().put(connectionUniqueId.toString(), Boolean.TRUE, settingsProvider.get().windowMillis()).join();
	}

	@Override
	public boolean isRecognized(@NotNull UUID connectionUniqueId) {
		Optional<Boolean> stored = cache.get().get(connectionUniqueId.toString()).join();
		return stored.orElse(false);
	}

	@Override
	public boolean consumeRecognized(@NotNull UUID connectionUniqueId) {
		Optional<Boolean> stored = cache.get().consume(connectionUniqueId.toString()).join();
		return stored.orElse(false);
	}

	/**
	 * Creates the cache on first use, once the recognition settings and the replication document are loaded.
	 */
	private static @NotNull Cache<Boolean> createCache(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull RecognitionSettings settings,
			@NotNull Replication replication
	) {
		long ttlMs = settings.windowMillis();
		String namespace = resolveNamespace(settings);
		return replication.isEnabled()
				? replicationSystem.cache(namespace)
						.defaultTtl(ttlMs)
						.replicated(ReplicationType.identity(Boolean.class))
				: replicationSystem.cache(namespace)
						.defaultTtl(ttlMs)
						.local();
	}

	private static @NotNull String resolveNamespace(@NotNull RecognitionSettings settings) {
		String namespace = settings.getReplication().getRecognizedConnectionNamespace();
		if (namespace.isBlank())
			throw new IllegalStateException("features.recognition.settings.replication.recognizedConnectionNamespace is missing");

		return namespace;
	}
}
