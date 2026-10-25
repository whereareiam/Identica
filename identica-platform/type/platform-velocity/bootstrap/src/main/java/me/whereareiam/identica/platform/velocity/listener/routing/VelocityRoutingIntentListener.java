package me.whereareiam.identica.platform.velocity.listener.routing;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.routing.RoutingTargetMissingEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentRetryEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentStartedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentUpdatedEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptDecision;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptReport;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptRequest;
import me.whereareiam.identica.routing.RoutingAttemptService;
import me.whereareiam.identica.type.routing.RoutingAttemptTrigger;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Singleton
public class VelocityRoutingIntentListener implements EventListener {
	private final ProxyServer proxyServer;
	private final RoutingAttemptService routingAttemptService;
	private final EventManager eventManager;
	private final MessageService messageService;

	@Inject
	public VelocityRoutingIntentListener(
			@NotNull ProxyServer proxyServer,
			@NotNull RoutingAttemptService routingAttemptService,
			@NotNull EventManager eventManager,
			@NotNull MessageService messageService
	) {
		this.proxyServer = proxyServer;
		this.routingAttemptService = routingAttemptService;
		this.eventManager = eventManager;
		this.messageService = messageService;
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
		Player player = proxyServer.getPlayer(intent.getConnectionUniqueId()).orElse(null);
		if (player == null) {
			Logger.debug("Velocity routing intent %s skipped connection=%s target=%s reason=player-offline",
					source, intent.getConnectionUniqueId(), intent.getEndpoint().getServer());
			return;
		}

		String currentServer = player.getCurrentServer()
				.map(server -> server.getServerInfo().getName())
				.orElse(null);
		if (currentServer == null) {
			Logger.debug("Velocity routing intent %s skipped player=%s target=%s reason=no-current-server",
					source, player.getUniqueId(), intent.getEndpoint().getServer());
			return;
		}

		RoutingAttemptDecision decision = routingAttemptService.decide(new RoutingAttemptRequest(
				player.getUniqueId(),
				trigger,
				currentServer
		));
		if (!decision.isAllowed() || decision.getIntent() == null) {
			Logger.debug("Velocity routing intent %s skipped player=%s current=%s target=%s reason=%s exhausted=%s",
					source,
					player.getUniqueId(),
					currentServer,
					intent.getEndpoint().getServer(),
					decision.getReason(),
					decision.isExhausted());
			return;
		}

		RoutingIntent currentIntent = decision.getIntent();
		String targetServer = currentIntent.getEndpoint().getServer();
		if (targetServer.isBlank()) {
			Logger.debug("Velocity routing intent %s skipped player=%s reason=blank-target",
					source, player.getUniqueId());
			return;
		}

		Optional<RegisteredServer> server = proxyServer.getServer(targetServer);
		if (server.isEmpty()) {
			Logger.debug("Velocity routing intent %s target missing player=%s target=%s",
					source, player.getUniqueId(), targetServer);
			RoutingTargetMissingEvent missingEvent = new RoutingTargetMissingEvent(
					player.getUniqueId(),
					player.getUsername(),
					currentIntent
			);
			eventManager.call(missingEvent);
			if (missingEvent.isDisconnect() && missingEvent.getMessage() != null)
				player.disconnect(messageService.disconnectReason(null, missingEvent.getMessage()));

			routingAttemptService.record(RoutingAttemptReport.failed(
					player.getUniqueId(),
					trigger,
					targetServer,
					RoutingAttemptFailureReason.MISSING_SERVER
			));
			return;
		}

		Logger.debug("Velocity routing intent %s applying player=%s current=%s target=%s",
				source, player.getUniqueId(), currentServer, targetServer);
		ConnectionRequestBuilder request = player.createConnectionRequest(server.get());
		if (isLastAttempt(currentIntent)) {
			// Velocity tells the player why a server refused them, and fires KickedFromServerEvent for other plugins,
			// only for a connection it handles itself. The last attempt therefore leaves a refusal to Velocity.
			request.connectWithIndication()
					.whenComplete((connected, throwable) -> handleIndicatedResult(player, targetServer, trigger, connected, throwable));
			return;
		}

		request.connect()
				.whenComplete((result, throwable) -> handleAsyncResult(player, targetServer, trigger, result, throwable));
	}

	private static boolean isLastAttempt(@NotNull RoutingIntent intent) {
		return !intent.getAttemptPolicy().allowsAttempt(intent.getAttemptState().getAttempts() + 1);
	}

	private void handleIndicatedResult(
			@NotNull Player player,
			@NotNull String targetServer,
			@NotNull RoutingAttemptTrigger trigger,
			Boolean connected,
			Throwable throwable
	) {
		if (throwable != null) {
			Logger.debug("Velocity routing last connect failed player=%s target=%s trigger=%s reason=%s",
					player.getUniqueId(), targetServer, trigger, throwable.toString());
			recordAttempt(player, trigger, false, targetServer, RoutingAttemptFailureReason.CONNECTION_EXCEPTION);
			return;
		}
		if (connected == null) {
			recordAttempt(player, trigger, false, targetServer, RoutingAttemptFailureReason.CONNECTION_RESULT_MISSING);
			return;
		}

		Logger.debug("Velocity routing last connect finished player=%s target=%s trigger=%s connected=%s",
				player.getUniqueId(), targetServer, trigger, connected);
		// Velocity only says whether the player arrived; it has already handled any refusal itself.
		if (connected) {
			recordAttempt(player, trigger, true, targetServer, null);
			return;
		}

		recordAttempt(player, trigger, false, targetServer, RoutingAttemptFailureReason.SERVER_DISCONNECTED);
	}

	private void handleAsyncResult(
			@NotNull Player player,
			@NotNull String targetServer,
			@NotNull RoutingAttemptTrigger trigger,
			ConnectionRequestBuilder.Result result,
			Throwable throwable
	) {
		if (throwable != null) {
			Logger.debug("Velocity routing async connect failed player=%s target=%s trigger=%s reason=%s",
					player.getUniqueId(), targetServer, trigger, throwable.toString());
			recordAttempt(player, trigger, false, targetServer, RoutingAttemptFailureReason.CONNECTION_EXCEPTION);
			return;
		}
		if (result == null) {
			recordAttempt(player, trigger, false, targetServer, RoutingAttemptFailureReason.CONNECTION_RESULT_MISSING);
			return;
		}

		ConnectionRequestBuilder.Status status = result.getStatus();
		Logger.debug("Velocity routing async connect finished player=%s target=%s trigger=%s status=%s",
				player.getUniqueId(), targetServer, trigger, status);
		switch (status) {
			case SUCCESS, ALREADY_CONNECTED -> recordAttempt(player, trigger, true, targetServer, null);
			case SERVER_DISCONNECTED -> recordAttempt(player, trigger, false, targetServer, RoutingAttemptFailureReason.SERVER_DISCONNECTED);
			case CONNECTION_CANCELLED -> recordAttempt(player, trigger, false, targetServer, RoutingAttemptFailureReason.CONNECTION_CANCELLED);
			case CONNECTION_IN_PROGRESS -> recordAttempt(player, trigger, false, targetServer, RoutingAttemptFailureReason.CONNECTION_IN_PROGRESS);
		}
	}

	private void recordAttempt(
			@NotNull Player player,
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
