package me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.phase;

import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.JourneyState;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.pipeline.authentication.AuthenticationOutcomeItem;
import me.whereareiam.identica.model.pipeline.authentication.AuthenticationOutcomeItem.AuthenticationOutcome;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionBlock;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionPlan;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionStage;
import me.whereareiam.identica.model.pipeline.journey.stage.JourneyStage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.JourneyStep;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.pipeline.state.PipelineState;
import me.whereareiam.identica.model.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.routing.RoutingCoordinator;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyExecutionPolicy;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.JourneyPolicy;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import me.whereareiam.identica.type.pipeline.journey.step.StepContextRequirement;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Execute Plan Phase")
class ExecutePlanPhaseTest {
	@DisplayName("Complete step stops the current stage but still allows later stages to run")
	@Test
	void completeStepStopsCurrentStageOnly() {
		IdentityService identityService = mock(IdentityService.class);
		EventManager eventManager = mock(EventManager.class);
		ProviderManager providerManager = mock(ProviderManager.class);
		PipelineStateStore pipelineStateStore = mock(PipelineStateStore.class);
		ProviderLinkPersistenceService providerLinkPersistenceService = mock(ProviderLinkPersistenceService.class);
		RoutingCoordinator routingCoordinator = mock(RoutingCoordinator.class);
		when(pipelineStateStore.find(any(me.whereareiam.identica.model.pipeline.state.PipelineStateReference.class)))
				.thenReturn(Optional.empty());

		ExecutePlanPhase phase = new ExecutePlanPhase(
				identityService,
				eventManager,
				this::settings,
                Messages::new,
				providerManager,
				pipelineStateStore,
				providerLinkPersistenceService,
				routingCoordinator
		);

		AtomicBoolean firstExecuted = new AtomicBoolean();
		AtomicBoolean skippedExecuted = new AtomicBoolean();
		AtomicBoolean laterStageExecuted = new AtomicBoolean();

		Step first = step("first-complete", context -> {
			firstExecuted.set(true);
			return StepResult.complete(context);
		});
		Step shouldSkip = step("should-skip", context -> {
			skippedExecuted.set(true);
			return StepResult.failed("should not execute");
		});
		Step laterStage = step("later-stage", context -> {
			laterStageExecuted.set(true);
			return StepResult.proceed(context);
		});

		JourneyStage providerStage = JourneyStage.builder()
				.id(StageType.PROVIDER.id())
				.type(StageType.PROVIDER)
				.order(200)
				.pipelineTypes(EnumSet.of(PipelineType.AUTHENTICATION))
				.journeyModes(EnumSet.of(JourneyMode.INTERACTIVE))
				.requireCompletion(true)
				.allowFallback(true)
				.build();
		JourneyStage endStage = JourneyStage.builder()
				.id(StageType.END.id())
				.type(StageType.END)
				.order(300)
				.pipelineTypes(EnumSet.of(PipelineType.AUTHENTICATION))
				.journeyModes(EnumSet.of(JourneyMode.INTERACTIVE))
				.allowFallback(true)
				.build();

		JourneyExecutionPlan plan = new JourneyExecutionPlan(List.of(
				new JourneyExecutionBlock(
						"group-provider",
						JourneyExecutionPolicy.SEQUENTIAL,
						null,
						List.of(new JourneyExecutionStage(providerStage, List.of(
								journeyStep(StageType.PROVIDER, first, 10),
								journeyStep(StageType.PROVIDER, shouldSkip, 20)
						)))
				),
				new JourneyExecutionBlock(
						"group-end",
						JourneyExecutionPolicy.SEQUENTIAL,
						null,
						List.of(new JourneyExecutionStage(endStage, List.of(
								journeyStep(StageType.END, laterStage, 10)
						)))
				)
		));

		AuthContext context = AuthContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(UUID.randomUUID(), "PlayerOne", "127.0.0.1"))
				.intendedServer("auth")
				.build();
		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(PipelineType.AUTHENTICATION);
		pipelineState.setScenario(context);

		JourneyState state = new JourneyState();
		state.setContext(context);
		state.setJourneyMode(JourneyMode.INTERACTIVE);
		state.setExecutionPlan(plan);

		PhaseResult<JourneyState> result = phase.execute(pipelineState, state).toCompletableFuture().join();

