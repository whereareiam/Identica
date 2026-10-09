package me.whereareiam.identica.feature.recognition.eligibility.rule;

import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionFeatures;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionProvidersProvider;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityContext;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityRuleDecision;
import me.whereareiam.identica.feature.recognition.type.RecognitionAttemptKind;
import me.whereareiam.identica.feature.recognition.type.RecognitionTrigger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Recognition Enabled Eligibility Rule")
class RecognitionEnabledEligibilityRuleTest {
	@DisplayName("Blocks session recognition when recognition is disabled")
	@Test
	void blocksSessionRecognitionWhenRecognitionIsDisabled() {
		RecognitionSettings settings = settings();
		RecognitionProvidersProvider providersProvider = providersProvider(null);

		RecognitionEligibilityRuleDecision decision = new RecognitionEnabledEligibilityRule(() -> settings, providersProvider)
				.evaluate(context());

		assertEquals(RecognitionEligibilityRuleDecision.Status.BLOCK, decision.getStatus());
	}

	@DisplayName("Provider recognition settings restore recognition when enabled")
	@Test
	void providerRecognitionSettingsRestoreRecognitionWhenEnabled() {
		RecognitionSettings settings = settings();
		RecognitionProvidersProvider providersProvider = providersProvider(true);

		RecognitionEligibilityRuleDecision decision = new RecognitionEnabledEligibilityRule(() -> settings, providersProvider)
				.evaluate(context());

		assertEquals(RecognitionEligibilityRuleDecision.Status.ABSTAIN, decision.getStatus());
	}

	private RecognitionEligibilityContext context() {
		return RecognitionEligibilityContext.builder()
				.providerId("premium")
				.attemptKind(RecognitionAttemptKind.SESSION_RECOGNITION)
				.trigger(RecognitionTrigger.AUTOMATIC)
				.build();
	}

	private RecognitionSettings settings() {
		RecognitionSettings settings = new RecognitionSettings();
		settings.setEnabled(false);
		settings.setValidity(Duration.ofHours(12));
		return settings;
	}

	private RecognitionProvidersProvider providersProvider(Boolean enabledOverride) {
		RecognitionProvidersProvider provider = mock(RecognitionProvidersProvider.class);
		Providers providers = new Providers();
		Providers.ProviderEntry premium = new Providers.ProviderEntry();
		premium.setId("premium");
		if (enabledOverride != null) {
			RecognitionFeatures features = new RecognitionFeatures();
			RecognitionFeatures.Recognition recognition = new RecognitionFeatures.Recognition();
			recognition.setEnabled(enabledOverride);
			features.setRecognition(recognition);
			premium.setFeatures(features);
		}
		providers.setProviders(java.util.List.of(premium));
		when(provider.get()).thenReturn(providers);
		return provider;
	}
}
