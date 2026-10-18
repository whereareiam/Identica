package me.whereareiam.identica.common.messaging;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.ServerJoinedEvent;
import me.whereareiam.identica.event.scenario.ScenarioResolvedEvent;
import me.whereareiam.identica.model.delivery.DeliveryDispatchContext;
import me.whereareiam.identica.model.delivery.DeliveryRequest;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.type.messaging.DeliverySource;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Singleton
public class DeliveryLifecycle implements EventListener {
	private final DeliveryService deliveryService;

	@Inject
	public DeliveryLifecycle(
			@NotNull DeliveryService deliveryService,
			@NotNull EventManager eventManager
	) {
		this.deliveryService = deliveryService;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onServerJoined(@NotNull ServerJoinedEvent event) {
		deliveryService.dispatch(DeliveryDispatchContext.builder()
				.identity(event.getIdentity())
				.server(event.getServer())
				.build());
	}

	@IdenticEvent
	public void onScenarioResolved(@NotNull ScenarioResolvedEvent event) {
		UUID connectionUniqueId = event.getConnectionUniqueId();
		// Completion deliveries are queued just before successful pipelines clear their state.
		for (DeliveryRequest request : deliveryService.pendingForConnection(connectionUniqueId)) {
			if (request.getSource() != DeliverySource.INITIAL_PROMPT)
				continue;

			deliveryService.acknowledge(request.getId(), "scenario-resolved");
		}
	}
}
