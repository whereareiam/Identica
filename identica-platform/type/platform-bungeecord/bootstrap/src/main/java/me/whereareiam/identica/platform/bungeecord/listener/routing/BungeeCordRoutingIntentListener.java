package me.whereareiam.identica.platform.bungeecord.listener.routing;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.ServerJoinedEvent;
import me.whereareiam.identica.event.routing.RoutingTargetMissingEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentRetryEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentStartedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentUpdatedEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptDecision;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptReport;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptRequest;
import me.whereareiam.identica.platform.bungeecord.util.BaseComponentMapper;
import me.whereareiam.identica.routing.RoutingAttemptService;
import me.whereareiam.identica.type.routing.RoutingAttemptTrigger;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerConnectRequest;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connects players to the server their routing intent names. BungeeCord switches a player only once their client has
 * entered a server's world: a switch while the client still configures its first server, such as a player a
 * checkpoint holds on the loading screen, leaves the next server waiting for a login that never comes. Such an
 * intent waits until the player has entered the first server's world.
 */
@Singleton
public class BungeeCordRoutingIntentListener implements EventListener {
	private final ProxyServer proxyServer;
	private final RoutingAttemptService routingAttemptService;
	private final EventManager eventManager;
	private final Set<UUID> inWorld = ConcurrentHashMap.newKeySet();
	private final Set<UUID> waiting = ConcurrentHashMap.newKeySet();

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

	/**
	 * Applies the intent that waited for the player's client to enter its first server's world.
	 */
	@IdenticEvent
	public void onServerJoined(@NotNull ServerJoinedEvent event) {
		UUID connectionUniqueId = event.getIdentity().getConnectionUniqueId();
		if (!waiting.remove(connectionUniqueId)) return;

		routingAttemptService.current(connectionUniqueId).ifPresent(intent -> apply(intent, "joined"));
	}

	/**
	 * Records that BungeeCord hands a player a server's join, from {@code ServerConnectedEvent}: from now on the
	 * player can be switched.
	 *
	 * @param connectionUniqueId the player's connection
	 */
	public void entersWorld(@NotNull UUID connectionUniqueId) {
		inWorld.add(connectionUniqueId);
	}

	/**
	 * Forgets a player who left.
	 *
	 * @param connectionUniqueId the player's connection
	 */
	public void left(@NotNull UUID connectionUniqueId) {
		inWorld.remove(connectionUniqueId);
		waiting.remove(connectionUniqueId);
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
		if (!inWorld.contains(player.getUniqueId())) {
			waiting.add(player.getUniqueId());
			Logger.debug("Bungee routing intent %s waits until the client entered a world player=%s current=%s target=%s",
					source, player.getUniqueId(), currentServer, intent.getEndpoint().getServer());
			return;
		}

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
				player.disconnect(BaseComponentMapper.map(missingEvent.getMessage()));

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
