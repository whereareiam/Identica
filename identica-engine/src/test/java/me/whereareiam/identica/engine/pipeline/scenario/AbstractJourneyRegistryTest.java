package me.whereareiam.identica.engine.pipeline.scenario;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.journey.JourneyPlan;
import me.whereareiam.identica.model.pipeline.journey.ProviderMethod;
import me.whereareiam.identica.model.pipeline.journey.stage.JourneyStage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.JourneyStep;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.AbstractStep;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Abstract Journey Registry")
class AbstractJourneyRegistryTest {
	private static final PipelineType SCENARIO = PipelineType.MIGRATION;

	private final AbstractJourneyRegistry registry = new AbstractJourneyRegistry() {
	};
	private final Step validation = step("validation", 0);
	private final Step lookup = step("lookup", 10);
	private final Step setup = step("setup", 20);
	private ScenarioContext context;

	@BeforeEach
	void registerWebsite() {
		context = MigrationContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(UUID.randomUUID(), "PlayerOne", "127.0.0.1"))
				.targetProviderId("website")
				.build();
		registry.registerStage(JourneyStage.builder()
				.id(StageType.PROVIDER.id())
				.type(StageType.PROVIDER)
				.order(200)
				.pipelineTypes(EnumSet.of(SCENARIO))
				.build());
		registry.registerStep(JourneyStep.builder()
				.stageId(StageType.PROVIDER.id())
				.providerId("website")
				.scenarios(EnumSet.of(SCENARIO))
				.step(validation)
				.build());
		registry.registerMethod(method("quick", lookup));
		registry.registerMethod(method("guided", lookup, setup));
	}

	@DisplayName("A method runs its own steps with those its provider registered without a method, by their order")
	@Test
	void methodRunsItsStepsWithTheProvidersSteps() {
		assertEquals(List.of("validation", "lookup"), stepNames("quick"));
		assertEquals(List.of("validation", "lookup", "setup"), stepNames("guided"));
	}

	@DisplayName("Lists a provider's methods in the order it registered them")
	@Test
	void listsMethodsInRegistrationOrder() {
		assertEquals(List.of("quick", "guided"), registry.methods("website").stream().map(ProviderMethod::getName).toList());
		assertTrue(registry.methods("credential").isEmpty());
	}

	@DisplayName("Removing a method removes its steps and keeps those of the provider")
	@Test
	void removingAMethodKeepsTheProvidersSteps() {
		assertTrue(registry.removeMethod("website", "guided"));

		assertEquals(List.of("quick"), registry.methods("website").stream().map(ProviderMethod::getName).toList());
		assertEquals(List.of("validation"), stepNames("guided"));
		assertEquals(List.of("validation", "lookup"), stepNames("quick"));
	}

	private List<String> stepNames(String method) {
		JourneyPlan plan = registry.resolvePlan(context, SCENARIO, "website", method);

		return plan.providerStages().getFirst().steps().stream()
				.map(step -> step.getStep().getName())
				.toList();
	}

	private ProviderMethod method(String name, Step... steps) {
		return ProviderMethod.builder()
				.providerId("website")
				.name(name)
				.scenario(SCENARIO)
				.steps(List.of(steps))
				.build();
	}

	private static Step step(String name, int order) {
		return new AbstractStep(name) {
			@Override
			public int order() {
				return order;
			}

			@Override
			public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
				return CompletableFuture.completedFuture(StepResult.proceed(context));
			}
		};
	}
}
