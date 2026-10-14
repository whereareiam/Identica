package me.whereareiam.identica.engine.pipeline.scenario.type.migration;

import me.whereareiam.identica.common.replication.DefaultReplicationSystem;
import me.whereareiam.identica.engine.pipeline.scenario.ConnectionRuns;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.scenario.migration.MigrationRequiredEvent;
import me.whereareiam.identica.event.scenario.migration.MigrationResolvedEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.migration.MigrationJourney;
import me.whereareiam.identica.model.migration.PendingMigration;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.pipeline.migration.MigrationPendingState;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.replication.ReplicationAdapter;
import me.whereareiam.identica.type.ScenarioResolution;
import me.whereareiam.identica.type.migration.MigrationInitiator;
import me.whereareiam.identica.type.migration.MigrationResultStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Commands and administrators start, cancel and look for migration journeys while a run of the same connection, such
 * as the one resuming the journey when the player connects again, may hold the journey's state.
 */
@DisplayName("Migration Journeys")
class MigrationJourneysTest {
	private final ConnectionRuns runs = new ConnectionRuns();
	private final PipelineStateStore store = mock(PipelineStateStore.class);
	private final EventManager events = mock(EventManager.class);
	private final MigrationJourneys journeys = new MigrationJourneys(
			runs,
			store,
			events,
			MigrationJourneysTest::engine,
			new DefaultReplicationSystem(localOnlyAdapter()),
			MigrationJourneysTest::replication
	);
	private final UUID connection = UUID.randomUUID();
	private final UUID account = UUID.randomUUID();

	@DisplayName("Cancels a journey only after the run holding it has finished, clearing what that run saved")
	@Test
	void cancelsAfterTheRunningRun() {
		CompletableFuture<Void> running = hold();
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());

		CompletableFuture<Optional<PendingMigration>> cancelled = journeys.cancel(connection).toCompletableFuture();

		assertFalse(cancelled.isDone());
		verify(store, never()).clear(any(PipelineStateReference.class), any(PipelineState.class));

