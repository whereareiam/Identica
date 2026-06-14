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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

@Singleton
public class VerificationChallengeStore {
	private static final String RECORD_NAMESPACE = "verification:challenge:records";
	private static final String ACTIVE_NAMESPACE = "verification:challenge:active";
	private static final String VERIFIED_NAMESPACE = "verification:challenge:verified";
	private static final String INDEX_NAMESPACE = "verification:challenge:index";

	private final LocalCache<PendingVerificationChallenge> records;
	private final LocalCache<String> active;
	private final LocalCache<String> verified;
	private final LocalCache<VerificationChallengeKeyIndex> accountIndex;
	private final Provider<VerificationSettings> verificationProvider;

	@Inject
	public VerificationChallengeStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<VerificationSettings> verificationProvider
	) {
		this.records = replicationSystem.cache(RECORD_NAMESPACE).local();
		this.active = replicationSystem.cache(ACTIVE_NAMESPACE).local();
		this.verified = replicationSystem.cache(VERIFIED_NAMESPACE).local();
		this.accountIndex = replicationSystem.cache(INDEX_NAMESPACE).local();
		this.verificationProvider = verificationProvider;
	}

	public void put(@NotNull PendingVerificationChallenge record) {
		long ttlMs = ttlMs();
		String challengeKey = key(record.getUniqueId(), record.getProviderId(), record.getPurpose());
		records.put(record.getChallengeId(), record, ttlMs).join();
		active.put(challengeKey, record.getChallengeId(), ttlMs).join();
		updateIndex(record.getUniqueId(), ttlMs, index -> index.withActiveKey(challengeKey));
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
		String challengeKey = key(record.getUniqueId(), record.getProviderId(), record.getPurpose());
		records.consume(record.getChallengeId()).join();
		active.consume(challengeKey).join();
		updateIndex(record.getUniqueId(), ttlMs(), index -> index.withoutActiveKey(challengeKey));
	}

	public void markVerified(@NotNull PendingVerificationChallenge record) {
		clear(record);
		long ttlMs = ttlMs();
		String challengeKey = key(record.getUniqueId(), record.getProviderId(), record.getPurpose());
		verified.put(challengeKey, "true", ttlMs).join();
		updateIndex(record.getUniqueId(), ttlMs, index -> index.withVerifiedKey(challengeKey));
	}

	public boolean consumeVerified(@NotNull UUID uniqueId, @Nullable String providerId, @Nullable String purpose) {
		String challengeKey = key(uniqueId, providerId, purpose);
		boolean consumed = verified.consume(challengeKey).join().isPresent();
		if (consumed)
			updateIndex(uniqueId, ttlMs(), index -> index.withoutVerifiedKey(challengeKey));

		return consumed;
	}

	public void clearByUniqueId(@NotNull UUID uniqueId) {
		VerificationChallengeKeyIndex index = accountIndex.consume(indexKey(uniqueId)).join().orElse(null);
		if (index == null) return;

		for (String activeKey : index.activeKeys()) {
			String challengeId = active.consume(activeKey).join().orElse(null);
			if (challengeId != null && !challengeId.isBlank())
				records.invalidate(challengeId).join();
		}

		for (String verifiedKey : index.verifiedKeys())
			verified.invalidate(verifiedKey).join();
	}

	private long ttlMs() {
		return verificationProvider.get().challengeTtlMillis();
	}

	private void updateIndex(
			@NotNull UUID uniqueId,
			long ttlMs,
			@NotNull UnaryOperator<VerificationChallengeKeyIndex> operation
	) {
		VerificationChallengeKeyIndex updated = operation.apply(currentIndex(uniqueId));
		storeIndex(uniqueId, updated, ttlMs);
	}

	private @NotNull VerificationChallengeKeyIndex currentIndex(@NotNull UUID uniqueId) {
		return accountIndex.get(indexKey(uniqueId)).join().orElse(VerificationChallengeKeyIndex.empty());
	}

	private void storeIndex(@NotNull UUID uniqueId, @NotNull VerificationChallengeKeyIndex index, long ttlMs) {
		if (index.isEmpty()) {
			accountIndex.invalidate(indexKey(uniqueId)).join();
			return;
		}

		accountIndex.put(indexKey(uniqueId), index, ttlMs).join();
	}

	private @NotNull String indexKey(@NotNull UUID uniqueId) {
		return uniqueId.toString();
	}

	private @NotNull String key(@NotNull UUID uniqueId, @Nullable String providerId, @Nullable String purpose) {
		return uniqueId + ":" + normalize(providerId) + ":" + normalize(purpose);
	}

	private @NotNull String normalize(@Nullable String value) {
		return value == null || value.isBlank() ? "-" : value.trim().toLowerCase();
	}

	private record VerificationChallengeKeyIndex(
			@NotNull List<String> activeKeys,
			@NotNull List<String> verifiedKeys
	) {
		private VerificationChallengeKeyIndex {
			activeKeys = List.copyOf(activeKeys);
			verifiedKeys = List.copyOf(verifiedKeys);
		}

		private static @NotNull VerificationChallengeKeyIndex empty() {
			return new VerificationChallengeKeyIndex(List.of(), List.of());
		}

		private boolean isEmpty() {
			return activeKeys.isEmpty() && verifiedKeys.isEmpty();
		}

		private @NotNull VerificationChallengeKeyIndex withActiveKey(@NotNull String key) {
			return new VerificationChallengeKeyIndex(add(activeKeys, key), verifiedKeys);
		}

		private @NotNull VerificationChallengeKeyIndex withoutActiveKey(@NotNull String key) {
			return new VerificationChallengeKeyIndex(remove(activeKeys, key), verifiedKeys);
		}

		private @NotNull VerificationChallengeKeyIndex withVerifiedKey(@NotNull String key) {
			return new VerificationChallengeKeyIndex(activeKeys, add(verifiedKeys, key));
		}

		private @NotNull VerificationChallengeKeyIndex withoutVerifiedKey(@NotNull String key) {
			return new VerificationChallengeKeyIndex(activeKeys, remove(verifiedKeys, key));
		}

		private static @NotNull List<String> add(@NotNull List<String> keys, @NotNull String key) {
			if (keys.contains(key)) return keys;

			List<String> updated = new ArrayList<>(keys);
			updated.add(key);
			return updated;
		}

		private static @NotNull List<String> remove(@NotNull List<String> keys, @NotNull String key) {
			if (!keys.contains(key)) return keys;

			List<String> updated = new ArrayList<>(keys);
			updated.removeIf(key::equals);
			return updated;
		}
	}
}
