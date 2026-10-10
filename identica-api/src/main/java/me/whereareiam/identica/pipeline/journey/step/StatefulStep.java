package me.whereareiam.identica.pipeline.journey.step;

import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * A step that reads or changes the state of its pipeline run, such as input a player gave between two runs.
 *
 * <p>The pipeline owns that state: it loads it before the run and saves it afterwards. A step works on the
 * state it is given and never loads or saves it through the state store, because a store that replicates
 * returns a separate copy, and the pipeline would then save its own copy over the step's changes.</p>
 *
 * <pre>{@code
 * public CompletableFuture<StepResult> execute(ScenarioContext context, PipelineState state) {
 *     Attempt attempt = state.item(Attempt.class).orElse(null);
 *     if (attempt == null) return CompletableFuture.completedFuture(StepResult.waiting(prompt));
 *
 *     state.removeItem(Attempt.class);
 *     return CompletableFuture.completedFuture(StepResult.complete(context));
 * }
 * }</pre>
 */
public interface StatefulStep extends Step {
	/**
	 * Runs the step with the state of its pipeline run.
	 *
	 * @param context scenario context of the run
	 * @param state state of the run; changes are saved by the pipeline
	 * @return step result
	 */
	@Override
	@NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context, @NotNull PipelineState state);

	/**
	 * Unsupported: a stateful step only runs with the state of its pipeline run.
	 *
	 * @param context scenario context
	 * @return never returns
	 */
	@Override
	default @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		throw new UnsupportedOperationException("Step '" + getName() + "' runs with the state of its pipeline run");
	}
}
