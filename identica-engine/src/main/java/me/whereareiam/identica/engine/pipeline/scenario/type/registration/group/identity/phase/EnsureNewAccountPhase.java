package me.whereareiam.identica.engine.pipeline.scenario.type.registration.group.identity.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.message.AccountMessages;
import me.whereareiam.identica.message.ScenarioMessages;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.ScenarioTransitionItem;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.registration.IdentityState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class EnsureNewAccountPhase implements PipelinePhase<IdentityState> {
	private final AccountPersistenceService accountPersistenceService;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final Provider<Messages> messagesProvider;

	@Override
	public @NotNull String id() {
		return "ensure-new-account";
	}

	@Override
	public int order() {
		return 200;
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

		RegistrationContext context = state.getContext();
		if (context == null || context.getProvider() == null) {
			state.setResult(PipelineResult.failed(ensureNewAccountMissingMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		ProviderContext provider = context.getProvider();
		if (provider.getProviderId() == null || provider.getProviderId().isBlank()
				|| provider.getProviderSubject() == null || provider.getProviderSubject().isBlank()) {
			state.setResult(PipelineResult.failed(ensureNewAccountMissingMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		AccountProviderLink existingLink = providerLinkPersistenceService
				.findBySubject(provider.getProviderId(), provider.getProviderSubject())
				.orElse(null);
		if (existingLink != null) {
			UUID linkedUniqueId = existingLink.getUniqueId();

			pipelineState.setScenario(context);
			context.setAccountUniqueId(linkedUniqueId);
			Account linkedAccount = accountPersistenceService.findByUniqueId(linkedUniqueId).orElse(null);
			if (linkedAccount == null) {
				state.setResult(PipelineResult.denied(accountAlreadyExistsMessage()));
				return CompletableFuture.completedFuture(PhaseResult.pass(state));
			}

			state.setLink(existingLink);
			state.setAccount(linkedAccount);
			state.setResult(PipelineResult.noPending());
			context.setTransition(ScenarioTransitionItem.builder()
					.targetPipeline(PipelineType.AUTHENTICATION)
					.reason("existing-provider-link")
					.mode(ScenarioTransitionItem.TransitionMode.RESTART)
					.journeyPolicy(ScenarioTransitionItem.JourneyPolicy.SKIP)
					.providerPolicy(ScenarioTransitionItem.ProviderPolicy.PRESERVE)
					.consumeOnce(true)
					.build());
			pipelineState.setScenario(context);
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		UUID reservedId = context.getAccountUniqueId();
		if (reservedId != null && accountPersistenceService.findByUniqueId(reservedId).isPresent()) {
			state.setResult(PipelineResult.denied(accountAlreadyExistsMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	private @NotNull PlayerMessage ensureNewAccountMissingMessage() {
		return ScenarioMessages.of(PipelineType.REGISTRATION).getEnsureNewAccountMissing().create(messagesProvider.get());
	}

	private @NotNull PlayerMessage accountAlreadyExistsMessage() {
		return AccountMessages.ALREADY_EXISTS.create(messagesProvider.get());
	}

}
