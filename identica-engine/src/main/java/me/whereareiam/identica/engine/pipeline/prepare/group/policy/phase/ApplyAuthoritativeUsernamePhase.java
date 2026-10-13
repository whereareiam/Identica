package me.whereareiam.identica.engine.pipeline.prepare.group.policy.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.pipeline.prepare.PrepareAccountCandidateItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.identity.username.UsernameConflictHandler;
import me.whereareiam.identica.persistence.username.AccountUsernamePersistenceService;
import me.whereareiam.identica.model.config.UsernameMessages;
import me.whereareiam.identica.model.identity.username.UsernameConflictResult;
import me.whereareiam.identica.model.identity.username.UsernameConflictSubject;
import me.whereareiam.identica.engine.pipeline.username.AbstractUsernamePhase;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class ApplyAuthoritativeUsernamePhase extends AbstractUsernamePhase implements PipelinePhase<PrepareGroupState> {
	private final UsernameConflictHandler conflictHandler;
	private final Provider<UsernameMessages> messagesProvider;

	@Inject
	public ApplyAuthoritativeUsernamePhase(
			ProviderManager providerManager,
			AccountUsernamePersistenceService usernamePersistenceService,
			UsernameConflictHandler conflictHandler,
			Provider<UsernameMessages> messagesProvider
	) {
		super(providerManager, usernamePersistenceService);
		this.conflictHandler = conflictHandler;
		this.messagesProvider = messagesProvider;
	}

	@Override
	public @NotNull String id() {
		return "apply-authoritative-username";
	}

	@Override
	public int order() {
		return 100;
	}

	@Override
	public @NotNull Class<PrepareGroupState> stateType() {
		return PrepareGroupState.class;
	}

	@Override
	public boolean supports(@NotNull PipelineState pipelineState, @NotNull PrepareGroupState state) {
		if (pipelineState.has(PrepareDecision.KEY)) return false;

		PrepareAccountCandidateItem candidate = pipelineState.get(PrepareAccountCandidateItem.KEY).orElse(null);
		return candidate != null
				&& candidate.getAccount() != null
				&& candidate.getLink() != null
				&& candidate.getProfile() != null
				&& hasAuthoritativeUsernameTrait(candidate.getLink().getProviderId());
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<PrepareGroupState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull PrepareGroupState state
	) {
		PrepareContextItem context = pipelineState.get(PrepareContextItem.KEY).orElse(new PrepareContextItem());
		PrepareAccountCandidateItem candidate = pipelineState.get(PrepareAccountCandidateItem.KEY).orElse(null);
		if (candidate == null || candidate.getAccount() == null || candidate.getLink() == null || candidate.getProfile() == null) {
			state.setResult(PipelineResult.failed(join(messagesProvider.get().getPipeline().getPrepare().getFailed())));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		if (!hasAuthoritativeUsernameTrait(candidate.getLink().getProviderId()))
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		synchronize(candidate.getAccount(), candidate.getLink(), candidate.getProfile());

		UsernameConflictResult result = conflictHandler.handle(
				UsernameConflictSubject.builder()
						.candidateUsername(candidate.getAccount().getUsername())
						.account(candidate.getAccount())
						.incomingLink(candidate.getLink())
						.incomingProfile(candidate.getProfile())
						.providerContext(context.getProvider())
						.build(),
				candidate.getAccount().getUsername()
		);

		candidate.getAccount().setUsername(result.getEffectiveUsername());
		candidate.setEffectiveUsername(result.getEffectiveUsername());
		pipelineState.put(PrepareAccountCandidateItem.KEY, candidate);
		if (!result.isDenied()) return CompletableFuture.completedFuture(PhaseResult.pass(state));

		pipelineState.put(PrepareDecision.KEY, PrepareDecision.deny(
				result.getDenialMessage(),
				context,
				candidate.getUniqueId(),
				result.getEffectiveUsername()
		));
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	private @NotNull String join(@NotNull java.util.List<String> lines) {
		return String.join("\n", lines);
	}
}
