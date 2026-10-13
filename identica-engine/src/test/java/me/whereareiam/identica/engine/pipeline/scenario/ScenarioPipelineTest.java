package me.whereareiam.identica.engine.pipeline.scenario;

import me.whereareiam.identica.engine.connection.ConnectionDecisionResolver;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.pipeline.state.PipelineInput;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A command hands its input to the pipeline while another run of the same connection, such as the one that resumes
 * the journey on joining, may still be going. That run has taken the state out of the store until it ends.
 */
@DisplayName("Scenario Pipeline")
class ScenarioPipelineTest {
	private final ConnectionDecisionResolver decisions = mock(ConnectionDecisionResolver.class);
	private final ScenarioRegistry registry = mock(ScenarioRegistry.class);
	private final PipelineStateStore store = mock(PipelineStateStore.class);
	private final AbstractScenarioPipeline runner = mock(AbstractScenarioPipeline.class);
	private final ScenarioPipeline pipeline = new ScenarioPipeline(
			decisions,
			registry,
			new ConnectionRuns(),
			store,
			mock(EventManager.class)
	);
	private final UUID connection = UUID.randomUUID();

	@BeforeEach
	void runners() {
		when(runner.type()).thenReturn(PipelineType.AUTHENTICATION);
		when(registry.selectForResume(any())).thenReturn(runner);
		when(registry.selectForAdvance(any())).thenReturn(runner);
		when(decisions.resolveDecision(any(), any(), any())).thenReturn(ConnectionDecision.waiting("prompt"));
	}

	@DisplayName("Asks for a command's input only after the run holding the state has finished, with the state it saved")
	@Test
	void asksTheInputAfterTheRunningResume() {
		CompletableFuture<PipelineResult> resumed = new CompletableFuture<>();
		when(runner.execute(isNull(), any(ResumeRequest.class))).thenReturn(resumed);
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());
		RecordingInput input = new RecordingInput(true);
		when(runner.executeAdvance(any())).thenReturn(CompletableFuture.completedFuture(PipelineResult.waiting("prompt")));

		pipeline.resume(resumeRequest());
		CompletableFuture<ConnectionDecision> advanced = pipeline.advance(advanceRequest(input)).toCompletableFuture();

		assertTrue(input.asked.isEmpty(), "the input must not be asked while the resumed run holds the state");
		assertFalse(advanced.isDone());
		verify(registry, never()).selectForAdvance(any());

		PipelineState saved = pendingState();
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.of(saved));
		resumed.complete(PipelineResult.waiting("prompt"));

		assertEquals(List.of(saved), input.asked);
		assertEquals(ConnectionDecision.Status.WAIT, advanced.join().getStatus());
		verify(runner).executeAdvance(argThat(request -> request.getInput() == input));
	}

	@DisplayName("Ends a declined input as nothing pending, without starting a run")
	@Test
	void declinedInputStartsNoRun() {
		PipelineState pending = pendingState();
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.of(pending));
		RecordingInput input = new RecordingInput(false);

		ConnectionDecision decision = pipeline.advance(advanceRequest(input)).toCompletableFuture().join();

		assertEquals(ConnectionDecision.Status.NO_PENDING, decision.getStatus());
		assertSame(pending, input.asked.getFirst());
		assertFalse(input.applied, "a declined input must not reach the state");
		verify(registry, never()).selectForAdvance(any());
	}

	@DisplayName("Ends a declined resume input as nothing pending, leaving the stored state where it is")
	@Test
	void declinedResumeInputLeavesTheState() {
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.of(pendingState()));

		ConnectionDecision decision = pipeline.resume(ResumeRequest.builder()
				.connectionUniqueId(connection)
				.identity(identity())
				.input(new RecordingInput(false))
				.build()).toCompletableFuture().join();

		assertEquals(ConnectionDecision.Status.NO_PENDING, decision.getStatus());
		verify(registry, never()).selectForResume(any());
		verify(store, never()).consume(any(PipelineStateReference.class));
	}

	private ResumeRequest resumeRequest() {
		return ResumeRequest.builder()
				.connectionUniqueId(connection)
				.identity(identity())
				.build();
	}

	private AdvanceRequest advanceRequest(PipelineInput input) {
		return AdvanceRequest.builder()
				.connectionUniqueId(connection)
				.identity(identity())
				.input(input)
				.build();
	}

	private ConnectionIdentity identity() {
		ConnectionIdentity identity = new ConnectionIdentity(UUID.randomUUID(), "PlayerOne", "127.0.0.1");
		identity.setConnectionUniqueId(connection);
		return identity;
	}

	private static PipelineState pendingState() {
		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.AUTHENTICATION);
		state.put(JourneyStateItem.KEY, new JourneyStateItem(null, null, 0), 60_000L);
		return state;
	}

	private static final class RecordingInput implements PipelineInput {
		private final boolean accepting;
		private final List<PipelineState> asked = new ArrayList<>();
		private boolean applied;

		private RecordingInput(boolean accepting) {
			this.accepting = accepting;
		}

		@Override
		public boolean accepts(@NotNull PipelineState state) {
			asked.add(state);
			return accepting;
		}

		@Override
		public void applyTo(@NotNull PipelineState state) {
			applied = true;
		}
	}
}
