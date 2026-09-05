package me.whereareiam.identica.feature.recognition;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionFeatures;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionProvidersProvider;
import me.whereareiam.identica.feature.recognition.eligibility.RecognitionEligibilityService;
import me.whereareiam.identica.feature.recognition.model.SessionRecognitionSnapshot;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityContext;
import me.whereareiam.identica.feature.recognition.store.SessionRecognitionStore;
import me.whereareiam.identica.feature.recognition.type.RecognitionAttemptKind;
import me.whereareiam.identica.feature.recognition.type.RecognitionSignal;
import me.whereareiam.identica.feature.recognition.type.RecognitionTrigger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultSessionRecognitionService implements SessionRecognitionService {
	private final Provider<RecognitionSettings> settingsProvider;
	private final RecognitionProvidersProvider providersProvider;
	private final SessionRecognitionStore sessionRecognitionStore;
	private final RecognitionEligibilityService recognitionEligibilityService;

	@Override
	public boolean matches(
			@Nullable String providerId,
			@Nullable String providerSubject,
			@Nullable String providerUsername,
			@Nullable String ip,
			@Nullable ConnectionIdentity.Origin origin
	) {
		if (isBlank(providerId) || isBlank(providerSubject) || !isRecognitionEnabled(providerId)) return false;
		if (!recognitionEligibilityService.evaluate(RecognitionEligibilityContext.builder()
				.providerId(providerId)
				.providerUsername(providerUsername)
				.clientIp(ip)
				.origin(origin)
				.attemptKind(RecognitionAttemptKind.SESSION_RECOGNITION)
				.trigger(RecognitionTrigger.AUTOMATIC)
				.build()).isAllowed())
			return false;

		Optional<SessionRecognitionSnapshot> storedOptional = sessionRecognitionStore.find(providerId.trim(), providerSubject.trim());
		if (storedOptional.isEmpty()) return false;

		SessionRecognitionSnapshot stored = storedOptional.get();
		for (RecognitionSignal signal : effectiveSignals(providerId)) {
			if (!matchesSignal(signal, stored, providerUsername, ip, origin))
				return false;
		}

		return true;
	}

	private boolean matchesSignal(
			@NotNull RecognitionSignal signal,
			@NotNull SessionRecognitionSnapshot stored,
			@Nullable String providerUsername,
			@Nullable String ip,
			@Nullable ConnectionIdentity.Origin origin
	) {
		return switch (signal) {
			case USERNAME -> matchesText(stored.getProviderUsername(), providerUsername);
			case IP -> matchesText(stored.getLastIp(), ip);
			case VIRTUAL_HOST -> matchesOrigin(stored, origin);
		};
	}

	private boolean matchesOrigin(
			@NotNull SessionRecognitionSnapshot stored,
			@Nullable ConnectionIdentity.Origin origin
	) {
		if (origin == null) return false;
		return matchesText(stored.getLastVirtualHost(), origin.getHost())
				&& equalsNullable(stored.getLastVirtualPort(), origin.getPort());
	}

	private boolean isRecognitionEnabled(@NotNull String providerId) {
		RecognitionFeatures.Recognition provider = findProviderRecognition(providerId);
		Boolean override = provider != null ? provider.getEnabled() : null;
		if (override != null) return override;

		return settingsProvider.get().isEnabled();
	}

	private @NotNull Set<RecognitionSignal> effectiveSignals(@NotNull String providerId) {
		LinkedHashSet<RecognitionSignal> resolved = new LinkedHashSet<>();
		RecognitionFeatures.Recognition provider = findProviderRecognition(providerId);
		List<RecognitionSignal> signals = provider != null ? provider.getSignals() : List.of();

		if (!signals.isEmpty()) {
			resolved.addAll(signals);
			return resolved;
		}

		List<RecognitionSignal> defaults = settingsProvider.get().getDefaultSignals();

		resolved.addAll(defaults);
		return resolved;
	}

	private @Nullable RecognitionFeatures.Recognition findProviderRecognition(@Nullable String rawId) {
		if (isBlank(rawId)) return null;

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

	private boolean matchesText(@Nullable String left, @Nullable String right) {
		if (isBlank(left) || isBlank(right)) return false;
		return left.trim().equalsIgnoreCase(right.trim());
	}

	private boolean equalsNullable(@Nullable Integer left, @Nullable Integer right) {
		return left != null && left.equals(right);
	}

	private boolean isBlank(@Nullable String value) {
		return value == null || value.isBlank();
	}
}
