package me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.provider.subject.SubjectResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Continues the connection with the provider that won its selection, and the subject that provider has for it.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ResolveProfilePhase implements PipelinePhase<PrepareGroupState> {
	private final ProviderOperations providerOperations;

	@Override
	public @NotNull String id() {
		return "resolve-profile";
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
		return pipelineState.get(PrepareDecision.KEY).isEmpty()
				&& state.getRequest() != null
				&& state.getRequest().getStage().includesProfile();
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<PrepareGroupState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull PrepareGroupState state
	) {
		var request = state.getRequest();
		if (request == null) return CompletableFuture.completedFuture(PhaseResult.pass(state));

		PrepareContextItem context = pipelineState.get(PrepareContextItem.KEY).orElse(new PrepareContextItem());
		String requestedUsername = request.getIdentity().getUsername();
		if (requestedUsername.isBlank()) {
			Logger.warn("Prepare profile missing username key=%s", request.getConnectionKey());
			pipelineState.put(PrepareDecision.KEY, PrepareDecision.allow(context, null, ""));

			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		ProviderClaim winner = context.getSelection().getWinner();
		if (winner == null) {
			Logger.debug("Prepare profile deferred username=%s stage=%s", requestedUsername, request.getStage());
			pipelineState.put(PrepareDecision.KEY, PrepareDecision.allow(context, null, requestedUsername));

			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		String subject = resolveSubject(winner, request.getIdentity());
		context.setProvider(ProviderContext.of(winner.getProviderId(), subject, requestedUsername));
		pipelineState.put(PrepareContextItem.KEY, context);
		Logger.debug("Prepare resolved provider username=%s provider=%s origin=%s subject=%s",
				requestedUsername,
				winner.getProviderId(),
				winner.getOrigin(),
				subject);

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	/**
	 * The subject the winning provider proved, or else the one it resolves for the connection.
	 */
	private @Nullable String resolveSubject(@NotNull ProviderClaim winner, @NotNull ConnectionIdentity identity) {
		if (winner.hasProof()) return winner.getVerifiedSubject();

		SubjectResolution resolution = providerOperations.resolveSelectedSubject(
				winner.getProviderId(),
				SubjectResolveContext.builder()
						.identity(identity)
						.build()
		);

		return resolution != null ? resolution.getProviderSubject() : null;
	}
}
