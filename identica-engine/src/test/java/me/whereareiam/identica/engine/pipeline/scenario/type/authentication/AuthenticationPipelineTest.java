package me.whereareiam.identica.engine.pipeline.scenario.type.authentication;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.common.config.defaults.EngineDefaults;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.engine.pipeline.PipelineExecutor;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.GroupOutcome;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.journey.JourneyOverrideItem;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.pipeline.PipelineGroup;
import me.whereareiam.identica.pipeline.PipelineRegistry;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.pipeline.state.StateKey;
import me.whereareiam.identica.pipeline.state.StoredPipelineState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.util.EventUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Authentication Pipeline")
class AuthenticationPipelineTest {
	@DisplayName("Fails to build a context when the request has no account UUID")
	@Test
	void buildContextRequiresPreparedAccountUniqueId() {
		TestAuthenticationPipeline pipeline = new TestAuthenticationPipeline();

		ScenarioContext context = pipeline.buildFor(ConnectionRequest.builder()
				.identity(new me.whereareiam.identica.identity.actor.ConnectionIdentity("PlayerOne", "127.0.0.1"))
				.build());

		assertNull(context);
	}

	@DisplayName("An advance applies the request's input to the state the run uses")
	@Test
	void advanceAppliesTheInputToTheStateOfTheRun() {
		EventUtil.initialize(mock(EventManager.class));
		UUID connectionId = UUID.randomUUID();
		PipelineState stored = PipelineState.initial();
		stored.setPipelineType(PipelineType.AUTHENTICATION);
		stored.setScenario(AuthContext.builder()
				.connectionUniqueId(connectionId)
				.accountUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(connectionId, "PlayerOne", "127.0.0.1"))
				.build());
		stored.put(JourneyStateItem.KEY, new JourneyStateItem(null, null, 0), 60_000L);

