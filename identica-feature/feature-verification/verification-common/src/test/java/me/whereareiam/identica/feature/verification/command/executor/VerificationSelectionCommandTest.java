package me.whereareiam.identica.feature.verification.command.executor;

import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.challenge.VerificationChallengeStore;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.SessionConnection;
import me.whereareiam.identica.model.config.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@code /2fa disable} keeps the method to disable for the account until a code confirms it. A logged-in player has
 * no journey, so it stays out of the pipeline state that journeys own.
 */
@DisplayName("Verification Selection Command")
class VerificationSelectionCommandTest {
	@DisplayName("Keeps the method to disable for the account and asks for the confirming code")
	@Test
	void keepsTheMethodToDisableForTheAccount() {
		UUID accountUniqueId = UUID.randomUUID();
		Identity identity = mock(Identity.class);
		when(identity.getAccountUniqueId()).thenReturn(accountUniqueId);
		when(identity.getUniqueId()).thenReturn(UUID.randomUUID());
		SessionService sessions = mock(SessionService.class);
		when(sessions.findByConnection(any(UUID.class), any(SessionConnection.class)))
				.thenReturn(CompletableFuture.completedFuture(Optional.of(Session.builder().uniqueId(accountUniqueId).providerId("credential").build())));
		VerificationService verification = mock(VerificationService.class);
		VerificationEnrollment enrollment = mock(VerificationEnrollment.class);
		when(enrollment.getMethodId()).thenReturn("totp");
		when(verification.findEnrollments(accountUniqueId)).thenReturn(List.of(enrollment));
		VerificationChallengeStore challenges = mock(VerificationChallengeStore.class);
		VerificationResultRenderer renderer = mock(VerificationResultRenderer.class);
		VerificationMessages messages = new VerificationMessages();
		messages.setCommands(new VerificationMessages.Commands());

		new VerificationSelectionCommand(
				Messages::new,
				() -> messages,
				verification,
				renderer,
				challenges,
				sessions
		).disable(identity, "totp");

		verify(challenges).putPendingDisable(eq(accountUniqueId), argThat(pending -> "totp".equals(pending.getMethodId())));
		verify(verification).resolveVerification(argThat(request -> accountUniqueId.equals(request.getUniqueId())
				&& "credential".equals(request.getProviderId()) && "disable-method".equals(request.getPurpose())));
		verify(renderer).presentDisablePrompt(identity, "totp");
	}
}
