package me.whereareiam.identica.common.provider;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.provider.ProviderAttemptSnapshot;
import me.whereareiam.identica.provider.ProviderAttemptStore;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

@Singleton
public class DefaultProviderAttemptStore implements ProviderAttemptStore {
	private static final String KEY_PREFIX = "attempt:";

	private final Provider<Providers> providersProvider;
	private final Supplier<ReplicatedCache<ProviderAttemptSnapshot>> cache;

	@Inject
	public DefaultProviderAttemptStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<Replication> replicationProvider,
			@NotNull Provider<Providers> providersProvider
	) {
		this.providersProvider = providersProvider;
		this.cache = Suppliers.memoize(() -> replicationSystem.cache(resolveNamespace(replicationProvider))
				.replicated(ReplicationType.identity(ProviderAttemptSnapshot.class)));
	}

	@Override
	public boolean hasAttempt(
			@NotNull String providerId,
			@NotNull String scope,
			@Nullable String username,
			@Nullable String ip
	) {
		String key = resolveKey(providerId, scope, username, ip);
		if (key == null) return false;

		Optional<ProviderAttemptSnapshot> snapshot = cache.get().get(key).join();
		return snapshot.isPresent();
	}

	@Override
	public void markAttempt(
			@NotNull String providerId,
			@NotNull String scope,
			@Nullable String username,
			@Nullable String ip
	) {
		String key = resolveKey(providerId, scope, username, ip);
		if (key == null) return;

		long ttlMs = providersProvider.get().getBehavior().attemptTtlMillis();
		if (ttlMs <= 0) return;

		cache.get().put(key, new ProviderAttemptSnapshot(System.currentTimeMillis()), ttlMs).join();
	}

	@Override
	public void clearAttempt(
			@NotNull String providerId,
			@NotNull String scope,
			@Nullable String username,
			@Nullable String ip
	) {
		String key = resolveKey(providerId, scope, username, ip);
		if (key == null) return;
		cache.get().invalidate(key).join();
	}

	private @Nullable String resolveKey(
			@NotNull String providerId,
			@NotNull String scope,
			@Nullable String username,
			@Nullable String ip
	) {
		String normalizedProvider = normalize(providerId);
		String normalizedScope = normalize(scope);
		String normalizedUsername = normalize(username);
		String normalizedIp = normalize(ip);
		if (normalizedProvider == null || normalizedScope == null || normalizedUsername == null || normalizedIp == null)
			return null;

		return KEY_PREFIX + normalizedProvider + "|" + normalizedScope + "|" + normalizedUsername + "|" + normalizedIp;
	}

	private @Nullable String normalize(@Nullable String value) {
		if (value == null || value.isBlank())
			return null;
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private static @NotNull String resolveNamespace(Provider<Replication> replicationProvider) {
		Replication replication = replicationProvider.get();
		if (replication == null)
			throw new IllegalStateException("replication is missing");

		String namespace = replication.getCache().getAttempts();
		if (namespace.isBlank())
			throw new IllegalStateException("replication.cache.attempts is missing");

		return namespace;
	}
}