		PipelineState saved = startedState();
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.of(saved));
		when(store.clear(any(PipelineStateReference.class), any(PipelineState.class))).thenReturn(true);
		running.complete(null);

		PendingMigration journey = cancelled.join().orElseThrow();
		assertEquals(account, journey.getUniqueId());
		verify(store).clear(any(PipelineStateReference.class), eq(saved));
		verify(events).call(argThat(event -> event instanceof MigrationResolvedEvent resolved
				&& resolved.getReason() == ScenarioResolution.CANCELLED
				&& account.equals(resolved.getAccountUniqueId())));
	}

	@DisplayName("Starts a journey only after the run holding the state has finished, never over the journey it left")
	@Test
	void doesNotStartOverAJourneyTheRunningRunLeft() {
		CompletableFuture<Void> running = hold();
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());

		CompletableFuture<MigrationResultStatus> started = journeys.start(journey()).toCompletableFuture();

		assertFalse(started.isDone());
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.of(startedState()));
		running.complete(null);

		assertEquals(MigrationResultStatus.PENDING_EXISTS, started.join());
		verify(store, never()).save(any(PipelineStateReference.class), any(PipelineState.class), anyLong());
	}

	@DisplayName("Starts a journey the player meets when connecting again")
	@Test
	void startsAJourney() {
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());
		when(store.save(any(PipelineStateReference.class), any(PipelineState.class), anyLong())).thenReturn(true);

		assertEquals(MigrationResultStatus.STARTED, journeys.start(journey()).toCompletableFuture().join());

		ArgumentCaptor<PipelineState> saved = ArgumentCaptor.forClass(PipelineState.class);
		verify(store).save(any(PipelineStateReference.class), saved.capture(), anyLong());
		PipelineState state = saved.getValue();
		assertEquals(PipelineType.MIGRATION, state.getPipelineType());
		assertTrue(state.has(JourneyStateItem.KEY));
		MigrationContext context = (MigrationContext) state.getScenario(PipelineType.MIGRATION);
		assertEquals("premium", context.getTargetProviderId());
		assertEquals(account, context.getAccountUniqueId());
		assertEquals(1234L, state.get(MigrationPendingState.KEY).orElseThrow().getRequestedAt());
		assertNull(state.getRevision(), "a connection without a state gets one only while it still has none");
		verify(events).call(argThat(event -> event instanceof MigrationRequiredEvent required
				&& connection.equals(required.getConnectionUniqueId())
				&& account.equals(required.getAccountUniqueId())));
	}

	@DisplayName("Starts a journey over exactly the state the connection had when it was read")
	@Test
	void startReplacesTheStateItRead() {
		PipelineState waiting = PipelineState.initial();
		waiting.setPipelineType(PipelineType.AUTHENTICATION);
		waiting.setRevision("read");
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.of(waiting));
		when(store.save(any(PipelineStateReference.class), any(PipelineState.class), anyLong())).thenReturn(true);

		assertEquals(MigrationResultStatus.STARTED, journeys.start(journey()).toCompletableFuture().join());

		ArgumentCaptor<PipelineState> saved = ArgumentCaptor.forClass(PipelineState.class);
		verify(store).save(eq(PipelineStateReference.of("PlayerOne", "127.0.0.1")), saved.capture(), anyLong());
		assertEquals("read", saved.getValue().getRevision());
	}

	@DisplayName("A start whose save loses to another proxy fails and announces no journey")
	@Test
	void startThatLosesFails() {
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());
		when(store.save(any(PipelineStateReference.class), any(PipelineState.class), anyLong())).thenReturn(false);

		assertEquals(MigrationResultStatus.FAILED, journeys.start(journey()).toCompletableFuture().join());

		verify(events, never()).call(any(MigrationRequiredEvent.class));
		assertTrue(journeys.find(connection).toCompletableFuture().join().isEmpty());
	}

	@DisplayName("A cancel whose clear loses to another proxy cancels nothing and keeps the newer state")
	@Test
	void cancelThatLosesCancelsNothing() {
		when(store.save(any(PipelineStateReference.class), any(PipelineState.class), anyLong())).thenReturn(true);
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());
		journeys.start(journey()).toCompletableFuture().join();
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.of(startedState()));
		when(store.clear(any(PipelineStateReference.class), any(PipelineState.class))).thenReturn(false);

		assertTrue(journeys.cancel(connection).toCompletableFuture().join().isEmpty());

		verify(store, never()).clear(any(PipelineStateReference.class));
		verify(events, never()).call(any(MigrationResolvedEvent.class));
		assertTrue(journeys.find(connection).toCompletableFuture().join().isPresent(), "the journey stays findable");
	}

	@DisplayName("Finds a journey in the state of the connection it was started for, its username and address")
	@Test
	void findsTheJourneyUnderItsConnection() {
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());
		when(store.save(any(PipelineStateReference.class), any(PipelineState.class), anyLong())).thenReturn(true);
		journeys.start(journey()).toCompletableFuture().join();
		when(store.find(PipelineStateReference.of("PlayerOne", "127.0.0.1"))).thenReturn(Optional.of(startedState()));

		assertTrue(journeys.find(connection).toCompletableFuture().join().isPresent());
	}

	@DisplayName("Finds a started journey with the details it was started with")
	@Test
	void findsAStartedJourney() {
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.of(startedState()));

		PendingMigration journey = journeys.find(connection).toCompletableFuture().join().orElseThrow();

		assertEquals(account, journey.getUniqueId());
		assertEquals(connection, journey.getConnectionUniqueId());
		assertEquals("premium", journey.getTargetProviderId());
		assertEquals(1234L, journey.getRequestedAt());
		assertEquals(MigrationInitiator.ADMIN, journey.getInitiator());
		assertEquals(PendingMigration.Phase.STARTED, journey.getPhase());
	}

	private CompletableFuture<Void> hold() {
		CompletableFuture<Void> running = new CompletableFuture<>();
		runs.submit(connection, () -> running);
		return running;
	}

	private MigrationJourney journey() {
		return MigrationJourney.builder()
				.connectionUniqueId(connection)
				.accountUniqueId(account)
				.username("PlayerOne")
				.ip("127.0.0.1")
				.targetProviderId("premium")
				.requestedAt(1234L)
				.initiator(MigrationInitiator.ADMIN)
				.build();
	}

	private PipelineState startedState() {
		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.MIGRATION);
		state.setScenario(MigrationContext.builder()
				.connectionUniqueId(connection)
				.identity(new ConnectionIdentity(account, "PlayerOne", "127.0.0.1"))
				.targetProviderId("premium")
				.build());
		state.put(MigrationPendingState.KEY, new MigrationPendingState("premium", 1234L, MigrationInitiator.ADMIN, null), 60_000L);
		state.put(JourneyStateItem.KEY, new JourneyStateItem(JourneyMode.INTERACTIVE, null, 0), 60_000L);
		return state;
	}

	private static Replication replication() {
		Replication replication = new Replication();
		Replication.Cache cache = new Replication.Cache();
		cache.setPipelineState("pipeline-state");
		replication.setCache(cache);
		return replication;
	}

	private static ReplicationAdapter localOnlyAdapter() {
		ReplicationAdapter adapter = mock(ReplicationAdapter.class);
		when(adapter.isAvailable()).thenReturn(false);
		return adapter;
	}

	private static Engine engine() {
		Engine engine = new Engine();
		engine.getScenarios().getMigration().setPipelineTtl(Duration.ofMinutes(5));
		engine.getScenarios().getMigration().setJourneyMode(JourneyMode.INTERACTIVE);
		return engine;
	}
}
