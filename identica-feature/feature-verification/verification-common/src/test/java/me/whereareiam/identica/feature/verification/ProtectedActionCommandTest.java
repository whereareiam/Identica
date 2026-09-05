package me.whereareiam.identica.feature.verification;

import me.whereareiam.identica.feature.verification.command.ProtectedActionCommand;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProtectedActionCommandTest {
	@Test
	void evaluatesTheCurrentSessionProviderAndRetainsProtectionWhenItsSessionIsMissing() {
		VerificationService verification = mock(VerificationService.class);
		SessionService sessions = mock(SessionService.class);
		UUID id = UUID.randomUUID();
		when(verification.findEnrollments(id)).thenReturn(List.of(mock(VerificationEnrollment.class)));
		when(sessions.findByUniqueId(id)).thenReturn(CompletableFuture.completedFuture(Optional.of(
				Session.builder().uniqueId(id).providerId("source-provider").build())));
		TestCommand command = new TestCommand(verification, sessions);
		assertFalse(command.requires(id));
		when(verification.isEnabledForProvider("source-provider")).thenReturn(true);
		assertTrue(command.requires(id));
		when(sessions.findByUniqueId(id)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
		assertTrue(command.requires(id));
	}

	@Test
	void noEnrollmentNeedsNoStepUp() {
		VerificationService verification = mock(VerificationService.class);
		SessionService sessions = mock(SessionService.class);
		assertFalse(new TestCommand(verification, sessions).requires(UUID.randomUUID()));
		verifyNoInteractions(sessions);
	}

	private static final class TestCommand extends ProtectedActionCommand<Void> {
		private final SessionService sessions;
		private TestCommand(VerificationService verification, SessionService sessions) {
			super(verification);
			this.sessions = sessions;
		}
		boolean requires(UUID id) { return requiresStepUp(id); }
		@Override protected @NotNull SessionService sessionService() { return sessions; }
		@Override protected @Nullable String currentSessionRequiredMessage() { return null; }
	}
}
