package me.whereareiam.identica.engine.pipeline.prepare.group.context.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.selection.ProviderSelector;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Restores, for the profile stage, what the handshake stage of the same connection decided: the handshake, the
 * claimed UUID, the provider selection and the known account.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RestorePrepareStatePhase implements PipelinePhase<PrepareGroupState> {
	private final PrepareStateStore prepareStateStore;
	private final ProviderSelector providerSelector;

	@Override
	public @NotNull String id() {
		return "restore-prepare-state";
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
	public @NotNull CompletionStage<PhaseResult<PrepareGroupState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull PrepareGroupState state
	) {
		if (state.getRequest() == null || !state.getRequest().getStage().includesProfile())
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		String connectionKey = state.getRequest().getConnectionKey();
		if (connectionKey == null || connectionKey.isBlank())
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		PrepareDecision previous = prepareStateStore.peek(connectionKey).orElse(null);
		if (previous == null)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		PrepareContextItem context = pipelineState.get(PrepareContextItem.KEY).orElse(new PrepareContextItem());
		if (previous.getHandshake() != null) context.setHandshake(previous.getHandshake());
		if (context.getClaimedUniqueId() == null) context.setClaimedUniqueId(previous.getClaimedUniqueId());
		if (previous.getSelection() != null) context.setSelection(withoutProof(previous.getSelection()));
		context.setPreferredLink(previous.getPreferredLink());
		context.setJourneyMode(previous.getJourneyMode());
		pipelineState.put(PrepareContextItem.KEY, context);

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	/**
	 * Proof belongs to the connection that gave it; this stage proves again what it can.
	 */
	private @NotNull ProviderSelection withoutProof(@NotNull ProviderSelection selection) {
		return providerSelector.revoke(selection, ProviderClaimOrigin.VERIFIED_SUBJECT);
	}
}
