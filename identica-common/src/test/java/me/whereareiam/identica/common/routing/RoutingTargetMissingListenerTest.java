package me.whereareiam.identica.common.routing;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.common.config.defaults.messages.MessagesDefaults;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.routing.RoutingTargetMissingEvent;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;
import me.whereareiam.identica.model.routing.RoutingEndpoint;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptState;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.routing.reason.RoutingReason;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Routing Target Missing Listener")
class RoutingTargetMissingListenerTest {
	private static final String SHARED = "No target server available";

	private Messages messages;
	private RoutingTargetMissingListener listener;

	@BeforeAll
	static void initializeSerializer() {
		SerializerEngine engine = mock(SerializerEngine.class);
		when(engine.serialize(any(SerializerContent.class)))
				.thenAnswer(call -> Component.text(call.getArgument(0, SerializerContent.class).getMessage()));
		Serializer.initialize(() -> engine);
	}

	@BeforeEach
	void defaultMessages() {
		messages = new MessagesDefaults().supply(new Messages());
		messages.getRouting().setMissingServer(List.of(SHARED));
		listener = new RoutingTargetMissingListener(() -> messages, mock(EventManager.class));
	}

	@DisplayName("A scenario with its own message disconnects with it")
	@ParameterizedTest(name = "{0}")
	@EnumSource(PipelineType.class)
	void scenarioMessageIsShown(PipelineType scenario) {
		scenarioRouting(scenario).setMissingServer(List.of(scenario.name(), "missing"));

		RoutingTargetMissingEvent event = missing(scenario);
		listener.onTargetMissing(event);

		assertTrue(event.isDisconnect());
		assertEquals(Component.text(scenario.name() + "\nmissing"), event.getMessage());
	}

	@DisplayName("A scenario without a message of its own shows the shared one")
	@ParameterizedTest(name = "{0}")
	@EnumSource(PipelineType.class)
	void emptyScenarioMessageFallsBackToShared(PipelineType scenario) {
		RoutingTargetMissingEvent event = missing(scenario);
		listener.onTargetMissing(event);

		assertEquals(Component.text(SHARED), event.getMessage());
	}

	@DisplayName("A scenario message of blank lines shows the shared one")
	@Test
	void blankScenarioMessageFallsBackToShared() {
		scenarioRouting(PipelineType.REGISTRATION).setMissingServer(List.of("", ""));

		RoutingTargetMissingEvent event = missing(PipelineType.REGISTRATION);
		listener.onTargetMissing(event);

		assertEquals(Component.text(SHARED), event.getMessage());
	}

	@DisplayName("A route outside any scenario shows the shared message")
	@Test
	void routeWithoutScenarioShowsShared() {
		scenarioRouting(PipelineType.AUTHENTICATION).setMissingServer(List.of("authentication"));

		RoutingTargetMissingEvent event = missing(null);
		listener.onTargetMissing(event);

		assertEquals(Component.text(SHARED), event.getMessage());
	}

	@DisplayName("With both messages empty the player is disconnected without a reason")
	@Test
	void bothEmptyDisconnectsWithoutReason() {
		messages.getRouting().setMissingServer(List.of());

		RoutingTargetMissingEvent event = missing(PipelineType.MIGRATION);
		listener.onTargetMissing(event);

		assertTrue(event.isDisconnect());
		assertEquals(Component.empty(), event.getMessage());
	}

	@DisplayName("A reason another listener already set is kept")
	@Test
	void earlierReasonIsKept() {
		scenarioRouting(PipelineType.AUTHENTICATION).setMissingServer(List.of("authentication"));
		RoutingTargetMissingEvent event = missing(PipelineType.AUTHENTICATION);
		event.setMessage(Component.text("earlier"));

		listener.onTargetMissing(event);

		assertEquals(Component.text("earlier"), event.getMessage());
	}

	private Messages.Scenarios.Scenario.ScenarioRouting scenarioRouting(PipelineType scenario) {
		Messages.Scenarios scenarios = messages.getScenarios();
		return switch (scenario) {
			case AUTHENTICATION -> scenarios.getAuthentication().getRouting();
			case REGISTRATION -> scenarios.getRegistration().getRouting();
			case MIGRATION -> scenarios.getMigration().getRouting();
		};
	}

	private static RoutingTargetMissingEvent missing(PipelineType scenario) {
		UUID connection = UUID.randomUUID();
		RoutingIntent intent = new RoutingIntent(
				UUID.randomUUID(),
				connection,
				new RoutingEndpoint("lobby"),
				RoutingReason.STEP,
				RoutingAttemptPolicy.defaultStep(),
				new RoutingAttemptState(),
				scenario,
				null,
				null,
				null,
				System.currentTimeMillis()
		);

		return new RoutingTargetMissingEvent(connection, "Alice", intent);
	}
}
