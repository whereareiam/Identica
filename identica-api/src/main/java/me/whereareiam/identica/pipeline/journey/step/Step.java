package me.whereareiam.identica.pipeline.journey.step;

import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.step.StepContextRequirement;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface Step {
	@NotNull String getName();

	default int order() {
		return 0;
	}

	@NotNull Set<JourneyMode> journeyModes();

	@NotNull StepContextRequirement contextRequirement();

	@SuppressWarnings("unused")
	default boolean shouldExecute(@NotNull ScenarioContext context) {
		return true;
	}

	@NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context);

	/**
	 * Runs the step within a pipeline run. The pipeline calls this method; a step that keeps nothing between
	 * runs inherits it, and a step that does implements {@link StatefulStep}.
	 *
	 * @param context scenario context of the run
	 * @param state state of the run, which the pipeline loaded and saves after the run
	 * @return step result
	 */
	default @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context, @NotNull PipelineState state) {
		return execute(context);
	}
}
