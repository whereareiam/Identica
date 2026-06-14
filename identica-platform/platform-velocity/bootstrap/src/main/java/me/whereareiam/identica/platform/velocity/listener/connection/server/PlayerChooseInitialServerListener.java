package me.whereareiam.identica.platform.velocity.listener.connection.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptDecision;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptReport;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptRequest;
import me.whereareiam.identica.routing.RoutingAttemptService;
import me.whereareiam.identica.type.routing.RoutingAttemptTrigger;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;

import java.util.Optional;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PlayerChooseInitialServerListener implements DynamicListener<PlayerChooseInitialServerEvent> {
	private final ProxyServer proxyServer;
	private final RoutingAttemptService routingAttemptService;

	@Override
	public void onEvent(PlayerChooseInitialServerEvent event) {
		UUID connectionId = event.getPlayer().getUniqueId();
		Logger.debug("Velocity initial server routing check player=%s username=%s",
				connectionId, event.getPlayer().getUsername());
		RoutingAttemptDecision decision = routingAttemptService.decide(new RoutingAttemptRequest(
				connectionId,
				RoutingAttemptTrigger.INITIAL_SERVER,
				null
		));
		if (!decision.isAllowed() || decision.getIntent() == null) {
			Logger.debug("Velocity initial server routing skipped player=%s reason=%s exhausted=%s",
					connectionId, decision.getReason(), decision.isExhausted());
			return;
		}

		RoutingIntent intent = decision.getIntent();
		String targetServer = intent.getEndpoint().getServer();
		if (targetServer.isBlank()) {
			Logger.debug("Velocity initial server routing skipped player=%s reason=blank-target", connectionId);
			return;
		}

		Optional<RegisteredServer> server = proxyServer.getServer(targetServer);
		if (server.isEmpty()) {
			Logger.debug("Velocity initial server routing target missing player=%s target=%s",
					connectionId, targetServer);
			routingAttemptService.record(RoutingAttemptReport.failed(
					connectionId,
					RoutingAttemptTrigger.INITIAL_SERVER,
					targetServer,
					RoutingAttemptFailureReason.MISSING_SERVER
			));
			return;
		}

		event.setInitialServer(server.get());
		Logger.debug("Velocity initial server routing applied player=%s target=%s",
				connectionId, targetServer);
		routingAttemptService.record(RoutingAttemptReport.succeeded(
				connectionId,
				RoutingAttemptTrigger.INITIAL_SERVER,
				targetServer
		));
	}
}
