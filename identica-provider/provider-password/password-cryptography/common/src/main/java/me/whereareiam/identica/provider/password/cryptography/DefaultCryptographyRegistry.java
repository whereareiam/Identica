package me.whereareiam.identica.provider.password.cryptography;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.provider.password.CryptographyRegistry;
import me.whereareiam.identica.provider.password.CryptographyAlgorithm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public class DefaultCryptographyRegistry implements CryptographyRegistry {
	private final ConcurrentMap<String, CryptographyAlgorithm> algorithms = new ConcurrentHashMap<>();

	@Inject
	public DefaultCryptographyRegistry(@Nullable Set<CryptographyAlgorithm> hashers) {
		if (hashers != null) {
			for (CryptographyAlgorithm hasher : hashers) {
				if (hasher == null) continue;
				register(hasher);
			}
		}

		if (algorithms.isEmpty()) Logger.warn("No password password hashers were registered");
	}

	@Override
	public @Nullable CryptographyAlgorithm resolve(@Nullable String id) {
		if (id == null || id.isBlank()) return null;
		return algorithms.get(normalize(id));
	}

	@Override
	public @NotNull Collection<CryptographyAlgorithm> all() {
		return List.copyOf(algorithms.values());
	}

	@Override
	public boolean register(@NotNull CryptographyAlgorithm hasher) {
		String id = normalize(hasher.id());
		if (id.isBlank()) return false;

		CryptographyAlgorithm existing = algorithms.putIfAbsent(id, hasher);
		if (existing != null) {
			Logger.warn("Duplicate password password hasher id %s, keeping first", id);
			return false;
		}
		return true;
	}

	@Override
	public boolean unregister(@NotNull String id) {
		if (id.isBlank()) return false;
		return algorithms.remove(normalize(id)) != null;
	}

	private String normalize(String id) {
		return id.trim().toLowerCase(Locale.ROOT);
	}
}