		assertTrue(firstExecuted.get());
		assertFalse(skippedExecuted.get());
		assertTrue(laterStageExecuted.get());
		assertEquals(PipelineStatus.COMPLETE, result.getState().getResult().getStatus());
		verify(routingCoordinator, atLeastOnce()).accept(any());
	}

	@DisplayName("Completed recognition steps store an authentication outcome item during journey execution")
	@Test
	void recognitionCompletionStoresAuthenticationOutcome() {
		IdentityService identityService = mock(IdentityService.class);
		EventManager eventManager = mock(EventManager.class);
		ProviderManager providerManager = mock(ProviderManager.class);
		PipelineStateStore pipelineStateStore = mock(PipelineStateStore.class);
		ProviderLinkPersistenceService providerLinkPersistenceService = mock(ProviderLinkPersistenceService.class);
		RoutingCoordinator routingCoordinator = mock(RoutingCoordinator.class);
		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());

		ExecutePlanPhase phase = new ExecutePlanPhase(
				identityService,
				eventManager,
				this::settings,
				Messages::new,
				providerManager,
				pipelineStateStore,
				providerLinkPersistenceService,
				routingCoordinator
		);

		Step contributeOutcome = new RecognitionStep();

		JourneyStage providerStage = JourneyStage.builder()
				.id(StageType.PROVIDER.id())
				.type(StageType.PROVIDER)
				.order(200)
				.pipelineTypes(EnumSet.of(PipelineType.AUTHENTICATION))
				.journeyModes(EnumSet.of(JourneyMode.INTERACTIVE))
				.requireCompletion(true)
				.allowFallback(true)
				.build();

		JourneyExecutionPlan plan = new JourneyExecutionPlan(List.of(
				new JourneyExecutionBlock(
						"group-provider",
						JourneyExecutionPolicy.SEQUENTIAL,
						null,
						List.of(new JourneyExecutionStage(providerStage, List.of(
								journeyStep(StageType.PROVIDER, contributeOutcome, 10)
						)))
				)
		));

		AuthContext context = AuthContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(UUID.randomUUID(), "PlayerOne", "127.0.0.1"))
				.intendedServer("auth")
				.build();
		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(PipelineType.AUTHENTICATION);
		pipelineState.setScenario(context);

		JourneyState state = new JourneyState();
		state.setContext(context);
		state.setJourneyMode(JourneyMode.INTERACTIVE);
		state.setExecutionPlan(plan);

		PhaseResult<JourneyState> result = phase.execute(pipelineState, state).toCompletableFuture().join();

		assertEquals(PipelineStatus.COMPLETE, result.getState().getResult().getStatus());
		assertEquals(AuthenticationOutcome.RECOGNIZED,
				pipelineState.item(AuthenticationOutcomeItem.class).orElseThrow().getOutcome());
	}

	@DisplayName("Waiting steps persist the current scenario identity reference")
	@Test
	void waitingStepPersistsCurrentScenarioIdentityReference() {
		IdentityService identityService = mock(IdentityService.class);
		EventManager eventManager = mock(EventManager.class);
		ProviderManager providerManager = mock(ProviderManager.class);
		PipelineStateStore pipelineStateStore = mock(PipelineStateStore.class);
		ProviderLinkPersistenceService providerLinkPersistenceService = mock(ProviderLinkPersistenceService.class);
		RoutingCoordinator routingCoordinator = mock(RoutingCoordinator.class);
		when(pipelineStateStore.find(any(PipelineStateReference.class)))
				.thenReturn(Optional.empty());
		when(identityService.findByConnectionUniqueId(any(UUID.class))).thenReturn(Optional.empty());

		ExecutePlanPhase phase = new ExecutePlanPhase(
				identityService,
				eventManager,
				this::settings,
				Messages::new,
				providerManager,
				pipelineStateStore,
				providerLinkPersistenceService,
				routingCoordinator
		);

		UUID connectionUniqueId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();
		AuthContext staleContext = AuthContext.builder()
				.identity(new ConnectionIdentity(accountUniqueId, "PlayerOne", "127.0.0.1"))
				.intendedServer("auth")
				.build();
		AuthContext currentContext = AuthContext.builder()
				.connectionUniqueId(connectionUniqueId)
				.accountUniqueId(accountUniqueId)
				.identity(new ConnectionIdentity(accountUniqueId, "PlayerOne", "127.0.0.1"))
				.intendedServer("auth")
				.build();
		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(PipelineType.AUTHENTICATION);
		pipelineState.setScenario(staleContext);

		JourneyStage providerStage = JourneyStage.builder()
				.id(StageType.PROVIDER.id())
				.type(StageType.PROVIDER)
				.order(200)
				.pipelineTypes(EnumSet.of(PipelineType.AUTHENTICATION))
				.journeyModes(EnumSet.of(JourneyMode.INTERACTIVE))
				.allowFallback(true)
				.build();
		Step onlineStep = step("online-wait", StepContextRequirement.ONLINE, context -> StepResult.proceed(context));
		JourneyExecutionPlan plan = new JourneyExecutionPlan(List.of(new JourneyExecutionBlock(
				"group-provider",
				JourneyExecutionPolicy.SEQUENTIAL,
				null,
				List.of(new JourneyExecutionStage(providerStage, List.of(journeyStep(StageType.PROVIDER, onlineStep, 10))))
		)));

		JourneyState state = new JourneyState();
		state.setContext(currentContext);
		state.setJourneyMode(JourneyMode.INTERACTIVE);
		state.setExecutionPlan(plan);

		PhaseResult<JourneyState> result = phase.execute(pipelineState, state).toCompletableFuture().join();
		AuthContext persistedContext = (AuthContext) pipelineState.getScenario(PipelineType.AUTHENTICATION);

		assertEquals(PipelineStatus.WAITING, result.getState().getResult().getStatus());
		assertEquals(connectionUniqueId, persistedContext.getConnectionUniqueId());
		assertEquals(accountUniqueId, persistedContext.getAccountUniqueId());
		assertTrue(pipelineState.item(JourneyStateItem.class).isPresent());
	}

	private Settings settings() {
		Settings settings = new Settings();
		Settings.Connection connection = new Settings.Connection();
		Settings.Scenarios scenarios = new Settings.Scenarios();
		configureScenario(scenarios.getAuthentication());
		configureScenario(scenarios.getRegistration());
		configureScenario(scenarios.getMigration());
		connection.setScenarios(scenarios);
		settings.setConnection(connection);
		return settings;
	}

	private void configureScenario(@NotNull Settings.Scenario scenario) {
		scenario.setPipelineTtl(Duration.ofSeconds(60));
		scenario.setAdvanceLockTtl(Duration.ofSeconds(5));
		scenario.setAllowResume(true);
		scenario.setJourneyMode(JourneyMode.INTERACTIVE);
		scenario.setJourneyPolicy(JourneyPolicy.PREFER);
	}

	private JourneyStep journeyStep(@NotNull StageType stageType, @NotNull Step step, int order) {
		return JourneyStep.builder()
				.stageId(stageType.id())
				.step(step)
				.order(order)
				.scenarios(EnumSet.of(PipelineType.AUTHENTICATION))
				.journeyModes(EnumSet.of(JourneyMode.INTERACTIVE))
				.build();
	}

	private Step step(@NotNull String name, @NotNull StepExecutor executor) {
		return step(name, StepContextRequirement.LOGIN, executor);
	}

	private Step step(
			@NotNull String name,
			@NotNull StepContextRequirement contextRequirement,
			@NotNull StepExecutor executor
	) {
		return new Step() {
			@Override
			public @NotNull String getName() {
				return name;
			}

			@Override
			public @NotNull java.util.Set<JourneyMode> journeyModes() {
				return EnumSet.of(JourneyMode.INTERACTIVE);
			}

			@Override
			public @NotNull StepContextRequirement contextRequirement() {
				return contextRequirement;
			}

			@Override
			public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
				return CompletableFuture.completedFuture(executor.execute(context));
			}
		};
	}

	@FunctionalInterface
	private interface StepExecutor {
		@NotNull StepResult execute(@NotNull ScenarioContext context);
	}

	private static final class RecognitionStep implements me.whereareiam.identica.pipeline.journey.step.type.AuthenticationRecognitionStep {
		@Override
		public @NotNull String getName() {
			return "recognize";
		}

		@Override
		public @NotNull java.util.Set<JourneyMode> journeyModes() {
			return EnumSet.of(JourneyMode.INTERACTIVE);
		}

		@Override
		public @NotNull StepContextRequirement contextRequirement() {
			return StepContextRequirement.LOGIN;
		}

		@Override
		public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
			return CompletableFuture.completedFuture(StepResult.complete(context));
		}
	}
}
