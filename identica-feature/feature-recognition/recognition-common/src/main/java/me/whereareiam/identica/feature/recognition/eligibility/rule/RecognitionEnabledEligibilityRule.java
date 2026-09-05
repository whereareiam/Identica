package me.whereareiam.identica.feature.recognition.eligibility.rule;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionFeatures;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionProvidersProvider;
import me.whereareiam.identica.feature.recognition.eligibility.RecognitionEligibilityRule;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityContext;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityRuleDecision;
import me.whereareiam.identica.feature.recognition.type.RecognitionAttemptKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RecognitionEnabledEligibilityRule implements RecognitionEligibilityRule {
	private final Provider<RecognitionSettings> settingsProvider;
	private final RecognitionProvidersProvider providersProvider;

	@Override
	public @NotNull String id() {
		return "recognition-enabled";
	}

	@Override
	public int order() {
		return 0;
	}

	@Override
	public boolean supports(@NotNull RecognitionEligibilityContext context) {
		return context.getAttemptKind() == RecognitionAttemptKind.SESSION_RECOGNITION;
	}

	@Override
	public @NotNull RecognitionEligibilityRuleDecision evaluate(@NotNull RecognitionEligibilityContext context) {
		String providerId = context.getProviderId();
		if (providerId == null || providerId.isBlank())
			return RecognitionEligibilityRuleDecision.block("recognition-provider-missing");
		if (!isRecognitionEnabled(providerId))
			return RecognitionEligibilityRuleDecision.block("recognition-disabled");

		return RecognitionEligibilityRuleDecision.abstain();
	}

	private boolean isRecognitionEnabled(@NotNull String providerId) {
		RecognitionFeatures.Recognition provider = findProviderRecognition(providerId);
		Boolean override = provider != null ? provider.getEnabled() : null;
		if (override != null) return override;

		return settingsProvider.get().isEnabled();
	}

	private @Nullable RecognitionFeatures.Recognition findProviderRecognition(@Nullable String rawId) {
		if (rawId == null || rawId.isBlank()) return null;

		for (Providers.ProviderEntry provider : providersProvider.get().getProviders()) {
			if (provider == null || provider.getId().isBlank()) continue;
			if (!provider.getId().trim().equalsIgnoreCase(rawId.trim())) continue;

			Providers.ProviderEntry.Features features = provider.getFeatures();
			if (!(features instanceof RecognitionFeatures recognitionFeatures))
				return null;

			return recognitionFeatures.getRecognition();
		}

		return null;
	}
}
