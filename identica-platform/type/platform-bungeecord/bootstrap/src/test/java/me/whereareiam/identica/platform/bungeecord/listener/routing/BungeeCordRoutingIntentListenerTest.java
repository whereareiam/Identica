package me.whereareiam.identica.platform.bungeecord.listener.routing;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.connection.ServerJoinedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentStartedEvent;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.routing.RoutingEndpoint;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptDecision;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptState;
import me.whereareiam.identica.routing.RoutingAttemptService;
import me.whereareiam.identica.type.routing.reason.RoutingReason;
import net.md_5.bungee.api.ProxyConfig;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerConnectRequest;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BungeeCord Routing Intent Listener")
class BungeeCordRoutingIntentListenerTest {
	private static final UUID ALICE = UUID.nameUUIDFromBytes("Alice".getBytes());

	private final ProxyServer proxy = mock(ProxyServer.class);
	private final RoutingAttemptService attempts = mock(RoutingAttemptService.class);
	private final ProxiedPlayer player = mock(ProxiedPlayer.class);
	private final RoutingIntent toAuth = new RoutingIntent(UUID.randomUUID(), ALICE, new RoutingEndpoint("auth"),
			RoutingReason.COMPLETION, RoutingAttemptPolicy.defaultCompletion(), new RoutingAttemptState(), null, null, null, null, 0L);
	private final BungeeCordRoutingIntentListener listener = new BungeeCordRoutingIntentListener(proxy, attempts, mock(EventManager.class));

	/**
	 * Builds a connect request with BungeeCord's configured timeout, which the running proxy provides.
	 */
	@BeforeAll
	static void runningProxy() {
		if (ProxyServer.getInstance() != null) return;

		ProxyServer running = mock(ProxyServer.class);
		ProxyConfig config = mock(ProxyConfig.class);
		when(running.getConfig()).thenReturn(config);
		when(config.getTimeout()).thenReturn(5000);
		ProxyServer.setInstance(running);
	}

	@BeforeEach
	void aliceConfiguresWithTheLobby() {
		when(proxy.getPlayer(ALICE)).thenReturn(player);
		when(player.getUniqueId()).thenReturn(ALICE);
		ServerInfo lobby = mock(ServerInfo.class);
		when(lobby.getName()).thenReturn("lobby");
		Server connected = mock(Server.class);
		when(connected.getInfo()).thenReturn(lobby);
		when(player.getServer()).thenReturn(connected);
		when(proxy.getServerInfo("auth")).thenReturn(mock(ServerInfo.class));
		when(attempts.decide(any())).thenReturn(RoutingAttemptDecision.allowed(toAuth));
		when(attempts.current(ALICE)).thenReturn(Optional.of(toAuth));
	}

	@DisplayName("Switches a player only once their client entered the first server's world")
	@Test
	void waitsForTheFirstWorldBeforeSwitching() {
		listener.onRoutingIntentStarted(new RoutingIntentStartedEvent(toAuth));
		verify(player, never()).connect(any(ServerConnectRequest.class));

		listener.entersWorld(ALICE);
		listener.onServerJoined(joined());

		verify(player).connect(any(ServerConnectRequest.class));
	}

	@DisplayName("Switches a player who is in a server's world at once")
	@Test
	void switchesAPlayerInAWorld() {
		listener.entersWorld(ALICE);

		listener.onRoutingIntentStarted(new RoutingIntentStartedEvent(toAuth));

		verify(player).connect(any(ServerConnectRequest.class));
	}

	@DisplayName("Forgets the waiting intent of a player who left")
	@Test
	void forgetsAPlayerWhoLeft() {
		listener.onRoutingIntentStarted(new RoutingIntentStartedEvent(toAuth));
		listener.left(ALICE);

		listener.onServerJoined(joined());

		verify(player, never()).connect(any(ServerConnectRequest.class));
	}

	private static ServerJoinedEvent joined() {
		Identity identity = mock(Identity.class);
		when(identity.getConnectionUniqueId()).thenReturn(ALICE);
		return new ServerJoinedEvent(identity, "lobby");
	}
}
