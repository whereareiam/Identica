package me.whereareiam.identica.model.checkpoint;

import lombok.Value;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A step that waits for the player, for which {@link me.whereareiam.identica.checkpoint.Checkpoints} picks where the
 * connection waits.
 */
@Value
public class CheckpointRequest {
	/**
	 * The waiting connection's unique id on the proxy.
	 */
	@NotNull UUID connectionUniqueId;
	/**
	 * The journey's context, such as its provider and the server the player intends to reach.
	 */
	@NotNull ScenarioContext context;
	/**
	 * The scenario the journey belongs to, such as authentication or registration.
	 */
	@NotNull PipelineType pipelineType;
	/**
	 * The stage of the journey the step belongs to.
	 */
	@NotNull StageType stage;
	/**
	 * The step that waits.
	 */
	@NotNull Step step;
	/**
	 * The step's waiting result, whose wait reason tells whether it waits for the player to be online or for input.
	 */
	@NotNull StepResult result;
}
