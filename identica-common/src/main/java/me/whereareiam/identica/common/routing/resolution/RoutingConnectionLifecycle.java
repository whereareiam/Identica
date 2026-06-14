package me.whereareiam.identica.common.routing.resolution;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionTerminatedEvent;
import me.whereareiam.identica.routing.RoutingCoordinator;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Singleton
public class RoutingConnectionLifecycle implements EventListener {
	private final RoutingCoordinator routingCoordinator;

	@Inject
	public RoutingConnectionLifecycle(
			@NotNull RoutingCoordinator routingCoordinator,
			@NotNull EventManager eventManager
	) {
		this.routingCoordinator = routingCoordinator;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onConnectionDisconnected(@NotNull ConnectionDisconnectedEvent event) {
		clear(event.getConnectionUniqueId());
	}

	@IdenticEvent
	public void onConnectionTerminated(@NotNull ConnectionTerminatedEvent event) {
		clear(event.getConnectionUniqueId());
	}

	private void clear(@NotNull UUID connectionUniqueId) {
		routingCoordinator.clear(connectionUniqueId);
	}
}
