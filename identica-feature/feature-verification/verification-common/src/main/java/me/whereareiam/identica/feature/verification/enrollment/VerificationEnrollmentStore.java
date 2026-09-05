package me.whereareiam.identica.feature.verification.enrollment;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.model.enrollment.PendingVerificationEnrollment;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.LocalCache;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class VerificationEnrollmentStore {
	private static final String NAMESPACE = "verification:enrollment";

	private final LocalCache<PendingVerificationEnrollment> cache;
	private final Provider<VerificationSettings> verificationProvider;

	@Inject
	public VerificationEnrollmentStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<VerificationSettings> verificationProvider
	) {
		this.cache = replicationSystem.cache(NAMESPACE).local();
		this.verificationProvider = verificationProvider;
	}

	public void put(@NotNull UUID uniqueId, @NotNull PendingVerificationEnrollment record) {
		cache.put(uniqueId.toString(), record, ttlMs()).join();
	}

	public @NotNull Optional<PendingVerificationEnrollment> peek(@NotNull UUID uniqueId) {
		return cache.get(uniqueId.toString()).join();
	}

	public @NotNull Optional<PendingVerificationEnrollment> consume(@NotNull UUID uniqueId) {
		return cache.consume(uniqueId.toString()).join();
	}

	public boolean clear(@NotNull UUID uniqueId) {
		return consume(uniqueId).isPresent();
	}

	public void clearAll() {
		while (true) {
			var entries = cache.listKeys(1, 100).join().getEntries();
			if (entries.isEmpty()) return;
			for (String key : entries)
				cache.invalidate(key).join();
		}
	}

	private long ttlMs() {
		return verificationProvider.get().enrollmentTtlMillis();
	}
}
