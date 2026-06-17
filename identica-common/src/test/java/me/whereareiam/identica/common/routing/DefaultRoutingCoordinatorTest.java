package me.whereareiam.identica.common.routing;

import me.whereareiam.identica.Registry;
import me.whereareiam.identica.common.event.EventController;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionTerminatedEvent;
import me.whereareiam.identica.event.routing.completion.CompletionRoutingReachedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentClearedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentReachedEvent;
import me.whereareiam.identica.model.routing.RoutingEndpoint;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptDecision;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptRequest;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptState;
import me.whereareiam.identica.replication.store.participant.ConnectionDisconnectedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionTerminatedParticipant;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.routing.RoutingAttemptTrigger;
import me.whereareiam.identica.type.routing.RoutingIntentStatus;
import me.whereareiam.identica.type.routing.reason.RoutingReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("Default Routing Coordinator")
class DefaultRoutingCoordinatorTest {
	@DisplayName("Already-reached completion attempts publish reached and clear the intent")
	@Test
	void alreadyReachedCompletionAttemptPublishesReachedAndClearsIntent() {
		DefaultRoutingIntentStore store = new DefaultRoutingIntentStore();
		EventController events = new EventController();
		ReachedCapture capture = new ReachedCapture();
		events.register(capture);
		DefaultRoutingCoordinator coordinator = new DefaultRoutingCoordinator(null, store, events, noopDisconnectedRegistry(), noopTerminatedRegistry());
		UUID connectionUniqueId = UUID.randomUUID();
		store.put(intent(connectionUniqueId, "lobby", RoutingReason.COMPLETION, RoutingAttemptPolicy.defaultCompletion()));

		RoutingAttemptDecision decision = coordinator.decide(new RoutingAttemptRequest(
				connectionUniqueId,
				RoutingAttemptTrigger.ASYNC_CONNECT,
				"lobby"
		));

		assertFalse(decision.isAllowed());
		assertEquals("already-reached", decision.getReason());
		assertEquals(1, capture.reached);
		assertEquals(1, capture.completionReached);
		assertEquals(1, capture.cleared);
		assertFalse(store.peek(connectionUniqueId).isPresent());
	}

	@DisplayName("Already-reached step attempts publish reached and retain the intent")
	@Test
	void alreadyReachedStepAttemptPublishesReachedAndKeepsIntent() {
		DefaultRoutingIntentStore store = new DefaultRoutingIntentStore();
		EventController events = new EventController();
		ReachedCapture capture = new ReachedCapture();
		events.register(capture);
		DefaultRoutingCoordinator coordinator = new DefaultRoutingCoordinator(null, store, events, noopDisconnectedRegistry(), noopTerminatedRegistry());
		UUID connectionUniqueId = UUID.randomUUID();
		store.put(intent(connectionUniqueId, "lobby", RoutingReason.STEP, RoutingAttemptPolicy.defaultStep()));

		RoutingAttemptDecision decision = coordinator.decide(new RoutingAttemptRequest(
				connectionUniqueId,
				RoutingAttemptTrigger.ASYNC_CONNECT,
				"lobby"
		));

		assertFalse(decision.isAllowed());
		assertEquals("already-reached", decision.getReason());
		assertEquals(1, capture.reached);
		assertEquals(0, capture.completionReached);
		assertEquals(0, capture.cleared);
		assertEquals(RoutingIntentStatus.REACHED, store.peek(connectionUniqueId).orElseThrow().getStatus());
	}

	private RoutingIntent intent(
			UUID connectionUniqueId,
			String server,
			RoutingReason reason,
			RoutingAttemptPolicy policy
	) {
		return new RoutingIntent(
				UUID.randomUUID(),
				connectionUniqueId,
				new RoutingEndpoint(server),
				reason,
				policy,
				new RoutingAttemptState(),
				PipelineType.MIGRATION,
				null,
				"credential",
				null,
				System.currentTimeMillis()
		);
	}

	@DisplayName("Disconnect and termination boundaries clear current routing intents")
	@Test
	void disconnectAndTerminationBoundariesClearCurrentIntent() {
		DefaultRoutingIntentStore store = new DefaultRoutingIntentStore();
		DefaultRoutingCoordinator coordinator = new DefaultRoutingCoordinator(null, store, new EventController(), noopDisconnectedRegistry(), noopTerminatedRegistry());
		UUID disconnectedConnection = UUID.randomUUID();
		store.put(intent(disconnectedConnection, "lobby", RoutingReason.STEP, RoutingAttemptPolicy.defaultStep()));

		coordinator.onConnectionDisconnected(new ConnectionDisconnectedEvent(disconnectedConnection, null, null));

		assertFalse(store.peek(disconnectedConnection).isPresent());

		UUID terminatedConnection = UUID.randomUUID();
		store.put(intent(terminatedConnection, "lobby", RoutingReason.STEP, RoutingAttemptPolicy.defaultStep()));

		coordinator.onConnectionTerminated(new ConnectionTerminatedEvent(terminatedConnection, null, null));

		assertFalse(store.peek(terminatedConnection).isPresent());
	}

	private Registry<ConnectionDisconnectedParticipant> noopDisconnectedRegistry() {
		return new Registry<>() {
			@Override
			public void register(ConnectionDisconnectedParticipant value) {
			}

			@Override
			public void unregister(ConnectionDisconnectedParticipant value) {
			}

			@Override
			public java.util.Set<ConnectionDisconnectedParticipant> values() {
				return java.util.Set.of();
			}
		};
	}

	private Registry<ConnectionTerminatedParticipant> noopTerminatedRegistry() {
		return new Registry<>() {
			@Override
			public void register(ConnectionTerminatedParticipant value) {
			}

			@Override
			public void unregister(ConnectionTerminatedParticipant value) {
			}

			@Override
			public java.util.Set<ConnectionTerminatedParticipant> values() {
				return java.util.Set.of();
			}
		};
	}

	private static final class ReachedCapture implements EventListener {
		private int reached;
		private int completionReached;
		private int cleared;

		@IdenticEvent
		public void onRoutingIntentReached(RoutingIntentReachedEvent event) {
			reached++;
		}

		@IdenticEvent
		public void onCompletionRoutingReached(CompletionRoutingReachedEvent event) {
			completionReached++;
		}

		@IdenticEvent
		public void onRoutingIntentCleared(RoutingIntentClearedEvent event) {
			cleared++;
		}
	}
}
