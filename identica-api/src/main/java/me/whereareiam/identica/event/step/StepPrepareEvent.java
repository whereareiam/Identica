package me.whereareiam.identica.event.step;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.base.SynchronousEvent;
import me.whereareiam.identica.provider.IdenticaProvider;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Event fired before a step executes, after it has been selected.
 */
@Getter
@ToString
@RequiredArgsConstructor
public class StepPrepareEvent implements Event, SynchronousEvent {
	private final @Nullable IdenticaProvider provider;
	private final @NotNull Step step;
	private final @NotNull ScenarioContext context;
	private final @NotNull PipelineType pipelineType;
	private final @NotNull StageType phase;
}
