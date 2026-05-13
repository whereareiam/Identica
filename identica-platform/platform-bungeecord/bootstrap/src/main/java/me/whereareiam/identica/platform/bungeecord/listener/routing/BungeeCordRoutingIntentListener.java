package me.whereareiam.identica.platform.bungeecord.listener.routing;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.routing.RoutingTargetMissingEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentRetryEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentStartedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentUpdatedEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptDecision;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptReport;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptRequest;
import me.whereareiam.identica.routing.RoutingAttemptService;
import me.whereareiam.identica.type.routing.RoutingAttemptTrigger;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerConnectRequest;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.jetbrains.annotations.NotNull;

@Singleton
public class BungeeCordRoutingIntentListener implements EventListener {
	private final ProxyServer proxyServer;
	private final RoutingAttemptService routingAttemptService;
	private final EventManager eventManager;

	@Inject
	public BungeeCordRoutingIntentListener(
			@NotNull ProxyServer proxyServer,
			@NotNull RoutingAttemptService routingAttemptService,
			@NotNull EventManager eventManager
	) {
		this.proxyServer = proxyServer;
		this.routingAttemptService = routingAttemptService;
		this.eventManager = eventManager;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onRoutingIntentStarted(@NotNull RoutingIntentStartedEvent event) {
		apply(event.getIntent(), "started");
	}

	@IdenticEvent
	public void onRoutingIntentUpdated(@NotNull RoutingIntentUpdatedEvent event) {
		apply(event.getIntent(), "updated");
	}

	@IdenticEvent
	public void onRoutingIntentRetry(@NotNull RoutingIntentRetryEvent event) {
		apply(event.getIntent(), "retry", RoutingAttemptTrigger.SCHEDULED_RETRY);
	}

	private void apply(@NotNull RoutingIntent intent, @NotNull String source) {
		apply(intent, source, RoutingAttemptTrigger.ASYNC_CONNECT);
	}

	private void apply(
			@NotNull RoutingIntent intent,
			@NotNull String source,
			@NotNull RoutingAttemptTrigger trigger
	) {
		ProxiedPlayer player = proxyServer.getPlayer(intent.getConnectionUniqueId());
		if (player == null) return;

		String currentServer = player.getServer() != null ? player.getServer().getInfo().getName() : null;
		if (currentServer == null) return;

		RoutingAttemptDecision decision = routingAttemptService.decide(new RoutingAttemptRequest(
				player.getUniqueId(),
				trigger,
				currentServer
		));
		if (!decision.isAllowed() || decision.getIntent() == null) return;

		RoutingIntent currentIntent = decision.getIntent();
		String targetServer = currentIntent.getEndpoint().getServer();
		if (targetServer.isBlank()) return;

		ServerInfo server = proxyServer.getServerInfo(targetServer);
		if (server == null) {
			RoutingTargetMissingEvent missingEvent = new RoutingTargetMissingEvent(
					player.getUniqueId(),
					player.getName(),
					currentIntent
			);
			eventManager.call(missingEvent);
			if (missingEvent.isDisconnect() && missingEvent.getMessage() != null)
				player.disconnect(TextComponent.fromLegacy(""));

			routingAttemptService.record(RoutingAttemptReport.failed(
					player.getUniqueId(),
					trigger,
					targetServer,
					RoutingAttemptFailureReason.MISSING_SERVER
			));
			return;
		}

		player.connect(ServerConnectRequest.builder()
				.target(server)
				.reason(net.md_5.bungee.api.event.ServerConnectEvent.Reason.PLUGIN)
				.callback((result, throwable) -> handleAsyncResult(player, targetServer, trigger, result, throwable))
				.build());
		Logger.debug("Bungee routing intent %s applying player=%s current=%s target=%s",
				source, player.getUniqueId(), currentServer, targetServer);
	}

	private void handleAsyncResult(
			@NotNull ProxiedPlayer player,
			@NotNull String targetServer,
			@NotNull RoutingAttemptTrigger trigger,
			ServerConnectRequest.Result result,
			Throwable throwable
	) {
		if (throwable != null) {
			recordAttempt(player, trigger, false, targetServer, RoutingAttemptFailureReason.CONNECTION_EXCEPTION);
			return;
		}
		if (result == null) {
			recordAttempt(player, trigger, false, targetServer, RoutingAttemptFailureReason.CONNECTION_RESULT_MISSING);
			return;
		}

		switch (result) {
			case SUCCESS, ALREADY_CONNECTED -> recordAttempt(player, trigger, true, targetServer, null);
			case EVENT_CANCEL, FAIL -> recordAttempt(player, trigger, false, targetServer, RoutingAttemptFailureReason.CONNECTION_CANCELLED);
		}
	}

	private void recordAttempt(
			@NotNull ProxiedPlayer player,
			@NotNull RoutingAttemptTrigger trigger,
			boolean accepted,
			@NotNull String targetServer,
			RoutingAttemptFailureReason failureReason
	) {
		RoutingAttemptReport report = accepted
				? RoutingAttemptReport.succeeded(player.getUniqueId(), trigger, targetServer)
				: RoutingAttemptReport.failed(player.getUniqueId(), trigger, targetServer, failureReason);
		routingAttemptService.record(report);
	}
}
