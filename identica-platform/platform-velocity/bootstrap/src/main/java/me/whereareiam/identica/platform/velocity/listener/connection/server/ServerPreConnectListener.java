package me.whereareiam.identica.platform.velocity.listener.connection.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
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
public class ServerPreConnectListener implements DynamicListener<ServerPreConnectEvent> {
	private final ProxyServer proxyServer;
	private final RoutingAttemptService routingAttemptService;

	@Override
	public void onEvent(ServerPreConnectEvent event) {
		UUID connectionId = event.getPlayer().getUniqueId();
		RoutingIntent currentIntent = routingAttemptService.current(connectionId).orElse(null);
		if (currentIntent == null) {
			Logger.debug("Velocity pre-connect routing skipped player=%s username=%s original=%s reason=no-intent",
					connectionId,
					event.getPlayer().getUsername(),
					event.getOriginalServer().getServerInfo().getName());
			return;
		}
		String targetServer = currentIntent.getEndpoint().getServer();
		if (targetServer.isBlank()) {
			Logger.debug("Velocity pre-connect routing skipped player=%s reason=blank-target",
					connectionId);
			return;
		}
		if (event.getOriginalServer().getServerInfo().getName().equalsIgnoreCase(targetServer)) {
			Logger.debug("Velocity pre-connect routing already targeted player=%s target=%s",
					connectionId, targetServer);
			return;
		}

		String currentServer = event.getPlayer().getCurrentServer()
				.map(server -> server.getServerInfo().getName())
				.orElse(null);
		RoutingAttemptDecision decision = routingAttemptService.decide(new RoutingAttemptRequest(
				connectionId,
				RoutingAttemptTrigger.PRE_CONNECT,
				currentServer
		));
		if (!decision.isAllowed() || decision.getIntent() == null) {
			Logger.debug("Velocity pre-connect routing skipped player=%s original=%s current=%s target=%s reason=%s exhausted=%s",
					connectionId,
					event.getOriginalServer().getServerInfo().getName(),
					currentServer,
					targetServer,
					decision.getReason(),
					decision.isExhausted());
			return;
		}

		RoutingIntent intent = decision.getIntent();
		targetServer = intent.getEndpoint().getServer();
		if (targetServer.isBlank()) return;

		Optional<RegisteredServer> server = proxyServer.getServer(targetServer);
		if (server.isEmpty()) {
			Logger.debug("Velocity pre-connect routing target missing player=%s target=%s",
					connectionId, targetServer);
			routingAttemptService.record(RoutingAttemptReport.failed(
					connectionId,
					RoutingAttemptTrigger.PRE_CONNECT,
					targetServer,
					RoutingAttemptFailureReason.MISSING_SERVER
			));
			return;
		}

		event.setResult(ServerPreConnectEvent.ServerResult.allowed(server.get()));
		Logger.debug("Velocity pre-connect routing applied player=%s original=%s target=%s",
				connectionId,
				event.getOriginalServer().getServerInfo().getName(),
				targetServer);
		routingAttemptService.record(RoutingAttemptReport.succeeded(
				connectionId,
				RoutingAttemptTrigger.PRE_CONNECT,
				targetServer
		));
	}
}
