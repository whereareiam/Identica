package me.whereareiam.identica.engine.pipeline.scenario.type.registration.group.identity.phase;

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
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.registration.IdentityState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ValidateProviderPhase implements PipelinePhase<IdentityState> {
	private final Provider<Messages> messagesProvider;

	@Override
	public @NotNull String id() {
		return "validate-provider";
	}

	@Override
	public int order() {
		return 100;
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

		RegistrationContext context = resolveContext(pipelineState);
		if (context == null) {
			state.setResult(PipelineResult.failed(providerValidationMissingMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		ProviderContext provider = context.getProvider();
		if (provider == null
				|| isBlank(provider.getProviderId())
				|| isBlank(provider.getProviderSubject())
				|| isBlank(provider.getProviderUsername())) {
			state.setResult(PipelineResult.denied(providerValidationMissingMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		AccountProviderProfile profile = AccountProviderProfile.builder()
				.providerId(provider.getProviderId())
				.providerSubject(provider.getProviderSubject())
				.providerUsername(provider.getProviderUsername())
				.build();

		state.setContext(context);
		state.setProfile(profile);
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	private @NotNull PlayerMessage providerValidationMissingMessage() {
		return ScenarioMessages.of(PipelineType.REGISTRATION).getProviderValidationMissing().create(messagesProvider.get());
	}


	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private RegistrationContext resolveContext(@NotNull PipelineState pipelineState) {
		PipelineType pipelineType = pipelineState.getPipelineType();
		if (pipelineType == null)
			return null;
		return pipelineState.getScenario(pipelineType) instanceof RegistrationContext registrationContext
				? registrationContext
				: null;
	}
}
