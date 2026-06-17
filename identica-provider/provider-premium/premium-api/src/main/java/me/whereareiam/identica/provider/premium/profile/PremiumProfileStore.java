package me.whereareiam.identica.provider.premium.profile;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.provider.premium.config.PremiumSettings;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.store.base.AbstractAccountScopedStore;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;
import me.whereareiam.identica.replication.store.scope.AccountScopedStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Locale;

@Singleton
public class PremiumProfileStore extends AbstractAccountScopedStore implements AccountScopedStore {
	private static final String KEY_USERNAME_PREFIX = "u:";

	private final @NotNull Provider<PremiumSettings> settingsProvider;
	private final @NotNull ReplicatedCache<PremiumProfileSnapshot> cache;

	@Inject
	public PremiumProfileStore(
			@NotNull Provider<PremiumSettings> settingsProvider,
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Registry<AccountLifecycleParticipant> participants
	) {
		super(replicationSystem, participants);
		this.settingsProvider = settingsProvider;
		ReplicationType<PremiumProfileSnapshot, PremiumProfileSnapshot> type =
				ReplicationType.identity(PremiumProfileSnapshot.class);
		this.cache = replicatedCache(resolveNamespace(settingsProvider), type);
	}

	public void save(@Nullable String username, @NotNull String profileId) {
		String key = resolveKey(username);
		if (key == null) return;

		long ttlMs = resolveTtlMs(settingsProvider.get().getProfileSnapshotTtl());
		if (ttlMs <= 0) return;

		PremiumProfileSnapshot snapshot = new PremiumProfileSnapshot(profileId, System.currentTimeMillis());
		cache.put(key, snapshot, ttlMs).join();
	}

	public @Nullable PremiumProfileSnapshot find(@Nullable String username) {
		String key = resolveKey(username);
		if (key == null) return null;
		return cache.get(key).join().orElse(null);
	}

	public void clear(@Nullable String username) {
		String key = resolveKey(username);
		if (key == null) return;
		cache.invalidate(key).join();
	}

	@Override
	public void onAccountLifecycle(@NotNull AccountLifecycleEvent event) {
		String username = event.getIdentity().getUsername();
		if (username.isBlank()) return;

		clear(username);
	}

	private @Nullable String resolveKey(@Nullable String username) {
		if (username == null || username.isBlank())
			return null;

		return KEY_USERNAME_PREFIX + username.trim().toLowerCase(Locale.ROOT);
	}

	private boolean isUsable(@Nullable Duration duration) {
		return duration != null
				&& !duration.isZero()
				&& !duration.isNegative();
	}

	private long resolveTtlMs(@Nullable Duration duration) {
		if (!isUsable(duration)) return 0;
		return duration.toMillis();
	}

	private static @NotNull String resolveNamespace(Provider<PremiumSettings> settingsProvider) {
		String namespace = settingsProvider.get()
				.getReplication()
				.getCache()
				.getProfileSnapshot();

		if (namespace.isBlank()) {
			throw new IllegalStateException("premium.settings.replication.cache.profileSnapshot is missing");
		}

		return namespace;
	}
}
