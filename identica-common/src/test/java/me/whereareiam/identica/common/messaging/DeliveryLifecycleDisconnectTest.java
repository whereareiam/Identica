package me.whereareiam.identica.common.messaging;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.service.DeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Delivery Lifecycle Disconnect")
class DeliveryLifecycleDisconnectTest {
	@DisplayName("Connection disconnect invalidates only connection-scoped deliveries")
	@Test
	void connectionDisconnectInvalidatesOnlyConnectionScopedDeliveries() {
		DeliveryService deliveryService = mock(DeliveryService.class);
		IdentityService identityService = mock(IdentityService.class);
		EventManager eventManager = mock(EventManager.class);
		DeliveryLifecycle lifecycle = new DeliveryLifecycle(deliveryService, identityService, eventManager);
		UUID connectionUniqueId = UUID.randomUUID();

		lifecycle.onConnectionDisconnected(new ConnectionDisconnectedEvent(connectionUniqueId, null, null));

		verify(deliveryService).invalidateByConnection(connectionUniqueId, "connection-disconnected");
	}
}
