package me.whereareiam.identica.engine.pipeline.scenario.base.journey.phase;

import me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.phase.ExecutePlanPhase;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionBlock;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionPlan;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionStage;
import me.whereareiam.identica.model.pipeline.journey.stage.JourneyStage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.JourneyStep;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.pipeline.state.scenario.shared.JourneyState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.provider.subject.SubjectResolveContext;
import me.whereareiam.identica.routing.RoutingCoordinator;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyExecutionPolicy;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.JourneyPolicy;
import me.whereareiam.identica.type.pipeline.journey.step.StepWaitReason;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import me.whereareiam.identica.type.pipeline.journey.step.StepContextRequirement;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
		RoutingCoordinator routingCoordinator = mock(RoutingCoordinator.class);
		when(pipelineStateStore.find(any(me.whereareiam.identica.pipeline.state.PipelineStateReference.class)))
				.thenReturn(Optional.empty());

		ExecutePlanPhase phase = new ExecutePlanPhase(
				identityService,
				eventManager,
				this::settings,
                Messages::new,
				providerManager,
				providerOperations(),
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

	@DisplayName("Waiting steps persist the current scenario identity reference")
	@Test
	void waitingStepPersistsCurrentScenarioIdentityReference() {
		IdentityService identityService = mock(IdentityService.class);
		EventManager eventManager = mock(EventManager.class);
		ProviderManager providerManager = mock(ProviderManager.class);
		PipelineStateStore pipelineStateStore = mock(PipelineStateStore.class);
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
				providerOperations(),
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
		Step onlineStep = step("online-wait", StepContextRequirement.ONLINE, StepResult::proceed);
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

	@DisplayName("Provider blocks enrich a missing provider subject from the selected provider resolvers")
	@Test
	void providerBlocksResolveMissingProviderSubject() {
		IdentityService identityService = mock(IdentityService.class);
		EventManager eventManager = mock(EventManager.class);
		ProviderManager providerManager = mock(ProviderManager.class);
		PipelineStateStore pipelineStateStore = mock(PipelineStateStore.class);
		RoutingCoordinator routingCoordinator = mock(RoutingCoordinator.class);
		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());

		ExecutePlanPhase phase = new ExecutePlanPhase(
				identityService,
				eventManager,
				this::settings,
				Messages::new,
				providerManager,
				providerOperations(),
				routingCoordinator
		);

		AtomicBoolean subjectResolved = new AtomicBoolean();
		Step providerStep = step("provider-step", context -> {
			assertNotNull(context.getProvider());
			assertEquals("credential", context.getProvider().getProviderId());
			assertEquals("credential-subject", context.getProvider().getProviderSubject());
			subjectResolved.set(true);
			return StepResult.proceed(context);
		});

		JourneyStage providerStage = JourneyStage.builder()
				.id(StageType.PROVIDER.id())
				.type(StageType.PROVIDER)
				.order(200)
				.pipelineTypes(EnumSet.of(PipelineType.AUTHENTICATION))
				.journeyModes(EnumSet.of(JourneyMode.INTERACTIVE))
				.allowFallback(true)
				.build();

		JourneyExecutionPlan plan = new JourneyExecutionPlan(List.of(new JourneyExecutionBlock(
				"group-provider",
				JourneyExecutionPolicy.SEQUENTIAL,
				"credential",
				List.of(new JourneyExecutionStage(providerStage, List.of(journeyStep(StageType.PROVIDER, providerStep, 10))))
		)));

		AuthContext context = AuthContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(UUID.randomUUID(), "PlayerOne", "127.0.0.1"))
				.intendedServer("auth")
				.build();
		context.setProvider(ProviderContext.builder()
				.providerId("credential")
				.providerUsername("PlayerOne")
				.source(ProviderOrigin.MANUAL)
				.build());

		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(PipelineType.AUTHENTICATION);
		pipelineState.setScenario(context);

		JourneyState state = new JourneyState();
		state.setContext(context);
		state.setJourneyMode(JourneyMode.INTERACTIVE);
		state.setExecutionPlan(plan);

		PhaseResult<JourneyState> result = phase.execute(pipelineState, state).toCompletableFuture().join();

		assertTrue(subjectResolved.get());
		assertEquals(PipelineStatus.COMPLETE, result.getState().getResult().getStatus());
	}

	@DisplayName("A strict seamless journey denies a step that waits for player input")
	@Test
	void strictSeamlessJourneyDeniesAStepWaitingForInput() {
		assertEquals(PipelineStatus.DENIED, seamlessWaitingResult(JourneyPolicy.STRICT, StepWaitReason.INPUT).getStatus());
		assertEquals("interaction-required", seamlessWaitingResult(JourneyPolicy.STRICT, StepWaitReason.INPUT).getMessage());
	}

	@DisplayName("A seamless journey keeps waiting when the mode is only preferred or the step waits for presence")
	@Test
	void seamlessJourneyKeepsWaitingWhenInputIsAllowedOrNotNeeded() {
		assertEquals(PipelineStatus.WAITING, seamlessWaitingResult(JourneyPolicy.PREFER, StepWaitReason.INPUT).getStatus());
		assertEquals(PipelineStatus.WAITING, seamlessWaitingResult(JourneyPolicy.STRICT, StepWaitReason.ONLINE).getStatus());
	}

	@DisplayName("A strict seamless journey reports the refused input when its last provider needed it")
	@Test
	void strictSeamlessJourneyReportsRefusedInputAfterTheLastProvider() {
		PipelineResult result = seamlessWaitingResult(JourneyPolicy.STRICT, StepWaitReason.INPUT, JourneyExecutionPolicy.FALLBACK, "credential");

		assertEquals(PipelineStatus.DENIED, result.getStatus());
		assertEquals("interaction-required", result.getMessage());
	}

	@DisplayName("A strict seamless journey refuses input in the same way when the refusal has no text")
	@Test
	void strictSeamlessJourneyRefusesInputWithoutText() {
		PipelineResult sequential = seamlessWaitingResult(
				JourneyPolicy.STRICT, StepWaitReason.INPUT, JourneyExecutionPolicy.SEQUENTIAL, null, List.of());
		PipelineResult lastProvider = seamlessWaitingResult(
				JourneyPolicy.STRICT, StepWaitReason.INPUT, JourneyExecutionPolicy.FALLBACK, "credential", List.of());

		assertEquals(PipelineStatus.DENIED, sequential.getStatus());
		assertEquals("", sequential.getMessage());
		assertEquals(PipelineStatus.DENIED, lastProvider.getStatus());
		assertEquals("", lastProvider.getMessage());
	}

	private PipelineResult seamlessWaitingResult(@NotNull JourneyPolicy policy, @NotNull StepWaitReason reason) {
		return seamlessWaitingResult(policy, reason, JourneyExecutionPolicy.SEQUENTIAL, null);
	}

	private PipelineResult seamlessWaitingResult(
			@NotNull JourneyPolicy policy,
			@NotNull StepWaitReason reason,
			@NotNull JourneyExecutionPolicy blockPolicy,
			@Nullable String providerId
	) {
		return seamlessWaitingResult(policy, reason, blockPolicy, providerId, List.of("interaction-required"));
	}

	private PipelineResult seamlessWaitingResult(
			@NotNull JourneyPolicy policy,
			@NotNull StepWaitReason reason,
			@NotNull JourneyExecutionPolicy blockPolicy,
			@Nullable String providerId,
			@NotNull List<String> interactionRequired
	) {
		IdentityService identityService = mock(IdentityService.class);
		PipelineStateStore pipelineStateStore = mock(PipelineStateStore.class);
		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());
		when(identityService.findByConnectionUniqueId(any(UUID.class))).thenReturn(Optional.empty());

		Engine settings = settings();
		settings.getScenarios().getAuthentication().setJourneyMode(JourneyMode.SEAMLESS);
		settings.getScenarios().getAuthentication().setJourneyPolicy(policy);
		Messages messages = new Messages();
		Messages.Engine engine = new Messages.Engine();
		Messages.Engine.Journey journey = new Messages.Engine.Journey();
		Messages.Engine.Journey.Step stepMessages = new Messages.Engine.Journey.Step();
		stepMessages.setInteractionRequired(interactionRequired);
		journey.setStep(stepMessages);
		engine.setJourney(journey);
		messages.setEngine(engine);

		ExecutePlanPhase phase = new ExecutePlanPhase(
				identityService,
				mock(EventManager.class),
				() -> settings,
				() -> messages,
				mock(ProviderManager.class),
				providerOperations(),
				mock(RoutingCoordinator.class)
		);

		UUID accountUniqueId = UUID.randomUUID();
		AuthContext context = AuthContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.accountUniqueId(accountUniqueId)
				.identity(new ConnectionIdentity(accountUniqueId, "PlayerOne", "127.0.0.1"))
				.intendedServer("auth")
				.build();
		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(PipelineType.AUTHENTICATION);
		pipelineState.setScenario(context);

		JourneyStage providerStage = JourneyStage.builder()
				.id(StageType.PROVIDER.id())
				.type(StageType.PROVIDER)
				.order(200)
				.pipelineTypes(EnumSet.of(PipelineType.AUTHENTICATION))
				.journeyModes(EnumSet.of(JourneyMode.SEAMLESS))
				.allowFallback(true)
				.build();
		Step waiting = step("waits", ignored -> StepResult.waiting("prompt", reason));
		JourneyStage endStage = JourneyStage.builder()
				.id(StageType.END.id())
				.type(StageType.END)
				.order(300)
				.pipelineTypes(EnumSet.of(PipelineType.AUTHENTICATION))
				.journeyModes(EnumSet.of(JourneyMode.SEAMLESS))
				.allowFallback(true)
				.build();
		JourneyExecutionPlan plan = new JourneyExecutionPlan(List.of(
				new JourneyExecutionBlock(
						"group-provider",
						blockPolicy,
						providerId,
						List.of(new JourneyExecutionStage(providerStage, List.of(journeyStep(StageType.PROVIDER, waiting, 10))))
				),
				new JourneyExecutionBlock(
						"group-end",
						JourneyExecutionPolicy.SEQUENTIAL,
						null,
						List.of(new JourneyExecutionStage(endStage, List.of(journeyStep(StageType.END, step("end", StepResult::proceed), 10))))
				)
		));

		JourneyState state = new JourneyState();
		state.setContext(context);
		state.setJourneyMode(JourneyMode.SEAMLESS);
		state.setExecutionPlan(plan);

		return phase.execute(pipelineState, state).toCompletableFuture().join().getState().getResult();
	}

	private Engine settings() {
		Engine settings = new Engine();
		Engine.Scenarios scenarios = new Engine.Scenarios();
		configureScenario(scenarios.getAuthentication());
		configureScenario(scenarios.getRegistration());
		configureScenario(scenarios.getMigration());
		settings.setScenarios(scenarios);
		return settings;
	}

	private void configureScenario(@NotNull Engine.Scenario scenario) {
		scenario.setPipelineTtl(Duration.ofSeconds(60));
		scenario.setAllowResume(true);
		scenario.setJourneyMode(JourneyMode.INTERACTIVE);
		scenario.setJourneyPolicy(JourneyPolicy.PREFER);
	}

	private ProviderOperations providerOperations() {
		return new ProviderOperations() {
			@Override
			public me.whereareiam.identica.model.provider.ResolvedEntrypoint resolveEntrypoint(String host, int port) {
				return null;
			}

			@Override
			public String displayEntrypoint(String providerId) {
				return null;
			}

			@Override
			public String displayProviderName(String providerId) {
				return null;
			}

			@Override
			public boolean hasEntrypoints(String providerId) {
				return false;
			}

			@Override
			public @NotNull List<me.whereareiam.identica.model.provider.InternalProvider> eligibleProviders(
					@NotNull ScenarioContext context,
					@NotNull JourneyMode journeyMode
			) {
				return List.of();
			}

			@Override
			public boolean isEligible(
					@NotNull ScenarioContext context,
					@NotNull me.whereareiam.identica.model.provider.InternalProvider provider,
					@NotNull JourneyMode journeyMode
			) {
				return false;
			}

			@Override
			public me.whereareiam.identica.model.identity.provider.AccountProviderLink selectPreferredLink(
					@NotNull List<me.whereareiam.identica.model.identity.provider.AccountProviderLink> links
			) {
				return null;
			}

			@Override
			public SubjectResolution discoverSubject(@NotNull SubjectResolveContext context) {
				return null;
			}

			@Override
			public SubjectResolution resolveSelectedSubject(String providerId, @NotNull SubjectResolveContext context) {
				if (!"credential".equalsIgnoreCase(providerId))
					return null;
				return SubjectResolution.builder()
						.providerId("credential")
						.providerSubject("credential-subject")
						.build();
			}
		};
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

}
