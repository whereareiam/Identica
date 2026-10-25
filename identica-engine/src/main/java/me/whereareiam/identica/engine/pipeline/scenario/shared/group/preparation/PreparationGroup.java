package me.whereareiam.identica.engine.pipeline.scenario.shared.group.preparation;

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
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.shared.PreparationState;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PreparationGroup implements PipelineGroup<PreparationState> {
	private final Provider<Messages> messagesProvider;
	@Override
	public @NotNull String id() {
		return "preparation";
	}

	@Override
	public int order() {
		return 100;
	}

	@Override
	public @NotNull Class<PreparationState> stateType() {
		return PreparationState.class;
	}

	@Override
	public @NotNull PreparationState initializeState(
			@NotNull PipelineState pipelineState,
			@Nullable PipelineResult currentResult
	) {
		PreparationState state = new PreparationState();
		ScenarioContext context = resolveScenarioContext(pipelineState);
		if (context != null)
			state.setContext(context);
		return state;
	}

	@Override
	public @NotNull GroupOutcome complete(@NotNull PipelineState pipelineState, @NotNull PreparationState state) {
		PipelineResult result = state.getResult();
		if (result != null) {
			return GroupOutcome.forceStop(result);
		}

		ScenarioContext resolved = state.getContext();
		if (resolved == null) {
			return GroupOutcome.forceStop(PipelineResult.failed(preparationMissingContextMessage(pipelineState)));
		}

		pipelineState.setScenario(resolved);
		return GroupOutcome.none();
	}

	private @Nullable ScenarioContext resolveScenarioContext(@NotNull PipelineState pipelineState) {
		PipelineType pipelineType = pipelineState.getPipelineType();
		if (pipelineType == null)
			return null;
		return pipelineState.getScenario(pipelineType);
	}

	private @NotNull PlayerMessage preparationMissingContextMessage(@NotNull PipelineState pipelineState) {
		return ScenarioMessages.of(scenarioOf(pipelineState)).getPreparationMissingContext().create(messagesProvider.get());
	}

	private static @NotNull PipelineType scenarioOf(@NotNull PipelineState pipelineState) {
		return pipelineState.getPipelineType() != null ? pipelineState.getPipelineType() : PipelineType.AUTHENTICATION;
	}
}
