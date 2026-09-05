package me.whereareiam.identica.feature.recognition.eligibility.rule;

import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityContext;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityRuleDecision;
import me.whereareiam.identica.feature.recognition.type.RecognitionAttemptKind;
import me.whereareiam.identica.feature.recognition.type.RecognitionTrigger;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Explicit Selection Recognition Eligibility Rule")
class ExplicitSelectionRecognitionEligibilityRuleTest {
	@DisplayName("Allows explicit manual provider selection")
	@Test
	void allowsExplicitManualProviderSelection() {
		RecognitionEligibilityRuleDecision decision = new ExplicitSelectionRecognitionEligibilityRule().evaluate(
				RecognitionEligibilityContext.builder()
						.providerId("premium")
						.selectedProvider(ProviderContext.of("premium", null, "PlayerOne", ProviderOrigin.MANUAL))
						.attemptKind(RecognitionAttemptKind.PROVIDER_HANDSHAKE_RECOGNITION)
						.trigger(RecognitionTrigger.EXPLICIT_PROVIDER_SELECTION)
						.build()
		);

		assertEquals(RecognitionEligibilityRuleDecision.Status.ALLOW, decision.getStatus());
	}

	@DisplayName("Abstains for automatic provider selection")
	@Test
	void abstainsForAutomaticProviderSelection() {
		RecognitionEligibilityRuleDecision decision = new ExplicitSelectionRecognitionEligibilityRule().evaluate(
				RecognitionEligibilityContext.builder()
						.providerId("premium")
						.selectedProvider(ProviderContext.of("premium", null, "PlayerOne", ProviderOrigin.AUTO))
						.attemptKind(RecognitionAttemptKind.PROVIDER_HANDSHAKE_RECOGNITION)
						.trigger(RecognitionTrigger.AUTOMATIC)
						.build()
		);

		assertEquals(RecognitionEligibilityRuleDecision.Status.ABSTAIN, decision.getStatus());
	}
}
