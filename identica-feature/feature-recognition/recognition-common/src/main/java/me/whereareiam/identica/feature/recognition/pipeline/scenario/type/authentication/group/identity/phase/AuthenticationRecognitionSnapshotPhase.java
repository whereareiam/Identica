package me.whereareiam.identica.feature.recognition.pipeline.scenario.type.authentication.group.identity.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.authentication.IdentityState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.feature.recognition.RecognitionFeatureId;
import me.whereareiam.identica.feature.recognition.model.SessionRecognitionSnapshot;
import me.whereareiam.identica.feature.recognition.store.SessionRecognitionStore;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AuthenticationRecognitionSnapshotPhase implements PipelinePhase<IdentityState> {
	@Inject
	private me.whereareiam.identica.feature.FeatureRegistry features;

	private final SessionRecognitionStore sessionRecognitionStore;
	private final ProviderManager providerManager;

	@Override
	public @NotNull String id() {
		return "recognition-authentication-snapshot";
	}

	@Override
	public int order() {
		return 260;
	}

	@Override
	public @NotNull Class<IdentityState> stateType() {
		return IdentityState.class;
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<IdentityState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull IdentityState state
	) {
		PipelineResult result = state.getResult();
		ProviderContext provider = state.getProvider();
		AccountProviderProfile profile = state.getProfile();
		AuthContext authContext = pipelineState.getScenario(pipelineState.getPipelineType()) instanceof AuthContext context
				? context
				: null;
		if (result == null
				|| result.getStatus() != PipelineStatus.COMPLETE
				|| provider == null
				|| profile == null
				|| authContext == null
				|| !supportsRecognition(provider.getProviderId()))
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		ConnectionIdentity.Origin origin = authContext.getIdentity().getOrigin();
		long now = System.currentTimeMillis();
		sessionRecognitionStore.save(SessionRecognitionSnapshot.builder()
				.providerId(profile.getProviderId())
				.providerSubject(profile.getProviderSubject())
				.providerUsername(profile.getProviderUsername())
				.lastIp(authContext.getIp())
				.lastVirtualHost(origin != null ? origin.getHost() : null)
				.lastVirtualPort(origin != null ? origin.getPort() : null)
				.capturedAt(now)
				.build());
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	private boolean supportsRecognition(String providerId) {
		if (providerId == null || providerId.isBlank())
			return false;

		for (InternalProvider provider : providerManager.getProviders()) {
			if (provider == null || provider.getDescriptor() == null)
				continue;
			if (!providerId.equalsIgnoreCase(provider.getDescriptor().getId()))
				continue;

			return provider.getDescriptor().supportsFeature(RecognitionFeatureId.ID)
					&& features.isEnabled(providerId, RecognitionFeatureId.ID);
		}

		return false;
	}
}
