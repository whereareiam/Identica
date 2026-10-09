package me.whereareiam.identica.common.event;

import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.base.SynchronousEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentStartedEvent;
import me.whereareiam.identica.event.scenario.ScenarioRequiredEvent;
import me.whereareiam.identica.event.scenario.ScenarioResolvedEvent;
import me.whereareiam.identica.event.scenario.authentication.AuthenticationRequiredEvent;
import me.whereareiam.identica.event.scenario.authentication.AuthenticationResolvedEvent;
import me.whereareiam.identica.event.scenario.migration.MigrationRequiredEvent;
import me.whereareiam.identica.event.scenario.migration.MigrationResolvedEvent;
import me.whereareiam.identica.event.scenario.registration.RegistrationRequiredEvent;
import me.whereareiam.identica.event.scenario.registration.RegistrationResolvedEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.model.routing.RoutingEndpoint;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptState;
import me.whereareiam.identica.type.ScenarioResolution;
import me.whereareiam.identica.type.event.EventOrder;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.routing.reason.RoutingReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Event Controller")
class EventControllerTest {
	@Test
	void shutdownFinishesProvidersAndFeaturesBeforeInfrastructureCloses() {
		EventController controller = new EventController();
		java.util.List<String> order = new java.util.ArrayList<>();
		Thread shutdownThread = Thread.currentThread();
		controller.register(new EventListener() {
			@IdenticEvent(EventOrder.HIGH)
			public void database(IdenticaShutdownEvent event) {
				assertEquals(shutdownThread, Thread.currentThread());
				order.add("database");
			}

			@IdenticEvent(EventOrder.LOW)
			public void providersAndFeatures(IdenticaShutdownEvent event) {
				assertEquals(shutdownThread, Thread.currentThread());
				order.add("providers");
				order.add("features");
			}
		});

		controller.call(new IdenticaShutdownEvent());
		order.add("platform");

		assertEquals(java.util.List.of("providers", "features", "database", "platform"), order);
	}

	@DisplayName("Interface-based routing listeners receive concrete routing intent events")
	@Test
	void routingIntentInterfaceListenerReceivesConcreteIntentEvent() {
		EventController eventController = new EventController();
		RoutingListener listener = new RoutingListener();
		eventController.register(listener);

		eventController.call(new RoutingIntentStartedEvent(intent()));

		assertEquals(1, listener.intentEvents.get());
	}

	@DisplayName("Interface-based scenario required listeners receive concrete scenario required events")
	@Test
	void scenarioRequiredInterfaceListenerReceivesConcreteEvents() {
		EventController eventController = new EventController();
		ScenarioRequiredListener listener = new ScenarioRequiredListener();
		eventController.register(listener);

		UUID connectionId = UUID.randomUUID();
		UUID accountId = UUID.randomUUID();
		eventController.call(new AuthenticationRequiredEvent(
				connectionId,
				accountId,
				authContext(connectionId, accountId),
				false,
				System.currentTimeMillis() + 1_000L,
				JourneyMode.SEAMLESS
		));
		eventController.call(new RegistrationRequiredEvent(
				connectionId,
				accountId,
				registrationContext(connectionId, accountId),
				false,
				System.currentTimeMillis() + 1_000L,
				JourneyMode.INTERACTIVE
		));
		eventController.call(new MigrationRequiredEvent(
				connectionId,
				accountId,
				migrationContext(connectionId, accountId),
				false,
				System.currentTimeMillis() + 1_000L,
				JourneyMode.SEAMLESS
		));

		assertEquals(3, listener.requiredEvents.get());
	}

	@DisplayName("Interface-based scenario resolved listeners receive concrete scenario resolved events")
	@Test
	void scenarioResolvedInterfaceListenerReceivesConcreteEvents() {
		EventController eventController = new EventController();
		ScenarioResolvedListener listener = new ScenarioResolvedListener();
		eventController.register(listener);

		UUID connectionId = UUID.randomUUID();
		UUID accountId = UUID.randomUUID();
		eventController.call(new AuthenticationResolvedEvent(
				connectionId,
				accountId,
				authContext(connectionId, accountId),
				ScenarioResolution.COMPLETED,
				true
		));
		eventController.call(new RegistrationResolvedEvent(
				connectionId,
				accountId,
				registrationContext(connectionId, accountId),
				ScenarioResolution.DENIED,
				false
		));
		eventController.call(new MigrationResolvedEvent(
				connectionId,
				accountId,
				migrationContext(connectionId, accountId),
				ScenarioResolution.CANCELLED,
				false
		));

		assertEquals(3, listener.resolvedEvents.get());
	}

