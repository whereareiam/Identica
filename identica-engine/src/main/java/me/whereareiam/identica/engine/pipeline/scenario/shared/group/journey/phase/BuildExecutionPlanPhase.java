package me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.journey.JourneyPlan;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionPlan;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.registry.JourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.AuthenticationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.MigrationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.RegistrationJourneyRegistry;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.shared.JourneyState;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BuildExecutionPlanPhase implements PipelinePhase<JourneyState> {
	private final AuthenticationJourneyRegistry authenticationJourneyRegistry;
	private final RegistrationJourneyRegistry registrationJourneyRegistry;
	private final MigrationJourneyRegistry migrationJourneyRegistry;
	private final Provider<Messages> messagesProvider;

	@Override
	public @NotNull String id() {
		return "build-execution-plan";
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
	public @NotNull CompletionStage<PhaseResult<JourneyState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull JourneyState state
	) {
		if (state.getResult() != null)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		ScenarioContext context = state.getContext();
		PipelineType pipelineType = pipelineState.getPipelineType();
		if (context == null || pipelineType == null) {
			state.setResult(PipelineResult.failed(journeyMissingContextMessage(pipelineState)));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		JourneyRegistry registry = resolveRegistry(pipelineType);
		JourneyPlan basePlan = registry.resolvePlan(context, pipelineType);
		state.setExecutionPlan(JourneyExecutionPlan.from(basePlan));
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	private @NotNull JourneyRegistry resolveRegistry(@NotNull PipelineType pipelineType) {
		if (pipelineType == PipelineType.REGISTRATION)
			return registrationJourneyRegistry;
		if (pipelineType == PipelineType.MIGRATION)
			return migrationJourneyRegistry;

		return authenticationJourneyRegistry;
	}

	private @NotNull String journeyMissingContextMessage(@NotNull PipelineState pipelineState) {
		Messages.Scenarios.Scenario.Errors errors = resolveScenarioErrors(pipelineState);
		return String.join("\n", errors.getJourney().getMissingContext());
	}

	private @NotNull Messages.Scenarios.Scenario.Errors resolveScenarioErrors(@NotNull PipelineState pipelineState) {
		PipelineType pipelineType = pipelineState.getPipelineType();
		Messages.Scenarios scenarios = messagesProvider.get().getScenarios();
		if (pipelineType == PipelineType.REGISTRATION) return scenarios.getRegistration().getErrors();
		if (pipelineType == PipelineType.MIGRATION) return scenarios.getMigration().getErrors();

		return scenarios.getAuthentication().getErrors();
	}
}
