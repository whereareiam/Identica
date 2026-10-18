package me.whereareiam.identica.common.messaging;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.connection.ServerJoinedEvent;
import me.whereareiam.identica.type.ScenarioResolution;
import me.whereareiam.identica.event.scenario.authentication.AuthenticationResolvedEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.delivery.DeliveryRequest;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.type.messaging.DeliverySource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@DisplayName("Delivery Lifecycle")
class DeliveryLifecycleTest {
	@DisplayName("Scenario resolution acknowledges initial prompt deliveries")
	@Test
	void scenarioResolvedAcknowledgesInitialPromptDeliveries() {
		DeliveryService deliveryService = mock(DeliveryService.class);
		EventManager eventManager = mock(EventManager.class);
		DeliveryLifecycle lifecycle = new DeliveryLifecycle(deliveryService, eventManager);
		UUID connectionUniqueId = UUID.randomUUID();
		UUID promptId = UUID.randomUUID();
		UUID ignoredId = UUID.randomUUID();
		DeliveryRequest prompt = mock(DeliveryRequest.class);
		DeliveryRequest ignored = mock(DeliveryRequest.class);

		when(prompt.getId()).thenReturn(promptId);
		when(prompt.getSource()).thenReturn(DeliverySource.INITIAL_PROMPT);
		when(ignored.getId()).thenReturn(ignoredId);
		when(ignored.getSource()).thenReturn(DeliverySource.NOTICE);

		when(deliveryService.pendingForConnection(connectionUniqueId)).thenReturn(List.of(prompt, ignored));

		lifecycle.onScenarioResolved(new AuthenticationResolvedEvent(
				connectionUniqueId,
				UUID.randomUUID(),
				AuthContext.builder()
						.connectionUniqueId(connectionUniqueId)
						.identity(new ConnectionIdentity("PlayerOne", "127.0.0.1"))
						.build(),
				ScenarioResolution.CANCELLED,
				false
		));

		verify(deliveryService).acknowledge(promptId, "scenario-resolved");
		verify(deliveryService, never()).acknowledge(eq(ignoredId), anyString());
	}

	@DisplayName("Joining a server dispatches the deliveries waiting for it")
	@Test
	void serverJoinedDispatchesDeliveries() {
		DeliveryService deliveryService = mock(DeliveryService.class);
		DeliveryLifecycle lifecycle = new DeliveryLifecycle(deliveryService, mock(EventManager.class));
		Identity identity = mock(Identity.class);

		lifecycle.onServerJoined(new ServerJoinedEvent(identity, "auth"));

		verify(deliveryService).dispatch(argThat(context ->
				context.getIdentity() == identity && "auth".equals(context.getServer())));
	}
}
