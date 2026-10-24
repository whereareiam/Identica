package me.whereareiam.identica.pipeline.state.scenario.shared;

import lombok.Getter;
import lombok.Setter;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionPlan;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.AbstractGroupState;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
public class JourneyState extends AbstractGroupState {
	private @Nullable ScenarioContext context;
	private @Nullable JourneyStateItem pending;
	private @Nullable JourneyExecutionPlan executionPlan;
}
