package me.whereareiam.identica.feature.verification.challenge;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.feature.verification.model.challenge.PendingVerificationChallenge;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.LocalCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class VerificationChallengeStore {
	private static final String RECORD_NAMESPACE = "verification:challenge:records";
	private static final String ACTIVE_NAMESPACE = "verification:challenge:active";
	private static final String VERIFIED_NAMESPACE = "verification:challenge:verified";

	private final LocalCache<PendingVerificationChallenge> records;
	private final LocalCache<String> active;
	private final LocalCache<String> verified;
	private final Provider<VerificationSettings> verificationProvider;

	@Inject
	public VerificationChallengeStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<VerificationSettings> verificationProvider
	) {
		this.records = replicationSystem.cache(RECORD_NAMESPACE).local();
		this.active = replicationSystem.cache(ACTIVE_NAMESPACE).local();
		this.verified = replicationSystem.cache(VERIFIED_NAMESPACE).local();
		this.verificationProvider = verificationProvider;
	}

	public void put(@NotNull PendingVerificationChallenge record) {
		long ttlMs = ttlMs();
		records.put(record.getChallengeId(), record, ttlMs).join();
		active.put(key(record.getUniqueId(), record.getProviderId(), record.getPurpose()), record.getChallengeId(), ttlMs).join();
	}

	public @NotNull Optional<PendingVerificationChallenge> find(@NotNull String challengeId) {
		return records.get(challengeId).join();
	}

	public @NotNull Optional<PendingVerificationChallenge> findActive(
			@NotNull UUID uniqueId,
			@Nullable String providerId,
			@Nullable String purpose
	) {
		String challengeId = active.get(key(uniqueId, providerId, purpose)).join().orElse(null);
		if (challengeId == null || challengeId.isBlank()) return Optional.empty();
		return find(challengeId);
	}

	public void clear(@NotNull PendingVerificationChallenge record) {
		records.consume(record.getChallengeId()).join();
		active.consume(key(record.getUniqueId(), record.getProviderId(), record.getPurpose())).join();
	}

	public void markVerified(@NotNull PendingVerificationChallenge record) {
		clear(record);
		verified.put(key(record.getUniqueId(), record.getProviderId(), record.getPurpose()), "true", ttlMs()).join();
	}

	public boolean consumeVerified(@NotNull UUID uniqueId, @Nullable String providerId, @Nullable String purpose) {
		return verified.consume(key(uniqueId, providerId, purpose)).join().isPresent();
	}

	public void clearAll() {
		clearCache(records);
		clearCache(active);
		clearCache(verified);
	}

	private void clearCache(LocalCache<?> cache) {
		while (true) {
			var entries = cache.listKeys(1, 100).join().getEntries();
			if (entries.isEmpty()) return;
			for (String key : entries)
				cache.invalidate(key).join();
		}
	}

	private long ttlMs() {
		return verificationProvider.get().challengeTtlMillis();
	}

	private @NotNull String key(@NotNull UUID uniqueId, @Nullable String providerId, @Nullable String purpose) {
		return uniqueId + ":" + normalize(providerId) + ":" + normalize(purpose);
	}

	private @NotNull String normalize(@Nullable String value) {
		return value == null || value.isBlank() ? "-" : value.trim().toLowerCase();
	}
}