	@DisplayName("Dispatch plan cache is invalidated when listeners are registered at runtime")
	@Test
	void registerInvalidatesCachedDispatchPlan() {
		EventController eventController = new EventController();
		SimpleEventListener first = new SimpleEventListener();
		SimpleEventListener second = new SimpleEventListener();
		eventController.register(first);

		eventController.call(new SimpleEvent());
		eventController.register(second);
		eventController.call(new SimpleEvent());

		assertEquals(2, first.events.get());
		assertEquals(1, second.events.get());
	}

	@DisplayName("Dispatch plan cache is invalidated when listeners are unregistered at runtime")
	@Test
	void unregisterInvalidatesCachedDispatchPlan() {
		EventController eventController = new EventController();
		SimpleEventListener first = new SimpleEventListener();
		SimpleEventListener second = new SimpleEventListener();
		eventController.register(first);
		eventController.register(second);

		eventController.call(new SimpleEvent());
		eventController.unregister(second);
		eventController.call(new SimpleEvent());

		assertEquals(2, first.events.get());
		assertEquals(1, second.events.get());
	}

	@DisplayName("Listener removal during dispatch affects the next event rather than the in-flight snapshot")
	@Test
	void unregisterDuringDispatchUsesCurrentSnapshot() {
		EventController eventController = new EventController();
		SimpleEventListener target = new SimpleEventListener(EventOrder.HIGH);
		RemovingListener remover = new RemovingListener(eventController, target);
		eventController.register(remover);
		eventController.register(target);

		eventController.call(new SimpleEvent());
		eventController.call(new SimpleEvent());

		assertEquals(2, remover.events.get());
		assertEquals(1, target.events.get());
	}

	private RoutingIntent intent() {
		UUID connectionId = UUID.randomUUID();
		return new RoutingIntent(
				UUID.randomUUID(),
				connectionId,
				new RoutingEndpoint("auth"),
				RoutingReason.STEP,
				RoutingAttemptPolicy.defaultStep(),
				new RoutingAttemptState(),
				PipelineType.AUTHENTICATION,
				null,
				null,
				null,
				System.currentTimeMillis()
		);
	}

	private AuthContext authContext(UUID connectionId, UUID accountId) {
		return AuthContext.builder()
				.connectionUniqueId(connectionId)
				.identity(new ConnectionIdentity(accountId, "PlayerOne", "127.0.0.1"))
				.intendedServer("auth")
				.build();
	}

	private RegistrationContext registrationContext(UUID connectionId, UUID accountId) {
		return RegistrationContext.builder()
				.connectionUniqueId(connectionId)
				.identity(new ConnectionIdentity(accountId, "PlayerOne", "127.0.0.1"))
				.intendedServer("register")
				.build();
	}

	private MigrationContext migrationContext(UUID connectionId, UUID accountId) {
		return MigrationContext.builder()
				.connectionUniqueId(connectionId)
				.identity(new ConnectionIdentity(accountId, "PlayerOne", "127.0.0.1"))
				.targetProviderId("premium")
				.build();
	}

	private static class RoutingListener implements EventListener {
		private final AtomicInteger intentEvents = new AtomicInteger();

		@IdenticEvent
		public void onRoutingIntent(RoutingIntentEvent event) {
			intentEvents.incrementAndGet();
		}
	}

	private static class ScenarioRequiredListener implements EventListener {
		private final AtomicInteger requiredEvents = new AtomicInteger();

		@IdenticEvent
		public void onScenarioRequired(ScenarioRequiredEvent event) {
			requiredEvents.incrementAndGet();
		}
	}

	private static class ScenarioResolvedListener implements EventListener {
		private final AtomicInteger resolvedEvents = new AtomicInteger();

		@IdenticEvent
		public void onScenarioResolved(ScenarioResolvedEvent event) {
			resolvedEvents.incrementAndGet();
		}
	}

	private static final class SimpleEvent implements SynchronousEvent {
	}

	private static class SimpleEventListener implements EventListener {
		private final AtomicInteger events = new AtomicInteger();
		private final EventOrder order;

		private SimpleEventListener() {
			this(EventOrder.NORMAL);
		}

		private SimpleEventListener(EventOrder order) {
			this.order = order;
		}

		@IdenticEvent(EventOrder.HIGH)
		public void onSimpleHigh(SimpleEvent event) {
			if (order != EventOrder.HIGH) return;
			events.incrementAndGet();
		}

		@IdenticEvent
		public void onSimple(SimpleEvent event) {
			if (order != EventOrder.NORMAL) return;
			events.incrementAndGet();
		}
	}

	private static final class RemovingListener implements EventListener {
		private final AtomicInteger events = new AtomicInteger();
		private final EventController eventController;
		private final EventListener target;

		private RemovingListener(EventController eventController, EventListener target) {
			this.eventController = eventController;
			this.target = target;
		}

		@IdenticEvent(EventOrder.LOWEST)
		public void onSimple(SimpleEvent event) {
			events.incrementAndGet();
			eventController.unregister(target);
		}
	}
}
