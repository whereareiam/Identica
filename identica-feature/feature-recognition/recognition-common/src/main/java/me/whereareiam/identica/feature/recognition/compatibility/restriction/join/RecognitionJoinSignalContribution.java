package me.whereareiam.identica.feature.recognition.compatibility.restriction.join;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.feature.recognition.RecognitionFeatureId;
import me.whereareiam.identica.feature.recognition.SessionRecognitionService;
import me.whereareiam.identica.feature.restriction.contribution.ProviderRestrictionSignalContribution;
import me.whereareiam.identica.feature.restriction.join.JoinRestrictionType;
import me.whereareiam.identica.feature.restriction.model.RestrictionEvaluationRequest;
import me.whereareiam.identica.feature.restriction.type.RestrictionSignal;
import me.whereareiam.identica.feature.restriction.type.RestrictionType;
import org.jetbrains.annotations.NotNull;

@Singleton
public class RecognitionJoinSignalContribution implements ProviderRestrictionSignalContribution {
	private static final RestrictionSignal RECOGNIZED = RestrictionSignal.of("recognized");

	private final SessionRecognitionService sessionRecognitionService;

	@Inject
	public RecognitionJoinSignalContribution(SessionRecognitionService sessionRecognitionService) {
		this.sessionRecognitionService = sessionRecognitionService;
	}

	@Override
	public @NotNull String featureId() {
		return RecognitionFeatureId.ID;
	}

	@Override
	public @NotNull RestrictionType restrictionType() {
		return JoinRestrictionType.TYPE;
	}

	@Override
	public @NotNull RestrictionSignal signal() {
		return RECOGNIZED;
	}

	@Override
	public boolean matches(@NotNull RestrictionEvaluationRequest request) {
		String providerId = request.getProviderId();
		String providerSubject = request.getProviderSubject();
		if (providerId == null || providerId.isBlank()) return false;
		if (providerSubject == null || providerSubject.isBlank()) return false;

		return sessionRecognitionService.matches(
				providerId,
				providerSubject,
				request.getProviderUsername(),
				request.getIp(),
				request.getOrigin()
		);
	}
}
