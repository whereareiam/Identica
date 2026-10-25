package me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.message.ScenarioMessages;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.GroupOutcome;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.pipeline.PipelineGroup;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.shared.JourneyState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JourneyGroup implements PipelineGroup<JourneyState> {
	private final Provider<Messages> messagesProvider;
	@Override
	public @NotNull String id() {
		return "journey";
	}

	@Override
	public int order() {
		return 300;
	}

	@Override
	public @NotNull Class<JourneyState> stateType() {
		return JourneyState.class;
	}

	@Override
	public @NotNull JourneyState initializeState(
			@NotNull PipelineState pipelineState,
			@Nullable PipelineResult currentResult
	) {
		JourneyState state = new JourneyState();
		state.setResult(currentResult);

		return state;
	}

	@Override
	public @NotNull GroupOutcome complete(@NotNull PipelineState pipelineState, @NotNull JourneyState state) {
		PipelineResult result = state.getResult();
		if (result == null) {
			return GroupOutcome.result(PipelineResult.failed(journeyMissingResultMessage(pipelineState)));
		}

		if (result.getStatus() == PipelineStatus.COMPLETE)
			pipelineState.remove(JourneyStateItem.KEY);

		return result.getStatus() == PipelineStatus.COMPLETE
				? GroupOutcome.result(result)
				: GroupOutcome.forceStop(result);
	}

	private @NotNull PlayerMessage journeyMissingResultMessage(@NotNull PipelineState pipelineState) {
		return ScenarioMessages.of(scenarioOf(pipelineState)).getJourneyMissingResult().create(messagesProvider.get());
	}

	private static @NotNull PipelineType scenarioOf(@NotNull PipelineState pipelineState) {
		return pipelineState.getPipelineType() != null ? pipelineState.getPipelineType() : PipelineType.AUTHENTICATION;
	}
}
