package me.whereareiam.identica.feature.recognition.store;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
import me.whereareiam.identica.feature.recognition.model.SessionRecognitionSnapshot;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

@Singleton
public class DefaultSessionRecognitionStore implements SessionRecognitionStore {
	private final ReplicatedCache<SessionRecognitionSnapshot> cache;

	@Inject
	public DefaultSessionRecognitionStore(
			@NotNull Provider<RecognitionSettings> settingsProvider,
			@NotNull Provider<Replication> ignoredReplicationProvider,
			@NotNull ReplicationSystem replicationSystem
	) {
		long ttlMs = settingsProvider.get().validityMillis();
		ReplicationType<SessionRecognitionSnapshot, SessionRecognitionSnapshot> type =
				ReplicationType.identity(SessionRecognitionSnapshot.class);
		this.cache = replicationSystem.cache(resolveNamespace(settingsProvider.get()))
				.defaultTtl(ttlMs)
				.replicated(type);
	}

	@Override
	public @NotNull Optional<SessionRecognitionSnapshot> find(
			@Nullable String providerId,
			@Nullable String providerSubject
	) {
		String key = key(providerId, providerSubject);
		if (key == null) return Optional.empty();

		return cache.get(key).join();
	}

	@Override
	public void save(@Nullable SessionRecognitionSnapshot snapshot) {
		if (snapshot == null) return;

		String key = key(snapshot.getProviderId(), snapshot.getProviderSubject());
		if (key == null) return;

		cache.put(key, snapshot).join();
	}

	@Override
	public void clear(
			@Nullable String providerId,
			@Nullable String providerSubject
	) {
		String key = key(providerId, providerSubject);
		if (key == null)
			return;

		cache.invalidate(key).join();
	}

	private @Nullable String key(
			@Nullable String providerId,
			@Nullable String providerSubject
	) {
		String normalizedProviderId = normalize(providerId);
		String normalizedProviderSubject = normalize(providerSubject);
		if (normalizedProviderId == null || normalizedProviderSubject == null)
			return null;

		return normalizedProviderId + "|" + normalizedProviderSubject;
	}

	private @Nullable String normalize(@Nullable String value) {
		if (value == null || value.isBlank()) return null;
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private static @NotNull String resolveNamespace(@NotNull RecognitionSettings settings) {
		String namespace = settings.getReplication().getSnapshotNamespace();
		if (namespace.isBlank())
			throw new IllegalStateException("features.recognition.settings.replication.snapshotNamespace is missing");

		return namespace;
	}
}
