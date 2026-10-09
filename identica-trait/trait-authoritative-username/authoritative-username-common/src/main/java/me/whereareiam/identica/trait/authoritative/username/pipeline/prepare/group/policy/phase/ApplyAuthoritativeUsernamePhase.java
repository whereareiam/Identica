package me.whereareiam.identica.trait.authoritative.username.pipeline.prepare.group.policy.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.pipeline.prepare.PrepareAccountCandidateItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecisionItem;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.trait.authoritative.username.conflict.resolver.UsernameConflictResolver;
import me.whereareiam.identica.trait.authoritative.username.database.AccountUsernameStatePersistenceService;
import me.whereareiam.identica.trait.authoritative.username.model.AuthoritativeUsernameMessages;
import me.whereareiam.identica.trait.authoritative.username.model.conflict.UsernameConflictResult;
import me.whereareiam.identica.trait.authoritative.username.model.conflict.UsernameConflictSubject;
import me.whereareiam.identica.trait.authoritative.username.pipeline.phase.base.AbstractUsernamePhase;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class ApplyAuthoritativeUsernamePhase extends AbstractUsernamePhase implements PipelinePhase<PrepareGroupState> {
	private final UsernameConflictResolver conflictResolver;
	private final Provider<AuthoritativeUsernameMessages> messagesProvider;

	@Inject
	public ApplyAuthoritativeUsernamePhase(
			ProviderManager providerManager,
			AccountUsernameStatePersistenceService statePersistenceService,
			UsernameConflictResolver conflictResolver,
			Provider<AuthoritativeUsernameMessages> messagesProvider
	) {
		super(providerManager, statePersistenceService);
		this.conflictResolver = conflictResolver;
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
		if (pipelineState.item(PrepareDecisionItem.class).isPresent()) return false;

		PrepareAccountCandidateItem candidate = pipelineState.item(PrepareAccountCandidateItem.class).orElse(null);
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
		PrepareContextItem context = pipelineState.item(PrepareContextItem.class).orElse(new PrepareContextItem());
		PrepareAccountCandidateItem candidate = pipelineState.item(PrepareAccountCandidateItem.class).orElse(null);
		if (candidate == null || candidate.getAccount() == null || candidate.getLink() == null || candidate.getProfile() == null) {
			state.setResult(PipelineResult.failed(join(messagesProvider.get().getPipeline().getPrepare().getFailed())));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		if (!hasAuthoritativeUsernameTrait(candidate.getLink().getProviderId()))
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		synchronize(candidate.getAccount(), candidate.getLink(), candidate.getProfile());

		UsernameConflictResult result = conflictResolver.handle(
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
		pipelineState.putItem(candidate, 0L);
		if (!result.isDenied()) return CompletableFuture.completedFuture(PhaseResult.pass(state));

		pipelineState.putItem(PrepareDecisionItem.deny(
				result.getDenialMessage(),
				context,
				candidate.getUniqueId(),
				result.getEffectiveUsername()
		), 0L);
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	private @NotNull String join(@NotNull java.util.List<String> lines) {
		return String.join("\n", lines);
	}
}
