package me.whereareiam.identica.feature.verification;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import me.whereareiam.identica.feature.verification.challenge.VerificationChallengeStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Verification Disconnect Lifecycle")
class VerificationDisconnectLifecycleTest {
	@DisplayName("Connection disconnect clears pending enrollment and challenge state")
	@Test
	void connectionDisconnectClearsPendingEnrollmentAndChallengeState() {
		VerificationService verificationService = mock(VerificationService.class);
		VerificationChallengeStore challengeStore = mock(VerificationChallengeStore.class);
		EventManager eventManager = mock(EventManager.class);
		VerificationDisconnectLifecycle lifecycle = new VerificationDisconnectLifecycle(
				verificationService,
				challengeStore,
				eventManager
		);
		UUID connectionUniqueId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();

		lifecycle.onConnectionDisconnected(new ConnectionDisconnectedEvent(connectionUniqueId, accountUniqueId, null));

		verify(verificationService).cancelPendingEnrollment(accountUniqueId);
		verify(challengeStore).clearByUniqueId(accountUniqueId);
	}
}
