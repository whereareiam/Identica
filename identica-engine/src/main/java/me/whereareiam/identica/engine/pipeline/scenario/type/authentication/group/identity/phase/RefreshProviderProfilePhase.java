package me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.identity.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.message.ScenarioMessages;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.persistence.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.authentication.IdentityState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RefreshProviderProfilePhase implements PipelinePhase<IdentityState> {
	private final ProviderProfilePersistenceService providerProfilePersistenceService;
	private final Provider<Messages> messagesProvider;

	@Override
	public @NotNull String id() {
		return "refresh-provider-profile";
	}

	@Override
	public int order() {
		return 250;
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
		if (result == null || result.getStatus() != PipelineStatus.COMPLETE)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		ProviderContext provider = state.getProvider();
		if (provider == null
				|| isBlank(provider.getProviderId())
				|| isBlank(provider.getProviderSubject())
				|| isBlank(provider.getProviderUsername())) {
			state.setResult(PipelineResult.failed(identityProfileMissingMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		AccountProviderProfile profile = state.getProfile();
		if (profile == null) {
			profile = AccountProviderProfile.builder()
					.providerId(provider.getProviderId())
					.providerSubject(provider.getProviderSubject())
					.providerUsername(provider.getProviderUsername())
					.build();
		}

		String candidate = provider.getProviderUsername().trim();
		AccountProviderProfile updated = profile.toBuilder()
				.providerUsername(candidate)
				.build();
		AccountProviderProfile stored = providerProfilePersistenceService.upsert(updated);
		state.setProfile(stored);

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	private @NotNull PlayerMessage identityProfileMissingMessage() {
		return ScenarioMessages.of(PipelineType.AUTHENTICATION).getProfileMissing().create(messagesProvider.get());
	}

	private boolean isBlank(@Nullable String value) {
		return value == null || value.isBlank();
	}
}