		PipelineStateStore store = mock(PipelineStateStore.class);
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.of(stored));
		PipelineExecutor executor = mock(PipelineExecutor.class);
		ArgumentCaptor<PipelineState> run = ArgumentCaptor.forClass(PipelineState.class);
		when(executor.execute(any(), run.capture(), any())).thenReturn(CompletableFuture.completedFuture(null));
		TestAuthenticationPipeline pipeline = new TestAuthenticationPipeline(store, executor);

		pipeline.executeAdvance(ResumeRequest.builder()
				.connectionUniqueId(connectionId)
				.input(state -> state.put(JourneyOverrideItem.KEY, chosenStage(), 60_000L))
				.build()).toCompletableFuture().join();

		assertSame(stored, run.getValue(), "the run works on the state the pipeline loaded");
		assertEquals("chosen", run.getValue().get(JourneyOverrideItem.KEY).orElseThrow().getStageId());
	}

	@DisplayName("A run waiting for the player never saves the values of run keys it was given")
	@Test
	void waitingRunDoesNotSaveRunValues() {
		EventUtil.initialize(mock(EventManager.class));
		UUID connectionId = UUID.randomUUID();
		PipelineState stored = PipelineState.initial();
		stored.setPipelineType(PipelineType.AUTHENTICATION);
		stored.setScenario(AuthContext.builder()
				.connectionUniqueId(connectionId)
				.accountUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(connectionId, "PlayerOne", "127.0.0.1"))
				.build());
		stored.put(JourneyStateItem.KEY, new JourneyStateItem(null, null, 0), 60_000L);

		PipelineStateStore store = mock(PipelineStateStore.class);
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.of(stored));
		when(store.save(any(PipelineStateReference.class), any(PipelineState.class), anyLong())).thenReturn(true);
		PipelineExecutor executor = mock(PipelineExecutor.class);
		// No step takes the input, as when the step waiting for the player expects another kind of input.
		when(executor.execute(any(), any(), any())).thenAnswer(invocation -> {
			PipelineExecutor.ExecutionObserver observer = invocation.getArgument(2);
			observer.onGroupCompleted(mock(PipelineGroup.class), new Object(),
					GroupOutcome.result(PipelineResult.waiting("prompt")), null);
			return CompletableFuture.completedFuture(null);
		});
		TestAuthenticationPipeline pipeline = new TestAuthenticationPipeline(store, executor);

		PipelineResult result = pipeline.executeAdvance(ResumeRequest.builder()
				.connectionUniqueId(connectionId)
				.input(state -> {
					state.put(TypedPassword.KEY, new TypedPassword("Secret-123"), 60_000L);
					state.put(JourneyOverrideItem.KEY, chosenStage(), 60_000L);
				})
				.build()).toCompletableFuture().join();

		ArgumentCaptor<PipelineState> saved = ArgumentCaptor.forClass(PipelineState.class);
		verify(store).save(any(PipelineStateReference.class), saved.capture(), anyLong());
		assertEquals(PipelineStatus.WAITING, result.getStatus());
		assertTrue(saved.getValue().get(TypedPassword.KEY).isEmpty(), "the typed password is saved");
		assertTrue(saved.getValue().store().getValues().values().stream()
				.noneMatch(value -> value.getPayload().contains("Secret-123")));
		assertEquals("chosen", saved.getValue().get(JourneyOverrideItem.KEY).orElseThrow().getStageId(),
				"other input is saved with the state");
		assertTrue(stored.get(TypedPassword.KEY).isEmpty(), "the run's state keeps the typed password");
	}

	@DisplayName("A run discards a saved state when a step cannot read a value back")
	@Test
	void runDiscardsAnUnreadableState() {
		EventUtil.initialize(mock(EventManager.class));
		UUID connectionId = UUID.randomUUID();
		PipelineState saved = PipelineState.initial();
		saved.setPipelineType(PipelineType.AUTHENTICATION);
		saved.setScenario(AuthContext.builder()
				.connectionUniqueId(connectionId)
				.accountUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(connectionId, "PlayerOne", "127.0.0.1"))
				.build());
		StoredPipelineState stored = saved.store();
		stored.getValues().put(JourneyStateItem.KEY.id(), new StoredPipelineState.StoredValue("{\"stepIndex\": \"first\"}", 0L));

		PipelineStateStore store = mock(PipelineStateStore.class);
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.of(PipelineState.restore(stored)));
		PipelineExecutor executor = mock(PipelineExecutor.class);
		when(executor.execute(any(), any(), any())).thenAnswer(invocation -> {
			PipelineState state = invocation.getArgument(1);
			state.get(JourneyStateItem.KEY);
			return CompletableFuture.completedFuture(null);
		});
		TestAuthenticationPipeline pipeline = new TestAuthenticationPipeline(store, executor);

		PipelineResult result = pipeline.executeAdvance(ResumeRequest.builder()
				.connectionUniqueId(connectionId)
				.build()).toCompletableFuture().join();

		assertEquals(PipelineStatus.FAILED, result.getStatus());
		verify(store).clear(any(PipelineStateReference.class));
		verify(store, never()).save(any(), any(), anyLong());
	}

	@DisplayName("A fresh run replaces only the state that was stored when it started")
	@Test
	void freshRunSavesOverTheStateItStartedOver() {
		EventUtil.initialize(mock(EventManager.class));
		PipelineState leftover = PipelineState.initial();
		leftover.setRevision("leftover");
		PipelineStateStore store = mock(PipelineStateStore.class);
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.of(leftover));
		when(store.save(any(PipelineStateReference.class), any(PipelineState.class), anyLong())).thenReturn(true);
		TestAuthenticationPipeline pipeline = new TestAuthenticationPipeline(store, waitingExecutor());

		PipelineResult result = pipeline.execute(freshRequest(), null).toCompletableFuture().join();

		ArgumentCaptor<PipelineState> saved = ArgumentCaptor.forClass(PipelineState.class);
		verify(store).save(any(PipelineStateReference.class), saved.capture(), anyLong());
		assertEquals(PipelineStatus.WAITING, result.getStatus());
		assertEquals("leftover", saved.getValue().getRevision());
	}

	@DisplayName("A fresh run that found no stored state saves only while there still is none")
	@Test
	void freshRunCreatesItsState() {
		EventUtil.initialize(mock(EventManager.class));
		PipelineStateStore store = mock(PipelineStateStore.class);
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());
		when(store.save(any(PipelineStateReference.class), any(PipelineState.class), anyLong())).thenReturn(true);
		TestAuthenticationPipeline pipeline = new TestAuthenticationPipeline(store, waitingExecutor());

		pipeline.execute(freshRequest(), null).toCompletableFuture().join();

		ArgumentCaptor<PipelineState> saved = ArgumentCaptor.forClass(PipelineState.class);
		verify(store).save(any(PipelineStateReference.class), saved.capture(), anyLong());
		assertNull(saved.getValue().getRevision());
	}

	@DisplayName("A run whose save loses to another writer disconnects the player with engine.concurrentChangeKick")
	@Test
	void runThatLosesItsSaveRequiresAReconnect() {
		EventUtil.initialize(mock(EventManager.class));
		PipelineStateStore store = mock(PipelineStateStore.class);
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());
		when(store.save(any(PipelineStateReference.class), any(PipelineState.class), anyLong())).thenReturn(false);
		TestAuthenticationPipeline pipeline = new TestAuthenticationPipeline(store, waitingExecutor());

		PipelineResult result = pipeline.execute(freshRequest(), null).toCompletableFuture().join();

		assertEquals(PipelineStatus.REQUIRE_RECONNECT, result.getStatus());
		assertEquals("reconnect", result.getMessage());
		ConnectionDecision decision = pipeline.mapDecision(result);
		assertEquals(ConnectionDecision.Status.REQUIRE_RECONNECT, decision.getStatus());
		assertEquals("reconnect", decision.getMessage());
	}

	private static ConnectionRequest freshRequest() {
		UUID connectionId = UUID.randomUUID();
		return ConnectionRequest.builder()
				.connectionUniqueId(connectionId)
				.identity(new ConnectionIdentity(UUID.randomUUID(), "PlayerOne", "127.0.0.1"))
				.build();
	}

	/** Runs a pipeline whose steps end waiting for the player. */
	private static PipelineExecutor waitingExecutor() {
		PipelineExecutor executor = mock(PipelineExecutor.class);
		when(executor.execute(any(), any(), any())).thenAnswer(invocation -> {
			PipelineExecutor.ExecutionObserver observer = invocation.getArgument(2);
			observer.onGroupCompleted(mock(PipelineGroup.class), new Object(),
					GroupOutcome.result(PipelineResult.waiting("prompt")), null);
			return CompletableFuture.completedFuture(null);
		});
		return executor;
	}

	private static JourneyOverrideItem chosenStage() {
		return new JourneyOverrideItem(null, "chosen", 0, false, null, List.of());
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static final class TypedPassword {
		private static final StateKey<TypedPassword> KEY = StateKey.run("test:typed-password", TypedPassword.class);

		private String password;
	}

	private static final class TestAuthenticationPipeline extends AuthenticationPipeline {
		private TestAuthenticationPipeline() {
			this(mock(PipelineStateStore.class), mock(PipelineExecutor.class));
		}

		private TestAuthenticationPipeline(PipelineStateStore store, PipelineExecutor executor) {
			super(
					mock(PipelineRegistry.class),
					TestAuthenticationPipeline::messages,
					() -> new EngineDefaults().supply(new Engine()),
					store,
					executor,
					mock(ProviderLinkPersistenceService.class)
			);
		}

		private static Messages messages() {
			Messages.Scenarios.Authentication authentication = new Messages.Scenarios.Authentication();
			authentication.setNoCompletionPipeline(List.of("no completion"));
			authentication.setAuthenticationFailed(List.of("failed"));
			Messages.Scenarios scenarios = new Messages.Scenarios();
			scenarios.setAuthentication(authentication);
			Messages.Engine engine = new Messages.Engine();
			engine.setConcurrentChangeKick(List.of("reconnect"));

			Messages messages = new Messages();
			messages.setScenarios(scenarios);
			messages.setEngine(engine);
			return messages;
		}

		private @Nullable ScenarioContext buildFor(@NotNull ConnectionRequest request) {
			return buildContext(request);
		}
	}
}
