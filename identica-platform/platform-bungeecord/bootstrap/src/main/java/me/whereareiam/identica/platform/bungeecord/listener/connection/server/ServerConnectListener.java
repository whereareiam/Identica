package me.whereareiam.identica.platform.bungeecord.listener.connection.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;
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
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.ServerConnectEvent;

import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ServerConnectListener implements DynamicListener<ServerConnectEvent> {
	private final ProxyServer proxyServer;
	private final RoutingAttemptService routingAttemptService;

	@Override
	public void onEvent(ServerConnectEvent event) {
		UUID connectionId = event.getPlayer().getUniqueId();
		RoutingIntent currentIntent = routingAttemptService.current(connectionId).orElse(null);
		if (currentIntent == null) return;

		String targetServer = currentIntent.getEndpoint().getServer();
		if (targetServer.isBlank()) return;
		if (event.getTarget().getName().equalsIgnoreCase(targetServer)) return;

		String currentServer = event.getPlayer().getServer() != null
				? event.getPlayer().getServer().getInfo().getName()
				: null;
		RoutingAttemptDecision decision = routingAttemptService.decide(new RoutingAttemptRequest(
				connectionId,
				currentServer == null
						? RoutingAttemptTrigger.INITIAL_SERVER
						: RoutingAttemptTrigger.PRE_CONNECT,
				currentServer
		));
		if (!decision.isAllowed() || decision.getIntent() == null) return;

		RoutingIntent intent = decision.getIntent();
		targetServer = intent.getEndpoint().getServer();
		if (targetServer.isBlank()) return;

		ServerInfo server = proxyServer.getServerInfo(targetServer);
		if (server == null) {
			routingAttemptService.record(RoutingAttemptReport.failed(
					connectionId,
					currentServer == null
							? RoutingAttemptTrigger.INITIAL_SERVER
							: RoutingAttemptTrigger.PRE_CONNECT,
					targetServer,
					RoutingAttemptFailureReason.MISSING_SERVER
			));
			return;
		}

		event.setTarget(server);
		routingAttemptService.record(RoutingAttemptReport.succeeded(
				connectionId,
				currentServer == null
						? RoutingAttemptTrigger.INITIAL_SERVER
						: RoutingAttemptTrigger.PRE_CONNECT,
				targetServer
		));
		Logger.debug("Bungee server-connect routing applied player=%s target=%s", connectionId, targetServer);
	}
}
