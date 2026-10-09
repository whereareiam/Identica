package me.whereareiam.identica.provider.credential.sentinel;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelDecision;
import me.whereareiam.identica.provider.credential.CredentialConstants;
import me.whereareiam.identica.provider.credential.event.authentication.AuthenticationAttemptFailedEvent;
import me.whereareiam.identica.provider.credential.event.authentication.AuthenticationAttemptSucceededEvent;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.model.authentication.AuthenticationAttemptContext;
import me.whereareiam.identica.feature.sentinel.SentinelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("Brute Force Sentinel Lifecycle")
class BruteForceSentinelLifecycleTest {
	@DisplayName("Failed authentication records a brute-force attempt and applies the decision")
	@Test
	void failedAuthenticationRecordsAttemptAndAppliesDecision() {
		SentinelService sentinelService = mock(SentinelService.class);
		EventManager eventManager = mock(EventManager.class);
		when(sentinelService.record(eq(CredentialConstants.SENTINEL.BRUTE_FORCE), any()))
				.thenReturn(SentinelDecision.allowed(null, 2, "warning"));

		BruteForceSentinelLifecycle lifecycle = new BruteForceSentinelLifecycle(sentinelService, eventManager);
		AuthenticationAttemptFailedEvent event = new AuthenticationAttemptFailedEvent(context(), null);

		lifecycle.onPasswordAuthenticationFailed(event);

		assertNotNull(event.getDecision());
		assertFalse(event.getDecision().isDeny());
		assertEquals("warning", event.getDecision().getWarningMessage());
		verify(sentinelService).record(eq(CredentialConstants.SENTINEL.BRUTE_FORCE), any(SentinelContext.class));
	}

	@DisplayName("Successful authentication clears a brute-force sentinel state")
	@Test
	void successfulAuthenticationClearsSentinelState() {
		SentinelService sentinelService = mock(SentinelService.class);
		EventManager eventManager = mock(EventManager.class);
		BruteForceSentinelLifecycle lifecycle = new BruteForceSentinelLifecycle(sentinelService, eventManager);

		lifecycle.onPasswordAuthenticationSucceeded(new AuthenticationAttemptSucceededEvent(context()));

		verify(sentinelService).clear(eq(CredentialConstants.SENTINEL.BRUTE_FORCE), any(SentinelContext.class));
	}

	private AuthenticationAttemptContext context() {
		return new AuthenticationAttemptContext(
				CredentialAccount.builder()
						.providerId("credential")
						.providerSubject("player")
						.passwordHash("hash")
						.hashingMethod("bcrypt")
						.build(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"player",
				"127.0.0.1"
		);
	}
}
