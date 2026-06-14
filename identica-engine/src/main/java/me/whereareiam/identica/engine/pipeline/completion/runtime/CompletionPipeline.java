package me.whereareiam.identica.engine.pipeline.completion.runtime;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.connection.ConnectionLifecycleService;
import me.whereareiam.identica.engine.pipeline.PipelineExecutor;
import me.whereareiam.identica.engine.pipeline.completion.registry.CompletionPipelineRegistry;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.pipeline.GroupOutcome;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.completion.CompletionPendingState;
import me.whereareiam.identica.pipeline.PipelineGroup;
import me.whereareiam.identica.pipeline.completion.CompletionPendingStore;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.completion.CompletionPipelineState;
import org.jetbrains.annotations.NotNull;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CompletionPipeline {
	private final CompletionPendingStore completionPendingStore;
	private final CompletionPipelineRegistry registry;
	private final PipelineExecutor executor;
	private final ConnectionLifecycleService connectionLifecycleService;

	public void complete(@NotNull Identity identity) {
		CompletionPendingState pendingState = completionPendingStore.consume(identity.getUniqueId()).orElse(null);
		if (pendingState == null) return;

		complete(identity, pendingState);
	}

	public void complete(@NotNull Identity identity, @NotNull CompletionPendingState pendingState) {
		CompletionPipelineState completionState = CompletionPipelineState.builder()
				.identity(identity)
				.pendingState(pendingState)
				.pipelineType(pendingState.getPipelineType())
				.build();

		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(pendingState.getPipelineType());
		executor.execute(registry, pipelineState, new PipelineExecutor.ExecutionObserver() {
					@Override
					public @NotNull <S> S initializeState(
							@NotNull PipelineGroup<S> group,
							@NotNull PipelineState pipelineState,
							PipelineResult currentResult
					) {
						if (group.stateType().equals(CompletionPipelineState.class)) {
							@SuppressWarnings("unchecked")
							S state = (S) completionState;
							return state;
						}
						return group.initializeState(pipelineState, currentResult);
					}

					@Override
					public <S> void onGroupCompleted(
							@NotNull PipelineGroup<S> group,
							@NotNull S state,
							@NotNull GroupOutcome outcome,
							PipelineResult currentResult
					) {
					}
				})
				.toCompletableFuture()
				.join();

		if (pendingState.getConnectionUniqueId() != null)
			connectionLifecycleService.completed(
					pendingState.getConnectionUniqueId(),
					pendingState.getAccountUniqueId(),
					pendingState.getPipelineType()
			);
	}
}
