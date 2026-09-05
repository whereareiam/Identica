package me.whereareiam.identica.feature.verification;

import me.whereareiam.identica.event.identity.session.SessionClosedEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;

class VerificationSessionLifecycleTest {
	@Test
	void clearsEnrollmentForTheAccountWhoseSessionClosed() {
		VerificationService service = mock(VerificationService.class);
		UUID accountUniqueId = UUID.randomUUID();
		new VerificationSessionLifecycle(service).onSessionClosed(new SessionClosedEvent(accountUniqueId, null));
		verify(service).cancelPendingEnrollment(accountUniqueId);
	}
}
