package me.whereareiam.identica.engine.pipeline.scenario.shared.group.finalize;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.message.ScenarioMessages;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.GroupOutcome;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.pipeline.PipelineGroup;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.shared.FinalizeState;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FinalizeGroup implements PipelineGroup<FinalizeState> {
	private final Provider<Messages> messagesProvider;
	@Override
	public @NotNull String id() {
		return "finalize";
	}

	@Override
	public int order() {
		return 900;
	}

	@Override
	public @NotNull Class<FinalizeState> stateType() {
		return FinalizeState.class;
	}

	@Override
	public @NotNull FinalizeState initializeState(
			@NotNull PipelineState pipelineState,
			@Nullable PipelineResult currentResult
	) {
		FinalizeState state = new FinalizeState();
		if (currentResult != null) state.setResult(currentResult);
		return state;
	}

	@Override
	public @NotNull GroupOutcome complete(@NotNull PipelineState pipelineState, @NotNull FinalizeState state) {
		PipelineResult result = state.getResult();
		if (result == null) {
			return GroupOutcome.result(PipelineResult.failed(finalizeMissingResultMessage(pipelineState)));
		}

		return GroupOutcome.result(result);
	}

	private @NotNull PlayerMessage finalizeMissingResultMessage(@NotNull PipelineState pipelineState) {
		return ScenarioMessages.of(scenarioOf(pipelineState)).getFinalizeMissingResult().create(messagesProvider.get());
	}

	private static @NotNull PipelineType scenarioOf(@NotNull PipelineState pipelineState) {
		return pipelineState.getPipelineType() != null ? pipelineState.getPipelineType() : PipelineType.AUTHENTICATION;
	}
}
