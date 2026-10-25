package me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.message.ScenarioMessages;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.PlayerMessage;
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
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
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
		JourneyMode journeyMode = state.getJourneyMode();
		if (context == null || pipelineType == null || journeyMode == null) {
			state.setResult(PipelineResult.failed(journeyMissingContextMessage(pipelineState)));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		JourneyRegistry registry = resolveRegistry(pipelineType);
		JourneyPlan basePlan = registry.resolvePlan(context, pipelineType, journeyMode, null);
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

	private @NotNull PlayerMessage journeyMissingContextMessage(@NotNull PipelineState pipelineState) {
		return ScenarioMessages.of(scenarioOf(pipelineState)).getJourneyMissingContext().create(messagesProvider.get());
	}

	private static @NotNull PipelineType scenarioOf(@NotNull PipelineState pipelineState) {
		return pipelineState.getPipelineType() != null ? pipelineState.getPipelineType() : PipelineType.AUTHENTICATION;
	}
}
