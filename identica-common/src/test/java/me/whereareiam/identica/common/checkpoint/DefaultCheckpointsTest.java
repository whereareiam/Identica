package me.whereareiam.identica.common.checkpoint;

import me.whereareiam.identica.checkpoint.Checkpoint;
import me.whereareiam.identica.checkpoint.CheckpointStop;
import me.whereareiam.identica.checkpoint.HeldConnection;
import me.whereareiam.identica.common.event.EventController;
import me.whereareiam.identica.event.connection.ServerJoinedEvent;
import me.whereareiam.identica.event.identity.IdentityDetachedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentClearedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentStartedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentUpdatedEvent;
import me.whereareiam.identica.event.scenario.ScenarioResolvedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.checkpoint.CheckpointRequest;
import me.whereareiam.identica.model.checkpoint.CheckpointSelection;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.routing.RoutingEndpoint;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.RoutingSignal;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptState;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.platform.adapter.PlatformCheckpointAdapter;
import me.whereareiam.identica.routing.RoutingAttemptService;
import me.whereareiam.identica.routing.RoutingCoordinator;
import me.whereareiam.identica.service.DeliveryService;
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
import java.util.EnumSet;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultCheckpointsTest {
	private static final UUID ALICE = UUID.nameUUIDFromBytes("Alice".getBytes());
	private static final List<CheckpointPlacement> LOADING_SCREEN = List.of(CheckpointPlacement.LOADING_SCREEN);
	private static final List<CheckpointPlacement> STEP_SERVER = List.of(CheckpointPlacement.STEP_SERVER);

	private final RoutingCoordinator routing = mock(RoutingCoordinator.class);
	private final RoutingAttemptService attempts = mock(RoutingAttemptService.class);
	private final DeliveryService deliveries = mock(DeliveryService.class);
	private final IdentityService identities = mock(IdentityService.class);

	@Test
	void picksTheLatestRegisteredCheckpointThatAcceptsAndThePlatformCanHold() {
		DefaultCheckpoints checkpoints = checkpoints(new Platform(CheckpointPlacement.LOADING_SCREEN));
		RecordingCheckpoint earlier = new RecordingCheckpoint("earlier", LOADING_SCREEN, true);
		RecordingCheckpoint refusing = new RecordingCheckpoint("refusing", LOADING_SCREEN, false);
		checkpoints.register(earlier);
		checkpoints.register(refusing);

		assertEquals(Optional.of(new CheckpointSelection(earlier, CheckpointPlacement.LOADING_SCREEN)), checkpoints.select(request()));
		assertEquals(Optional.of(new CheckpointSelection(earlier, CheckpointPlacement.LOADING_SCREEN)), checkpoints.selected(ALICE));

		checkpoints.unregister(earlier);
		assertEquals(Optional.empty(), checkpoints.select(request()));
		assertEquals(Optional.empty(), checkpoints.selected(ALICE));
	}

	@Test
	void holdsAtTheFirstPlacementOfTheCheckpointThePlatformCanHoldForTheConnection() {
		List<CheckpointPlacement> either = List.of(CheckpointPlacement.LOADING_SCREEN, CheckpointPlacement.STEP_SERVER);

		DefaultCheckpoints loadingScreen = checkpoints(new Platform(CheckpointPlacement.LOADING_SCREEN));
		loadingScreen.register(new RecordingCheckpoint("either", either, true));
		assertEquals(CheckpointPlacement.LOADING_SCREEN, loadingScreen.select(request()).orElseThrow().getPlacement());

		DefaultCheckpoints stepServer = checkpoints(new Platform());
		stepServer.register(new RecordingCheckpoint("either", either, true));
		assertEquals(CheckpointPlacement.STEP_SERVER, stepServer.select(request()).orElseThrow().getPlacement());
	}

	@Test
	void neverPicksACheckpointNoneOfWhosePlacementsThePlatformCanHold() {
		DefaultCheckpoints checkpoints = checkpoints(new Platform());
		checkpoints.register(new RecordingCheckpoint("loading-screen", LOADING_SCREEN, true));

		assertEquals(Optional.empty(), checkpoints.select(request()));
	}

	@Test
	void givesAPlayerHeldOnTheLoadingScreenWhatWaitsForThemOnceTheJourneyContinued() {
		DefaultCheckpoints checkpoints = held();
		Identity alice = mock(Identity.class);
		when(identities.findByConnectionUniqueId(ALICE)).thenReturn(Optional.of(alice));

		arrive(checkpoints);

		verify(deliveries).dispatch(argThat(context -> context.getIdentity() == alice && context.getServer() == null));
	}

	@Test
	void handsAHeldConnectionToItsCheckpointResumesItAndReleasesItWithTheCompletionDestination() {
		DefaultCheckpoints checkpoints = checkpoints(new Platform(CheckpointPlacement.LOADING_SCREEN));
		RecordingCheckpoint checkpoint = new RecordingCheckpoint("loading-screen", LOADING_SCREEN, true);
		checkpoints.register(checkpoint);
		checkpoints.select(request());
		RecordingConnection connection = new RecordingConnection(() -> assertEquals(1, checkpoint.arrived.size()));

		checkpoints.arrive(connection);
		CheckpointStop stop = checkpoint.arrived.getFirst();
		assertEquals(ALICE, stop.getConnectionUniqueId());
		assertEquals(CheckpointPlacement.LOADING_SCREEN, stop.getPlacement());
		assertEquals("password-authentication-password", stop.getWaiting().orElseThrow().getStep().getName());
		assertEquals(1, connection.resumes);
		assertTrue(checkpoints.holds(ALICE));

		checkpoints.onScenarioResolved(resolved(ScenarioResolution.COMPLETED));
		assertTrue(checkpoints.holds(ALICE));
		assertEquals(Optional.empty(), stop.getWaiting());
		checkpoints.onRoutingUpdated(new RoutingIntentUpdatedEvent(intent(RoutingReason.STEP, "lobby")));
		assertTrue(checkpoints.holds(ALICE));
		checkpoints.onRoutingStarted(new RoutingIntentStartedEvent(intent(RoutingReason.COMPLETION, "lobby")));

		assertEquals(List.of("lobby"), connection.releases);
		assertFalse(checkpoints.holds(ALICE));
	}

	@Test
	void releasesAConnectionWithoutACheckpointForTheLoadingScreenAtOnce() {
		DefaultCheckpoints withoutCheckpoint = checkpoints(new Platform(CheckpointPlacement.LOADING_SCREEN));
		RecordingConnection connection = new RecordingConnection();
		withoutCheckpoint.arrive(connection);
		assertEquals(Collections.singletonList(null), connection.releases);
		assertFalse(withoutCheckpoint.holds(ALICE));

		DefaultCheckpoints onTheStepServer = checkpoints(new Platform(CheckpointPlacement.LOADING_SCREEN));
		onTheStepServer.register(new RecordingCheckpoint("step-server", STEP_SERVER, true));
		onTheStepServer.select(request());
		RecordingConnection held = new RecordingConnection();
		onTheStepServer.arrive(held);
		assertEquals(Collections.singletonList(null), held.releases);
		assertEquals(0, held.resumes);
	}

	@Test
	void holdsOnTheStepsServerOnceThePlayerIsInTheWorldOfTheServerRoutingSendsThemTo() {
		DefaultCheckpoints checkpoints = checkpoints(new Platform());
		RecordingCheckpoint checkpoint = new RecordingCheckpoint("step-server", STEP_SERVER, true);
		checkpoints.register(checkpoint);
		checkpoints.select(request());
		RoutingIntent toAuth = intent(RoutingReason.STEP, "auth");
		when(attempts.current(ALICE)).thenReturn(Optional.of(toAuth));

		checkpoints.onServerJoined(joined("lobby"));
		assertFalse(checkpoints.holds(ALICE));

		checkpoints.onServerJoined(joined("auth"));
		assertTrue(checkpoints.holds(ALICE));
		CheckpointStop stop = checkpoint.arrived.getFirst();
		assertEquals(CheckpointPlacement.STEP_SERVER, stop.getPlacement());
		assertEquals("password-authentication-password", stop.getWaiting().orElseThrow().getStep().getName());

		checkpoints.onScenarioResolved(resolved(ScenarioResolution.COMPLETED));
		checkpoints.onRoutingStarted(new RoutingIntentStartedEvent(intent(RoutingReason.COMPLETION, "lobby")));
		assertTrue(stop.released().toCompletableFuture().isDone());
		assertFalse(checkpoints.holds(ALICE));
	}

	@Test
	void holdsOnTheStepsServerOnceRoutingMovesThePlayerNowhereElse() {
		DefaultCheckpoints checkpoints = checkpoints(new Platform());
		RecordingCheckpoint checkpoint = new RecordingCheckpoint("step-server", STEP_SERVER, true);
		checkpoints.register(checkpoint);
		checkpoints.onServerJoined(joined("lobby"));
		checkpoints.select(request());
		when(attempts.current(ALICE)).thenReturn(Optional.of(intent(RoutingReason.STEP, "auth")));
		checkpoints.onRoutingStarted(new RoutingIntentStartedEvent(intent(RoutingReason.STEP, "auth")));
		assertFalse(checkpoints.holds(ALICE));

		when(attempts.current(ALICE)).thenReturn(Optional.empty());
		checkpoints.onRoutingCleared(new RoutingIntentClearedEvent(ALICE, null, RoutingClearReason.NO_TARGET));

		assertEquals(1, checkpoint.arrived.size());
		assertTrue(checkpoints.holds(ALICE));
	}

	@Test
	void answersThroughThePlatformAsThePlayer() {
		Platform platform = new Platform(CheckpointPlacement.LOADING_SCREEN);
		DefaultCheckpoints checkpoints = checkpoints(platform);
		RecordingCheckpoint checkpoint = new RecordingCheckpoint("loading-screen", LOADING_SCREEN, true);
		checkpoints.register(checkpoint);
		checkpoints.select(request());
		arrive(checkpoints);

		assertTrue(checkpoint.arrived.getFirst().answer("login secret").toCompletableFuture().join());

		assertEquals(List.of("login secret"), platform.answers);
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
		RecordingCheckpoint later = new RecordingCheckpoint("later", LOADING_SCREEN, true);
		checkpoints.register(later);

		assertEquals("loading-screen", checkpoints.select(request()).orElseThrow().getCheckpoint().id());
	}

	@Test
	void handsAHeldConnectionOverToTheStepsServer() {
		DefaultCheckpoints checkpoints = held();
		RecordingCheckpoint checkpoint = (RecordingCheckpoint) checkpoints.selected(ALICE).orElseThrow().getCheckpoint();
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
		assertEquals("loading-screen", checkpoints.select(request()).orElseThrow().getCheckpoint().id());
	}

	@Test
	void forgetsAConnectionThatLeft() {
		DefaultCheckpoints checkpoints = held();
		arrive(checkpoints);

		checkpoints.onIdentityDetached(new IdentityDetachedEvent(ALICE));

		assertFalse(checkpoints.holds(ALICE));
		assertEquals(Optional.empty(), checkpoints.selected(ALICE));
	}

	@Test
	void endsTheStepServerHoldOfAConnectionThatLeft() {
		DefaultCheckpoints checkpoints = checkpoints(new Platform());
		RecordingCheckpoint checkpoint = new RecordingCheckpoint("step-server", STEP_SERVER, true);
		checkpoints.register(checkpoint);
		checkpoints.select(request());
		when(attempts.current(ALICE)).thenReturn(Optional.empty());
		checkpoints.onServerJoined(joined("auth"));

		checkpoints.onIdentityDetached(new IdentityDetachedEvent(ALICE));

		assertTrue(checkpoint.arrived.getFirst().released().toCompletableFuture().isDone());
		assertFalse(checkpoints.holds(ALICE));
	}

	private DefaultCheckpoints checkpoints(PlatformCheckpointAdapter platform) {
		return new DefaultCheckpoints(platform, () -> routing, () -> attempts, () -> deliveries, () -> identities, new EventController());
	}

	/**
	 * Returns checkpoints that picked a loading-screen checkpoint for Alice's waiting step.
	 */
	private DefaultCheckpoints held() {
		DefaultCheckpoints checkpoints = checkpoints(new Platform(CheckpointPlacement.LOADING_SCREEN));
		checkpoints.register(new RecordingCheckpoint("loading-screen", LOADING_SCREEN, true));
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
				StepResult.waiting(message("")));
	}

	private static ServerJoinedEvent joined(String server) {
		Identity identity = mock(Identity.class);
		when(identity.getConnectionUniqueId()).thenReturn(ALICE);
		return new ServerJoinedEvent(identity, server);
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

	/**
	 * A platform that holds the given placements for every connection and records the answers it runs.
	 */
	private static final class Platform implements PlatformCheckpointAdapter {
		private final Set<CheckpointPlacement> placements = EnumSet.noneOf(CheckpointPlacement.class);
		private final List<String> answers = new ArrayList<>();

		private Platform(CheckpointPlacement... placements) {
			this.placements.addAll(List.of(placements));
		}

		@Override
		public @NotNull Set<CheckpointPlacement> placements(@NotNull UUID connectionUniqueId) {
			return placements;
		}

		@Override
		public @NotNull CompletionStage<Boolean> answer(@NotNull UUID connectionUniqueId, @NotNull String command) {
			answers.add(command);
			return CompletableFuture.completedFuture(true);
		}
	}

	private static final class RecordingCheckpoint implements Checkpoint {
		private final String id;
		private final List<CheckpointPlacement> placements;
		private final boolean accepts;
		private final List<CheckpointStop> arrived = new ArrayList<>();

		private RecordingCheckpoint(String id, List<CheckpointPlacement> placements, boolean accepts) {
			this.id = id;
			this.placements = placements;
			this.accepts = accepts;
		}

		@Override
		public @NotNull String id() {
			return id;
		}

		@Override
		public @NotNull List<CheckpointPlacement> placements() {
			return placements;
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
	 * A held connection that records its resumes, releases and hand-overs and completes its release on the first
	 * release or hand-over.
	 */
	private static final class RecordingConnection implements HeldConnection {
		private final Runnable onResume;
		private final List<@Nullable String> releases = new ArrayList<>();
		private final List<@Nullable String> handOvers = new ArrayList<>();
		private final CompletableFuture<Void> released = new CompletableFuture<>();
		private int resumes;

		private RecordingConnection() {
			this(() -> {});
		}

		private RecordingConnection(Runnable onResume) {
			this.onResume = onResume;
		}

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
			onResume.run();
			resumes++;
			return CompletableFuture.completedFuture(ConnectionDecision.allow());
		}

		@Override
		public @NotNull CompletionStage<Void> released() {
			return released;
		}
	}

	private static PlayerMessage message(String text) {
		return PlayerMessage.builder().text(text).build();
	}
}
