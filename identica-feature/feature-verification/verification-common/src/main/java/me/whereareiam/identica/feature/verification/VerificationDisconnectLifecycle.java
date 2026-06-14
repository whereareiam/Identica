package me.whereareiam.identica.feature.verification;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import me.whereareiam.identica.feature.verification.challenge.VerificationChallengeStore;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

// TODO Rewrite

@Singleton
public class VerificationDisconnectLifecycle implements EventListener {
	private final VerificationService verificationService;
	private final VerificationChallengeStore challengeStore;

	@Inject
	public VerificationDisconnectLifecycle(
			@NotNull VerificationService verificationService,
			@NotNull VerificationChallengeStore challengeStore,
			@NotNull EventManager eventManager
	) {
		this.verificationService = verificationService;
		this.challengeStore = challengeStore;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onConnectionDisconnected(@NotNull ConnectionDisconnectedEvent event) {
		UUID accountUniqueId = event.getAccountUniqueId();
		if (accountUniqueId == null) return;

		verificationService.cancelPendingEnrollment(accountUniqueId);
		challengeStore.clearByUniqueId(accountUniqueId);
	}
}
