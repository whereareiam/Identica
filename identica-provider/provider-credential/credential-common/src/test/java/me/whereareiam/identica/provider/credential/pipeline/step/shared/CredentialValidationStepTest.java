package me.whereareiam.identica.provider.credential.pipeline.step.shared;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.step.StepContextRequirement;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Credential Validation Step")
class CredentialValidationStepTest {
	private final CredentialValidationStep step = new CredentialValidationStep();

	@DisplayName("Decides from the login in every journey")
	@Test
	void decidesFromTheLoginInEveryJourney() {
		assertEquals(EnumSet.allOf(JourneyMode.class), step.journeyModes());
		assertEquals(StepContextRequirement.LOGIN, step.contextRequirement());
	}

	@DisplayName("Continues with a Credential provider context that names its subject")
	@Test
	void continuesWithACredentialSubject() {
		StepResult result = step.execute(context(ProviderContext.of("credential", "credential-subject", "whereareiam"))).join();

		assertEquals(StepResult.StepStatus.CONTINUE, result.getStatus());
	}

	@DisplayName("Fails without a provider context, for another provider or without a subject")
	@Test
	void failsWithoutACredentialSubject() {
		assertEquals(StepResult.StepStatus.FAILED, step.execute(context(null)).join().getStatus());
		assertEquals(StepResult.StepStatus.FAILED,
				step.execute(context(ProviderContext.of("premium", "premium-subject", "whereareiam"))).join().getStatus());
		assertEquals(StepResult.StepStatus.FAILED,
				step.execute(context(ProviderContext.of("credential", " ", "whereareiam"))).join().getStatus());
	}

	private static AuthContext context(@Nullable ProviderContext provider) {
		return AuthContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(UUID.randomUUID(), "whereareiam", "127.0.0.1"))
				.provider(provider)
				.build();
	}
}
