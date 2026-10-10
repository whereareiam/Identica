package me.whereareiam.identica.engine.pipeline.prepare.group.context.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RestorePrepareStatePhase implements PipelinePhase<PrepareGroupState> {
	private final PrepareStateStore prepareStateStore;

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
		if (previous == null || (previous.getHandshake() == null && previous.getClaimedUniqueId() == null))
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		PrepareContextItem context = pipelineState.item(PrepareContextItem.class).orElse(new PrepareContextItem());
		if (previous.getHandshake() != null) context.setHandshake(previous.getHandshake());
		if (context.getClaimedUniqueId() == null) context.setClaimedUniqueId(previous.getClaimedUniqueId());
		pipelineState.putItem(context, 0L);

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}
}
