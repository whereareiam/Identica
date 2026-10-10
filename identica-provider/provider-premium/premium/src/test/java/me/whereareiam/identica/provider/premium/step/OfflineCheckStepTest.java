package me.whereareiam.identica.provider.premium.step;

import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.provider.ProviderAttemptStore;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.provider.premium.pipeline.step.shared.OfflineCheckStep;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileSnapshot;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileStore;
import me.whereareiam.identica.util.UniqueIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Offline-Check Step")
class OfflineCheckStepTest {
	@Mock
	private PremiumProfileStore profileStore;
	@Mock
	private ProviderAttemptStore attemptStore;
	@Mock
	private HandshakeStore handshakeStore;

	private OfflineCheckStep step;

	@BeforeEach
	void setUp() {
		step = new OfflineCheckStep(
				this::messages,
				profileStore,
				attemptStore,
				handshakeStore,
				this::settings
		);
	}

	@DisplayName("Keeps the observed premium profile snapshot when the stored session is still offline")
	@Test
	void rejectedOfflineSessionKeepsProfileSnapshot() {
		String username = "whereareiam";
		String ip = "127.0.0.1";
		String offlineSubject = UniqueIdGenerator.offlinePlayerUniqueId(username).toString();

		when(profileStore.find(username)).thenReturn(new PremiumProfileSnapshot(offlineSubject, System.currentTimeMillis()));
		when(attemptStore.hasAttempt("premium", "verify", username, ip)).thenReturn(true);

		StepResult result = step.execute(AuthContext.builder()
						.identity(new ConnectionIdentity(username, ip))
						.build())
				.join();

		assertEquals(StepResult.StepStatus.FAILED, result.getStatus());
		verify(attemptStore).clearAttempt("premium", "verify", username, ip);
		verify(handshakeStore).invalidateInstruction(username, ip);
	}

	private PremiumMessages messages() {
		PremiumMessages.Verification.Authentication authentication = new PremiumMessages.Verification.Authentication();
		authentication.setPrompt(List.of("prompt"));
		authentication.setInvalid("invalid");
		authentication.setRequired("required");
		authentication.setUnavailable("unavailable");

		PremiumMessages.Verification verification = new PremiumMessages.Verification();
		verification.setRejoin(List.of("rejoin"));
		verification.setAuthentication(authentication);

		PremiumMessages messages = new PremiumMessages();
		messages.setVerification(verification);
		return messages;
	}

	private Engine settings() {
		Engine.Behavior behavior = new Engine.Behavior();
		behavior.setHandshakeInstructionTtl(Duration.ofMinutes(10));

		Engine settings = new Engine();
		settings.setBehavior(behavior);
		return settings;
	}
}
