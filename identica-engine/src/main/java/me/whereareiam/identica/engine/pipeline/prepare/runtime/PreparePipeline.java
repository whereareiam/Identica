package me.whereareiam.identica.engine.pipeline.prepare.runtime;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.engine.pipeline.PipelineExecutor;
import me.whereareiam.identica.engine.pipeline.prepare.registry.PreparePipelineRegistry;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.pipeline.GroupOutcome;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.pipeline.PipelineGroup;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Generic connection preparation flow that runs before the scenario pipelines.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PreparePipeline {
	private final PreparePipelineRegistry registry;
	private final PipelineExecutor executor;

	public @NotNull CompletionStage<PrepareDecision> prepare(@Nullable PrepareRequest request) {
		if (request == null) return CompletableFuture.completedFuture(PrepareDecision.allow());

		try {
			Logger.debug("Prepare started stage=%s username=%s key=%s",
					request.getStage(),
					request.getIdentity().getUsername(),
					request.getConnectionKey());

			PipelineState pipelineState = PipelineState.initial();

			return executor.execute(registry, pipelineState, new PipelineExecutor.ExecutionObserver() {
						@Override
						public @NotNull <S> S initializeState(
								@NotNull PipelineGroup<S> group,
								@NotNull PipelineState pipelineState,
								PipelineResult currentResult
						) {
							S state = group.initializeState(pipelineState, currentResult);
							if (state instanceof PrepareGroupState prepareState)
								prepareState.setRequest(request);
							return state;
						}

						@Override
						public <S> void onGroupCompleted(
								@NotNull PipelineGroup<S> group,
								@NotNull S ignoredState,
								@NotNull GroupOutcome outcome,
								PipelineResult currentResult
						) {
						}
					})
					.thenApply(ignored -> pipelineState.get(PrepareDecision.KEY).orElseGet(PrepareDecision::allow))
					.toCompletableFuture();
		} catch (Exception e) {
			Logger.severe(e, "Prepare failed stage=%s username=%s",
					request.getStage(),
					request.getIdentity().getUsername());
			return CompletableFuture.completedFuture(PrepareDecision.deny(null));
		}
	}
}
