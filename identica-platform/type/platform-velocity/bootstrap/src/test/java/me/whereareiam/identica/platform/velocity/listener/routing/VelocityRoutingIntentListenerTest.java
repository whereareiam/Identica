package me.whereareiam.identica.platform.velocity.listener.routing;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import me.whereareiam.identica.event.routing.intent.RoutingIntentRetryEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentStartedEvent;
import me.whereareiam.identica.model.routing.RoutingEndpoint;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptDecision;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptReport;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptState;
import me.whereareiam.identica.routing.RoutingAttemptService;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import me.whereareiam.identica.type.routing.RoutingAttemptTrigger;
import me.whereareiam.identica.type.routing.reason.RoutingReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Velocity Routing Intent Listener")
class VelocityRoutingIntentListenerTest {
	@DisplayName("Records failed async connect results instead of treating them as accepted")
	@Test
	void failedAsyncConnectIsRecordedAsRejected() {
		ProxyServer proxyServer = mock(ProxyServer.class);
		RoutingAttemptService routingAttemptService = mock(RoutingAttemptService.class);
		me.whereareiam.identica.common.event.EventController eventController = new me.whereareiam.identica.common.event.EventController();
		new VelocityRoutingIntentListener(proxyServer, routingAttemptService, eventController);

		RoutingIntent intent = intent();
		Player player = player(intent.getConnectionUniqueId(), "auth");
		RegisteredServer registeredServer = registeredServer("lobby");
		ConnectionRequestBuilder connectionRequest = mock(ConnectionRequestBuilder.class);
		ConnectionRequestBuilder.Result result = mock(ConnectionRequestBuilder.Result.class);

		when(proxyServer.getPlayer(intent.getConnectionUniqueId())).thenReturn(Optional.of(player));
		when(proxyServer.getServer("lobby")).thenReturn(Optional.of(registeredServer));
		when(routingAttemptService.decide(any())).thenReturn(RoutingAttemptDecision.allowed(intent));
		when(player.createConnectionRequest(registeredServer)).thenReturn(connectionRequest);
		when(connectionRequest.connect()).thenReturn(CompletableFuture.completedFuture(result));
		when(result.getStatus()).thenReturn(ConnectionRequestBuilder.Status.SERVER_DISCONNECTED);

		eventController.call(new RoutingIntentStartedEvent(intent));

		ArgumentCaptor<RoutingAttemptReport> reportCaptor = ArgumentCaptor.forClass(RoutingAttemptReport.class);
		verify(routingAttemptService).record(reportCaptor.capture());

		RoutingAttemptReport report = reportCaptor.getValue();
		assertEquals(RoutingAttemptTrigger.ASYNC_CONNECT, report.getTrigger());
		assertFalse(report.isAccepted());
		assertEquals("lobby", report.getServer());
		assertEquals(RoutingAttemptFailureReason.SERVER_DISCONNECTED, report.getFailureReason());
	}

	@DisplayName("Uses the scheduled retry trigger when replaying a routing attempt")
	@Test
	void retryEventUsesScheduledRetryTrigger() {
		ProxyServer proxyServer = mock(ProxyServer.class);
		RoutingAttemptService routingAttemptService = mock(RoutingAttemptService.class);
		me.whereareiam.identica.common.event.EventController eventController = new me.whereareiam.identica.common.event.EventController();
		new VelocityRoutingIntentListener(proxyServer, routingAttemptService, eventController);

		RoutingIntent intent = intent();
		Player player = player(intent.getConnectionUniqueId(), "auth");
		RegisteredServer registeredServer = registeredServer("lobby");
		ConnectionRequestBuilder connectionRequest = mock(ConnectionRequestBuilder.class);
		ConnectionRequestBuilder.Result result = mock(ConnectionRequestBuilder.Result.class);

		when(proxyServer.getPlayer(intent.getConnectionUniqueId())).thenReturn(Optional.of(player));
		when(proxyServer.getServer("lobby")).thenReturn(Optional.of(registeredServer));
		when(routingAttemptService.decide(any())).thenReturn(RoutingAttemptDecision.allowed(intent));
		when(player.createConnectionRequest(registeredServer)).thenReturn(connectionRequest);
		when(connectionRequest.connect()).thenReturn(CompletableFuture.completedFuture(result));
		when(result.getStatus()).thenReturn(ConnectionRequestBuilder.Status.SUCCESS);

		eventController.call(new RoutingIntentRetryEvent(intent));

		ArgumentCaptor<RoutingAttemptReport> reportCaptor = ArgumentCaptor.forClass(RoutingAttemptReport.class);
		verify(routingAttemptService).record(reportCaptor.capture());

		RoutingAttemptReport report = reportCaptor.getValue();
		assertEquals(RoutingAttemptTrigger.SCHEDULED_RETRY, report.getTrigger());
		assertEquals("lobby", report.getServer());
		assertNull(report.getFailureReason());
	}

	private RoutingIntent intent() {
		return new RoutingIntent(
				UUID.randomUUID(),
				UUID.randomUUID(),
				new RoutingEndpoint("lobby"),
				RoutingReason.STEP,
				new RoutingAttemptPolicy(),
				new RoutingAttemptState(),
				PipelineType.AUTHENTICATION,
				null,
				null,
				null,
				System.currentTimeMillis()
		);
	}

	private Player player(UUID uniqueId, String currentServerName) {
		Player player = mock(Player.class);
		ServerConnection serverConnection = mock(ServerConnection.class);
		ServerInfo serverInfo = mock(ServerInfo.class);

		when(player.getUniqueId()).thenReturn(uniqueId);
		when(player.getCurrentServer()).thenReturn(Optional.of(serverConnection));
		when(serverConnection.getServerInfo()).thenReturn(serverInfo);
		when(serverInfo.getName()).thenReturn(currentServerName);
		return player;
	}

	private RegisteredServer registeredServer(String name) {
		RegisteredServer server = mock(RegisteredServer.class);
		ServerInfo info = mock(ServerInfo.class);
		when(server.getServerInfo()).thenReturn(info);
		when(info.getName()).thenReturn(name);
		return server;
	}
}
