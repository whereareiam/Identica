package me.whereareiam.identica.common.checkpoint;

import me.whereareiam.identica.checkpoint.Checkpoint;
import me.whereareiam.identica.checkpoint.CheckpointStop;
import me.whereareiam.identica.checkpoint.HeldConnection;
import me.whereareiam.identica.common.event.EventController;
import me.whereareiam.identica.event.identity.IdentityDetachedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentClearedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentStartedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentUpdatedEvent;
import me.whereareiam.identica.event.scenario.ScenarioResolvedEvent;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.checkpoint.CheckpointRequest;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.routing.RoutingEndpoint;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.RoutingSignal;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptState;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.routing.RoutingCoordinator;
import me.whereareiam.identica.type.ScenarioResolution;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import me.whereareiam.identica.type.routing.reason.RoutingClearReason;
import me.whereareiam.identica.type.routing.reason.RoutingReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultCheckpointsTest {
	private static final UUID ALICE = UUID.nameUUIDFromBytes("Alice".getBytes());

	private final RoutingCoordinator routing = mock(RoutingCoordinator.class);

	@Test
	void picksTheLatestRegisteredCheckpointThatAcceptsAndThePlatformCanHold() {
		DefaultCheckpoints checkpoints = checkpoints(Set.of(CheckpointPlacement.PROXY));
		RecordingCheckpoint earlier = new RecordingCheckpoint("earlier", CheckpointPlacement.PROXY, true);
		RecordingCheckpoint refusing = new RecordingCheckpoint("refusing", CheckpointPlacement.PROXY, false);
		checkpoints.register(earlier);
		checkpoints.register(refusing);

		assertEquals(Optional.of(earlier), checkpoints.select(request()));
		assertEquals(Optional.of(earlier), checkpoints.selected(ALICE));

		checkpoints.unregister(earlier);
		assertEquals(Optional.empty(), checkpoints.select(request()));
		assertEquals(Optional.empty(), checkpoints.selected(ALICE));
	}

	@Test
	void neverPicksACheckpointWhosePlacementThePlatformCannotHold() {
		DefaultCheckpoints checkpoints = checkpoints(Set.of());
		checkpoints.register(new RecordingCheckpoint("loading-screen", CheckpointPlacement.PROXY, true));

		assertEquals(Optional.empty(), checkpoints.select(request()));
	}

	@Test
	void handsAHeldConnectionToItsCheckpointAndReleasesItWithTheCompletionDestination() {
		DefaultCheckpoints checkpoints = checkpoints(Set.of(CheckpointPlacement.PROXY));
		RecordingCheckpoint checkpoint = new RecordingCheckpoint("loading-screen", CheckpointPlacement.PROXY, true);
		checkpoints.register(checkpoint);
		checkpoints.select(request());
		RecordingConnection connection = new RecordingConnection();

		checkpoints.arrive(connection);
		assertEquals(List.of(ALICE), checkpoint.arrived.stream().map(CheckpointStop::getConnectionUniqueId).toList());
		assertTrue(checkpoints.holds(ALICE));

		checkpoints.onScenarioResolved(resolved(ScenarioResolution.COMPLETED));
		assertTrue(checkpoints.holds(ALICE));
		checkpoints.onRoutingUpdated(new RoutingIntentUpdatedEvent(intent(RoutingReason.STEP, "lobby")));
		assertTrue(checkpoints.holds(ALICE));
		checkpoints.onRoutingStarted(new RoutingIntentStartedEvent(intent(RoutingReason.COMPLETION, "lobby")));

		assertEquals(List.of("lobby"), connection.releases);
		assertFalse(checkpoints.holds(ALICE));
	}

	@Test
	void releasesAConnectionWithoutACheckpointAtOnce() {
		DefaultCheckpoints checkpoints = checkpoints(Set.of(CheckpointPlacement.PROXY));
		RecordingConnection connection = new RecordingConnection();

		checkpoints.arrive(connection);

		assertEquals(Collections.singletonList(null), connection.releases);
		assertFalse(checkpoints.holds(ALICE));
	}

	@Test
	void releasesACancelledJourneyWhereThePlayerIsButLeavesAnExpiredOneToItsDisconnect() {
		DefaultCheckpoints cancelled = held();
		RecordingConnection cancelledConnection = arrive(cancelled);
		cancelled.onScenarioResolved(resolved(ScenarioResolution.CANCELLED));
		assertEquals(Collections.singletonList(null), cancelledConnection.releases);

		DefaultCheckpoints expired = held();
		RecordingConnection expiredConnection = arrive(expired);
		expired.onScenarioResolved(resolved(ScenarioResolution.EXPIRED));
		assertEquals(List.of(), expiredConnection.releases);
		assertTrue(expired.holds(ALICE));
	}

	@Test
	void releasesAConnectionWhoseRoutingIsDroppedOnceNothingOfItWaits() {
		DefaultCheckpoints checkpoints = held();
		RecordingConnection connection = arrive(checkpoints);

		checkpoints.onRoutingCleared(new RoutingIntentClearedEvent(ALICE, null, RoutingClearReason.NO_TARGET));
		assertEquals(List.of(), connection.releases);

		checkpoints.onScenarioResolved(resolved(ScenarioResolution.COMPLETED));
		checkpoints.onRoutingCleared(new RoutingIntentClearedEvent(ALICE, null, RoutingClearReason.REPLACED));
		assertEquals(List.of(), connection.releases);
		checkpoints.onRoutingCleared(new RoutingIntentClearedEvent(ALICE, null, RoutingClearReason.NO_TARGET));
		assertEquals(Collections.singletonList(null), connection.releases);
	}

	@Test
	void keepsTheCheckpointThatHoldsAConnectionForItsLaterSteps() {
		DefaultCheckpoints checkpoints = held();
		arrive(checkpoints);
		RecordingCheckpoint later = new RecordingCheckpoint("later", CheckpointPlacement.PROXY, true);
		checkpoints.register(later);

		assertEquals("loading-screen", checkpoints.select(request()).orElseThrow().id());
	}

	@Test
	void handsAHeldConnectionOverToTheStepsServer() {
		DefaultCheckpoints checkpoints = held();
		RecordingCheckpoint checkpoint = (RecordingCheckpoint) checkpoints.selected(ALICE).orElseThrow();
		RecordingConnection connection = arrive(checkpoints);
		doAnswer(invocation -> {
			checkpoints.onRoutingStarted(new RoutingIntentStartedEvent(intent(RoutingReason.STEP, "auth")));
			return null;
		}).when(routing).accept(any());

		checkpoint.arrived.getFirst().handOver();

		ArgumentCaptor<RoutingSignal> signal = ArgumentCaptor.forClass(RoutingSignal.class);
		verify(routing).accept(signal.capture());
		assertEquals("password-authentication-password", signal.getValue().getStep().getName());
		assertEquals(PipelineType.AUTHENTICATION, signal.getValue().getPipelineType());
		assertEquals(List.of(), connection.releases);
		assertEquals(List.of("auth"), connection.handOvers);
		assertFalse(checkpoints.holds(ALICE));
		assertEquals(Optional.empty(), checkpoints.select(request()));

		checkpoints.onScenarioResolved(resolved(ScenarioResolution.COMPLETED));
		assertEquals("loading-screen", checkpoints.select(request()).orElseThrow().id());
	}

	@Test
	void forgetsAConnectionThatLeft() {
		DefaultCheckpoints checkpoints = held();
		arrive(checkpoints);

		checkpoints.onIdentityDetached(new IdentityDetachedEvent(ALICE));

		assertFalse(checkpoints.holds(ALICE));
		assertEquals(Optional.empty(), checkpoints.selected(ALICE));
	}

	private DefaultCheckpoints checkpoints(Set<CheckpointPlacement> placements) {
		return new DefaultCheckpoints(() -> placements, () -> routing, new EventController());
	}

	/**
	 * Returns checkpoints that picked a proxy checkpoint for Alice's waiting step.
	 */
	private DefaultCheckpoints held() {
		DefaultCheckpoints checkpoints = checkpoints(Set.of(CheckpointPlacement.PROXY));
		checkpoints.register(new RecordingCheckpoint("loading-screen", CheckpointPlacement.PROXY, true));
		checkpoints.select(request());
		return checkpoints;
	}

	private static RecordingConnection arrive(DefaultCheckpoints checkpoints) {
		RecordingConnection connection = new RecordingConnection();
		checkpoints.arrive(connection);
		return connection;
	}

	private static CheckpointRequest request() {
		Step step = mock(Step.class);
		when(step.getName()).thenReturn("password-authentication-password");
		return new CheckpointRequest(ALICE, mock(ScenarioContext.class), PipelineType.AUTHENTICATION, StageType.PROVIDER, step,
				StepResult.waiting(""));
	}

	private static ScenarioResolvedEvent resolved(ScenarioResolution resolution) {
		ScenarioResolvedEvent event = mock(ScenarioResolvedEvent.class);
		when(event.getConnectionUniqueId()).thenReturn(ALICE);
		when(event.getReason()).thenReturn(resolution);
		return event;
	}

	private static RoutingIntent intent(RoutingReason reason, String server) {
		return new RoutingIntent(UUID.randomUUID(), ALICE, new RoutingEndpoint(server), reason,
				RoutingAttemptPolicy.defaultCompletion(), new RoutingAttemptState(), null, null, null, null, 0L);
	}

	private static final class RecordingCheckpoint implements Checkpoint {
		private final String id;
		private final CheckpointPlacement placement;
		private final boolean accepts;
		private final List<CheckpointStop> arrived = new ArrayList<>();

		private RecordingCheckpoint(String id, CheckpointPlacement placement, boolean accepts) {
			this.id = id;
			this.placement = placement;
			this.accepts = accepts;
		}

		@Override
		public @NotNull String id() {
			return id;
		}

		@Override
		public @NotNull CheckpointPlacement placement() {
			return placement;
		}

		@Override
		public boolean accepts(@NotNull CheckpointRequest request) {
			return accepts;
		}

		@Override
		public void arrived(@NotNull CheckpointStop stop) {
			arrived.add(stop);
		}
	}

	/**
	 * A held connection that records its releases and hand-overs and completes its release on the first one.
	 */
	private static final class RecordingConnection implements HeldConnection {
		private final List<@Nullable String> releases = new ArrayList<>();
		private final List<@Nullable String> handOvers = new ArrayList<>();
		private final CompletableFuture<Void> released = new CompletableFuture<>();

		@Override
		public void release(@Nullable String destination) {
			releases.add(destination);
			released.complete(null);
		}

		@Override
		public void handOver(@Nullable String destination) {
			handOvers.add(destination);
			released.complete(null);
		}

		@Override
		public @NotNull UUID getConnectionUniqueId() {
			return ALICE;
		}

		@Override
		public @NotNull CompletionStage<ConnectionDecision> resume() {
			throw new AssertionError("Checkpoints do not resume journeys");
		}

		@Override
		public @NotNull CompletionStage<Boolean> answer(@NotNull String command) {
			throw new AssertionError("Checkpoints do not answer");
		}

		@Override
		public @NotNull CompletionStage<Void> released() {
			return released;
		}
	}
}
