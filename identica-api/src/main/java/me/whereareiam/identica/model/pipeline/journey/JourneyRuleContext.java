package me.whereareiam.identica.model.pipeline.journey;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JourneyRuleContext {
	private @NotNull ScenarioContext context;
	private @NotNull PipelineType pipelineType;
	private @Nullable JourneyStateItem pending;
	private @Nullable PipelineState pipelineState;
}
