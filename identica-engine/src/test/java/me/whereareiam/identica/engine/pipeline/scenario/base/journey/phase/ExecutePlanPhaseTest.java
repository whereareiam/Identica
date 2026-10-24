package me.whereareiam.identica.engine.pipeline.scenario.base.journey.phase;

import me.whereareiam.identica.common.provider.selection.DefaultProviderSelector;
import me.whereareiam.identica.common.provider.selection.ProviderClaimRanking;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.phase.ExecutePlanPhase;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.journey.JourneyOverrideItem;
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
import me.whereareiam.identica.type.pipeline.journey.JourneyChoice;
import me.whereareiam.identica.type.pipeline.journey.JourneyExecutionPolicy;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
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
		EventManager eventManager = mock(EventManager.class);
		ProviderManager providerManager = mock(ProviderManager.class);
		PipelineStateStore pipelineStateStore = mock(PipelineStateStore.class);
		RoutingCoordinator routingCoordinator = mock(RoutingCoordinator.class);
		when(pipelineStateStore.find(any(me.whereareiam.identica.pipeline.state.PipelineStateReference.class)))
				.thenReturn(Optional.empty());

		ExecutePlanPhase phase = new ExecutePlanPhase(
				eventManager,
				this::settings,
                Messages::new,
				providerManager,
				providerOperations(),
				routingCoordinator,
				new DefaultProviderSelector(new ProviderClaimRanking(providerManager))
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
			return StepResult.failed(message("should not execute"));
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
				.requireCompletion(true)
				.allowFallback(true)
				.build();
		JourneyStage endStage = JourneyStage.builder()
				.id(StageType.END.id())
				.type(StageType.END)
				.order(300)
				.pipelineTypes(EnumSet.of(PipelineType.AUTHENTICATION))
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
		EventManager eventManager = mock(EventManager.class);
		ProviderManager providerManager = mock(ProviderManager.class);
		PipelineStateStore pipelineStateStore = mock(PipelineStateStore.class);
		RoutingCoordinator routingCoordinator = mock(RoutingCoordinator.class);
		when(pipelineStateStore.find(any(PipelineStateReference.class)))
				.thenReturn(Optional.empty());

		ExecutePlanPhase phase = new ExecutePlanPhase(
				eventManager,
				this::settings,
				Messages::new,
				providerManager,
				providerOperations(),
				routingCoordinator,
				new DefaultProviderSelector(new ProviderClaimRanking(providerManager))
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
				.allowFallback(true)
				.build();
		Step askingStep = step("asks", ignored -> StepResult.waiting(message("prompt")));
		JourneyExecutionPlan plan = new JourneyExecutionPlan(List.of(new JourneyExecutionBlock(
				"group-provider",
				JourneyExecutionPolicy.SEQUENTIAL,
				null,
				List.of(new JourneyExecutionStage(providerStage, List.of(journeyStep(StageType.PROVIDER, askingStep, 10))))
		)));

		JourneyState state = new JourneyState();
		state.setContext(currentContext);
		state.setExecutionPlan(plan);

		PhaseResult<JourneyState> result = phase.execute(pipelineState, state).toCompletableFuture().join();
		AuthContext persistedContext = (AuthContext) pipelineState.getScenario(PipelineType.AUTHENTICATION);

		assertEquals(PipelineStatus.WAITING, result.getState().getResult().getStatus());
		assertEquals(connectionUniqueId, persistedContext.getConnectionUniqueId());
		assertEquals(accountUniqueId, persistedContext.getAccountUniqueId());
		assertTrue(pipelineState.has(JourneyStateItem.KEY));
	}

	@DisplayName("A waiting step continues the journey at the earlier step of its stage it names")
	@Test
	void waitingStepResumesAtTheEarlierStepItNames() {
		assertEquals(0, resumedStepIndex("setup"));
	}

	@DisplayName("A waiting step that names no earlier step of its stage continues the journey where it waits")
	@Test
	void waitingStepResumesWhereItWaitsWithoutAnEarlierStep() {
		assertEquals(1, resumedStepIndex("unknown"));
		assertEquals(1, resumedStepIndex("confirm"));
	}

	private int resumedStepIndex(String resumeStep) {
		ProviderManager providerManager = mock(ProviderManager.class);
		ExecutePlanPhase phase = new ExecutePlanPhase(
				mock(EventManager.class),
				this::settings,
				Messages::new,
				providerManager,
				providerOperations(),
				mock(RoutingCoordinator.class),
				new DefaultProviderSelector(new ProviderClaimRanking(providerManager))
		);

		JourneyStage providerStage = JourneyStage.builder()
				.id(StageType.PROVIDER.id())
				.type(StageType.PROVIDER)
				.order(200)
				.pipelineTypes(EnumSet.of(PipelineType.AUTHENTICATION))
				.allowFallback(true)
				.build();
		Step setup = step("setup", StepResult::proceed);
		Step confirm = step("confirm", context -> StepResult.waitingAt(resumeStep, message("Start over.")));
		JourneyExecutionPlan plan = new JourneyExecutionPlan(List.of(new JourneyExecutionBlock(
				"group-provider",
				JourneyExecutionPolicy.SEQUENTIAL,
				null,
				List.of(new JourneyExecutionStage(providerStage, List.of(
						journeyStep(StageType.PROVIDER, setup, 10),
						journeyStep(StageType.PROVIDER, confirm, 20)
				)))
		)));

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
		state.setExecutionPlan(plan);

		PhaseResult<JourneyState> result = phase.execute(pipelineState, state).toCompletableFuture().join();

		assertEquals(PipelineStatus.WAITING, result.getState().getResult().getStatus());
		return pipelineState.get(JourneyStateItem.KEY).orElseThrow().getStepIndex();
	}

	@DisplayName("Provider blocks enrich a missing provider subject from the selected provider resolvers")
	@Test
	void providerBlocksResolveMissingProviderSubject() {
		EventManager eventManager = mock(EventManager.class);
		ProviderManager providerManager = mock(ProviderManager.class);
		PipelineStateStore pipelineStateStore = mock(PipelineStateStore.class);
		RoutingCoordinator routingCoordinator = mock(RoutingCoordinator.class);
		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());

		ExecutePlanPhase phase = new ExecutePlanPhase(
				eventManager,
				this::settings,
				Messages::new,
				providerManager,
				providerOperations(),
				routingCoordinator,
				new DefaultProviderSelector(new ProviderClaimRanking(providerManager))
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
				.build());

		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(PipelineType.AUTHENTICATION);
		pipelineState.setScenario(context);

		JourneyState state = new JourneyState();
		state.setContext(context);
		state.setExecutionPlan(plan);

		PhaseResult<JourneyState> result = phase.execute(pipelineState, state).toCompletableFuture().join();

		assertTrue(subjectResolved.get());
		assertEquals(PipelineStatus.COMPLETE, result.getState().getResult().getStatus());
	}

	@DisplayName("A step that asks while the player logs in keeps the journey waiting, whoever picks the option")
	@Test
	void askingStepKeepsTheJourneyWaiting() {
		for (JourneyChoice choice : JourneyChoice.values()) {
			List<String> executed = new ArrayList<>();
			PipelineResult result = providerFallbackResult(executed, choice, StepResult.waiting(message("prompt")), StepResult.complete(null));

			assertEquals(PipelineStatus.WAITING, result.getStatus(), choice.name());
			assertEquals("prompt", result.getMessage().getText());
			assertEquals(List.of("credential"), executed);
		}
	}

	@DisplayName("A provider's denial ends the journey with that provider's message")
	@Test
	void providerDenialEndsTheJourneyWithItsMessage() {
		PipelineResult result = providerFallbackResult(new ArrayList<>(), StepResult.denied(message("locked-out")));

		assertEquals(PipelineStatus.DENIED, result.getStatus());
		assertEquals("locked-out", result.getMessage().getText());
	}

	@DisplayName("A provider's denial ends the journey in the same way when the denial has no text")
	@Test
	void providerDenialWithoutTextEndsTheJourney() {
		PipelineResult empty = providerFallbackResult(new ArrayList<>(), StepResult.denied(message("")));
		PipelineResult absent = providerFallbackResult(new ArrayList<>(), StepResult.denied(null));

		assertEquals(PipelineStatus.DENIED, empty.getStatus());
		assertEquals("", empty.getMessage().getText());
		assertEquals(PipelineStatus.DENIED, absent.getStatus());
		assertNull(absent.getMessage());
	}

	@DisplayName("A provider's denial is not bypassed by a later provider")
	@Test
	void providerDenialDoesNotFallBackToTheNextProvider() {
		List<String> executed = new ArrayList<>();
		PipelineResult result = providerFallbackResult(executed, StepResult.denied(message("locked-out")), StepResult.complete(null));

		assertEquals(PipelineStatus.DENIED, result.getStatus());
		assertEquals("locked-out", result.getMessage().getText());
		assertEquals(List.of("credential"), executed);
	}

	@DisplayName("A provider that fails is excluded from the selection and falls back to the next provider")
	@Test
	void failedProviderFallsBackToTheNextProvider() {
		List<String> executed = new ArrayList<>();
		PipelineResult result = providerFallbackResult(executed, StepResult.failed(message("")), StepResult.complete(null));

		assertEquals(PipelineStatus.COMPLETE, result.getStatus());
		assertEquals(List.of("credential", "premium", "excluded:credential", "end"), executed);
	}

	@DisplayName("A method that fails falls back to the next method of its provider without excluding the provider")
	@Test
	void failedMethodFallsBackToTheNextMethod() {
		List<String> executed = new ArrayList<>();
		PipelineState pipelineState = PipelineState.initial();
		PipelineResult result = methodFallbackResult(executed, pipelineState, StepResult.failed(message("")), StepResult.complete(null));

		assertEquals(PipelineStatus.COMPLETE, result.getStatus());
		assertEquals(List.of("website:quick", "website:guided", "end"), executed);
		JourneyOverrideItem override = pipelineState.get(JourneyOverrideItem.KEY).orElseThrow();
		assertEquals(List.of("website:quick"), override.getExcludedMethods());
		assertTrue(override.getExcludedProviders().isEmpty());
	}

	@DisplayName("The last method of a provider that fails excludes its provider from the selection")
	@Test
	void lastFailedMethodExcludesItsProvider() {
		List<String> executed = new ArrayList<>();
		PipelineResult result = methodFallbackResult(executed, PipelineState.initial(), StepResult.failed(message("no website account")));

		assertEquals(PipelineStatus.FAILED, result.getStatus());
		assertEquals("no website account", result.getMessage().getText());
		assertEquals(List.of("website:quick"), executed);
	}

	@DisplayName("A provider the player picked without naming a method continues with its first method")
	@Test
	void pickedProviderContinuesWithItsFirstMethod() {
		List<String> executed = new ArrayList<>();
		ExecutePlanPhase phase = new ExecutePlanPhase(
				mock(EventManager.class),
				this::settings,
				Messages::new,
				mock(ProviderManager.class),
				providerOperations(),
				mock(RoutingCoordinator.class),
				new DefaultProviderSelector(new ProviderClaimRanking(mock(ProviderManager.class)))
		);

		AuthContext context = AuthContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(UUID.randomUUID(), "PlayerOne", "127.0.0.1"))
				.intendedServer("auth")
				.build();
		context.setProvider(ProviderContext.of("website", null, "PlayerOne"));
		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(PipelineType.AUTHENTICATION);
		pipelineState.setScenario(context);

		JourneyStage preStage = JourneyStage.builder()
				.id(StageType.PRE.id())
				.type(StageType.PRE)
				.order(100)
				.pipelineTypes(EnumSet.of(PipelineType.AUTHENTICATION))
				.build();
		JourneyStage providerStage = JourneyStage.builder()
				.id(StageType.PROVIDER.id())
				.type(StageType.PROVIDER)
				.order(200)
				.pipelineTypes(EnumSet.of(PipelineType.AUTHENTICATION))
				.allowFallback(true)
				.build();
		Step choice = step("choice", current -> {
			executed.add("choice");
			return StepResult.waiting(message("choose"));
		});
		Step guided = step("guided", current -> {
			executed.add(current.getProvider().getProviderId() + ":" + current.getProvider().getMethod());
			return StepResult.complete(current);
		});
		JourneyState state = new JourneyState();
		state.setContext(context);
		state.setPending(new JourneyStateItem(StageType.PROVIDER.id(), 0));
		state.setExecutionPlan(new JourneyExecutionPlan(List.of(
				new JourneyExecutionBlock("pre", JourneyExecutionPolicy.SEQUENTIAL, null,
						List.of(new JourneyExecutionStage(preStage, List.of(journeyStep(StageType.PRE, choice, 10))))),
				new JourneyExecutionBlock("group-provider", JourneyExecutionPolicy.FALLBACK, "website", "guided",
						List.of(new JourneyExecutionStage(providerStage, List.of(journeyStep(StageType.PROVIDER, guided, 10)))))
		)));

		PipelineResult result = phase.execute(pipelineState, state).toCompletableFuture().join().getState().getResult();

		assertEquals(PipelineStatus.COMPLETE, result.getStatus());
		assertEquals(List.of("website:guided"), executed);
	}

	@DisplayName("A running method tells its steps which method runs")
	@Test
	void runningMethodIsKnownToItsSteps() {
		List<String> executed = new ArrayList<>();
		methodFallbackResult(executed, PipelineState.initial(), StepResult.complete(null));

		assertEquals(List.of("website:quick", "end"), executed);
	}

	private PipelineResult providerFallbackResult(@NotNull List<String> executed, @NotNull StepResult... providerResults) {
		return providerFallbackResult(executed, JourneyChoice.AUTOMATIC, providerResults);
	}

	/**
	 * Runs an authentication plan of one fallback block per provider, credential first and premium second, followed
	 * by an end block, and records which blocks ran.
	 */
	private PipelineResult providerFallbackResult(
			@NotNull List<String> executed,
			@NotNull JourneyChoice choice,
			@NotNull StepResult... providerResults
	) {
		Engine settings = settings();
		settings.getScenarios().getAuthentication().setChoice(choice);

		ExecutePlanPhase phase = new ExecutePlanPhase(
				mock(EventManager.class),
				() -> settings,
				Messages::new,
				mock(ProviderManager.class),
				providerOperations(),
				mock(RoutingCoordinator.class),
				new DefaultProviderSelector(new ProviderClaimRanking(mock(ProviderManager.class)))
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
				.allowFallback(true)
				.build();
		JourneyStage endStage = JourneyStage.builder()
				.id(StageType.END.id())
				.type(StageType.END)
				.order(300)
				.pipelineTypes(EnumSet.of(PipelineType.AUTHENTICATION))
				.allowFallback(true)
				.build();

		List<String> providerIds = List.of("credential", "premium");
		List<JourneyExecutionBlock> blocks = new ArrayList<>();
		for (int index = 0; index < providerResults.length; index++) {
			String providerId = providerIds.get(index);
			StepResult providerResult = providerResults[index];
			Step providerStep = step(providerId, current -> {
				executed.add(providerId);
				current.getSelection().getExcludedProviderIds().forEach(excluded -> executed.add("excluded:" + excluded));
				return providerResult.getStatus() == StepResult.StepStatus.COMPLETE ? StepResult.complete(current) : providerResult;
			});
			blocks.add(new JourneyExecutionBlock(
					"group-provider",
					JourneyExecutionPolicy.FALLBACK,
					providerId,
					List.of(new JourneyExecutionStage(providerStage, List.of(journeyStep(StageType.PROVIDER, providerStep, 10))))
			));
		}
		Step endStep = step("end", current -> {
			executed.add("end");
			return StepResult.proceed(current);
		});
		blocks.add(new JourneyExecutionBlock(
				"group-end",
				JourneyExecutionPolicy.SEQUENTIAL,
				null,
				List.of(new JourneyExecutionStage(endStage, List.of(journeyStep(StageType.END, endStep, 10))))
		));

		JourneyState state = new JourneyState();
		state.setContext(context);
		state.setExecutionPlan(new JourneyExecutionPlan(blocks));

		return phase.execute(pipelineState, state).toCompletableFuture().join().getState().getResult();
	}

	/**
	 * Runs an authentication plan of one fallback block per method of the website provider, quick first and guided
	 * second, followed by an end block, and records which methods ran.
	 */
	private PipelineResult methodFallbackResult(
			@NotNull List<String> executed,
			@NotNull PipelineState pipelineState,
			@NotNull StepResult... methodResults
	) {
		Engine settings = settings();
		ExecutePlanPhase phase = new ExecutePlanPhase(
				mock(EventManager.class),
				() -> settings,
				Messages::new,
				mock(ProviderManager.class),
				providerOperations(),
				mock(RoutingCoordinator.class),
				new DefaultProviderSelector(new ProviderClaimRanking(mock(ProviderManager.class)))
		);

		UUID accountUniqueId = UUID.randomUUID();
		AuthContext context = AuthContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.accountUniqueId(accountUniqueId)
				.identity(new ConnectionIdentity(accountUniqueId, "PlayerOne", "127.0.0.1"))
				.intendedServer("auth")
				.build();
		pipelineState.setPipelineType(PipelineType.AUTHENTICATION);
		pipelineState.setScenario(context);

		JourneyStage providerStage = JourneyStage.builder()
				.id(StageType.PROVIDER.id())
				.type(StageType.PROVIDER)
				.order(200)
				.pipelineTypes(EnumSet.of(PipelineType.AUTHENTICATION))
				.allowFallback(true)
				.build();
		JourneyStage endStage = JourneyStage.builder()
				.id(StageType.END.id())
				.type(StageType.END)
				.order(300)
				.pipelineTypes(EnumSet.of(PipelineType.AUTHENTICATION))
				.allowFallback(true)
				.build();

		List<String> methods = List.of("quick", "guided");
		List<JourneyExecutionBlock> blocks = new ArrayList<>();
		for (int index = 0; index < methodResults.length; index++) {
			String method = methods.get(index);
			StepResult methodResult = methodResults[index];
			Step methodStep = step(method, current -> {
				executed.add(current.getProvider().getProviderId() + ":" + current.getProvider().getMethod());
				return methodResult.getStatus() == StepResult.StepStatus.COMPLETE ? StepResult.complete(current) : methodResult;
			});
			blocks.add(new JourneyExecutionBlock(
					"group-provider",
					JourneyExecutionPolicy.FALLBACK,
					"website",
					method,
					List.of(new JourneyExecutionStage(providerStage, List.of(journeyStep(StageType.PROVIDER, methodStep, 10))))
			));
		}
		Step endStep = step("end", current -> {
			executed.add("end");
			return StepResult.proceed(current);
		});
		blocks.add(new JourneyExecutionBlock(
				"group-end",
				JourneyExecutionPolicy.SEQUENTIAL,
				null,
				List.of(new JourneyExecutionStage(endStage, List.of(journeyStep(StageType.END, endStep, 10))))
		));

		JourneyState state = new JourneyState();
		state.setContext(context);
		state.setExecutionPlan(new JourneyExecutionPlan(blocks));

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
		scenario.setChoice(JourneyChoice.PROMPTED);
	}

	private ProviderOperations providerOperations() {
		return new ProviderOperations() {
			@Override
			public me.whereareiam.identica.model.provider.ResolvedEntrypoint resolveEntrypoint(String host, int port) {
				return null;
			}

			@Override
			public @NotNull List<SubjectResolution> resolveSubjects(@NotNull SubjectResolveContext context) {
				return List.of();
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
					@NotNull PipelineType pipelineType
			) {
				return List.of();
			}

			@Override
			public boolean isEligible(
					@NotNull ScenarioContext context,
					@NotNull me.whereareiam.identica.model.provider.InternalProvider provider,
					@NotNull PipelineType pipelineType
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
				.build();
	}

	private Step step(@NotNull String name, @NotNull StepExecutor executor) {
		return new Step() {
			@Override
			public @NotNull String getName() {
				return name;
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

	private static PlayerMessage message(String text) {
		return PlayerMessage.builder().text(text).build();
	}
}
