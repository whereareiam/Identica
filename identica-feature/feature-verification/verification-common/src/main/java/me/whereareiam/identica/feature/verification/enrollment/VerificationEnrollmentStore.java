package me.whereareiam.identica.feature.verification.enrollment;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.model.enrollment.PendingVerificationEnrollment;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.LocalCache;
import me.whereareiam.identica.replication.store.base.AbstractDisconnectScopedStore;
import me.whereareiam.identica.replication.store.participant.ConnectionDisconnectedParticipant;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class VerificationEnrollmentStore extends AbstractDisconnectScopedStore {
	private static final String NAMESPACE = "verification:enrollment";

	private final LocalCache<PendingVerificationEnrollment> cache;
	private final Provider<VerificationSettings> verificationProvider;

	@Inject
	public VerificationEnrollmentStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<VerificationSettings> verificationProvider,
			@NotNull Registry<ConnectionDisconnectedParticipant> participants
	) {
		super(replicationSystem, participants);
		this.cache = localCache(NAMESPACE);
		this.verificationProvider = verificationProvider;
	}

	public void put(@NotNull UUID uniqueId, @NotNull PendingVerificationEnrollment record) {
		cache.put(uniqueId, record, ttlMs()).join();
	}

	public @NotNull Optional<PendingVerificationEnrollment> peek(@NotNull UUID uniqueId) {
		return cache.get(uniqueId).join();
	}

	public @NotNull Optional<PendingVerificationEnrollment> consume(@NotNull UUID uniqueId) {
		return cache.consume(uniqueId).join();
	}

	public boolean clear(@NotNull UUID uniqueId) {
		return consume(uniqueId).isPresent();
	}

	@Override
	public void onConnectionDisconnected(@NotNull ConnectionDisconnectedEvent event) {
		UUID uniqueId = event.getAccountUniqueId();
		if (uniqueId == null) return;
		clear(uniqueId);
	}

	private long ttlMs() {
		return verificationProvider.get().enrollmentTtlMillis();
	}
}
