package me.whereareiam.identica.feature.restriction;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.feature.restriction.config.RestrictionSettings;
import me.whereareiam.identica.feature.restriction.type.RestrictionType;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

@Singleton
public class RestrictionActivationStore {
	private final Provider<RestrictionSettings> settingsProvider;
	private final ReplicatedCache<Boolean> cache;

	@Inject
	public RestrictionActivationStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<RestrictionSettings> settingsProvider
	) {
		this.settingsProvider = settingsProvider;
		ReplicationType<Boolean, Boolean> type = ReplicationType.identity(Boolean.class);
		this.cache = replicationSystem.cache(resolveNamespace(settingsProvider))
				.replicated(type);
	}

	public boolean isActive(@NotNull RestrictionType type, @Nullable String providerId) {
		String key = key(type, providerId);
		if (key == null) return false;

		return cache.get(key).join().orElse(false);
	}

	public void enable(@NotNull RestrictionType type, @Nullable String providerId) {
		String key = key(type, providerId);
		if (key == null) return;

		cache.put(key, Boolean.TRUE, settingsProvider.get().toggleTtlMillis()).join();
	}

	public void disable(@NotNull RestrictionType type, @Nullable String providerId) {
		String key = key(type, providerId);
		if (key == null) return;

		cache.invalidate(key).join();
	}

	private @Nullable String key(@NotNull RestrictionType type, @Nullable String providerId) {
		if (providerId == null || providerId.isBlank()) return null;
		return type.getId() + ":" + providerId.trim().toLowerCase(Locale.ROOT);
	}

	private static @NotNull String resolveNamespace(@NotNull Provider<RestrictionSettings> settingsProvider) {
		String namespace = settingsProvider.get().getReplication().getToggleNamespace();
		if (namespace.isBlank()) {
			throw new IllegalStateException("features.restriction.settings.replication.toggleNamespace is missing");
		}

		return namespace;
	}
}
