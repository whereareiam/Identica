package me.whereareiam.identica.pipeline.journey.step;

import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * One piece of a journey, such as recognising a returning player or asking for a password. A provider groups its
 * steps into {@link me.whereareiam.identica.model.pipeline.journey.ProviderMethod methods}; core and features
 * register steps every provider runs.
 *
 * <p>Every step runs as early as it can, while the player still logs in. A step that needs something from the player
 * returns {@link StepResult#waiting(String)}: the journey then sends the player to the step's server and runs the step
 * again once the player is there and whenever the player answers. A step therefore decides from what it finds on each
 * run, and a run without the player's answer only asks.</p>
 */
public interface Step {
	@NotNull String getName();

	default int order() {
		return 0;
	}

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
