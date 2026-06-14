package me.whereareiam.identica.platform.velocity.adapter;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.whereareiam.identica.model.routing.RoutingConnectionSnapshot;
import me.whereareiam.identica.model.routing.RoutingEndpoint;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptState;
import me.whereareiam.identica.model.routing.execution.RoutingOutcome;
import me.whereareiam.identica.model.routing.execution.RoutingRequest;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.routing.RoutingAttemptTrigger;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import me.whereareiam.identica.type.routing.reason.RoutingReason;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Velocity Routing Adapter")
class VelocityRoutingAdapterTest {
	@DisplayName("Server disconnect results keep the proxy reason text")
	@Test
	void serverDisconnectResultsKeepTheProxyReasonText() {
		ProxyServer proxyServer = mock(ProxyServer.class);
		Player player = mock(Player.class);
		RegisteredServer registeredServer = mock(RegisteredServer.class);
		ConnectionRequestBuilder connectionRequest = mock(ConnectionRequestBuilder.class);
		ConnectionRequestBuilder.Result result = mock(ConnectionRequestBuilder.Result.class);
		VelocityRoutingAdapter adapter = new VelocityRoutingAdapter(proxyServer);
		RoutingIntent intent = intent();

		when(proxyServer.getServer("lobby")).thenReturn(Optional.of(registeredServer));
		when(proxyServer.getPlayer(intent.getConnectionUniqueId())).thenReturn(Optional.of(player));
		when(player.createConnectionRequest(registeredServer)).thenReturn(connectionRequest);
		when(connectionRequest.connect()).thenReturn(CompletableFuture.completedFuture(result));
		when(result.getStatus()).thenReturn(ConnectionRequestBuilder.Status.SERVER_DISCONNECTED);
		when(result.getReasonComponent()).thenReturn(Optional.of(Component.text("You are not whitelisted on this server!")));

		RoutingOutcome outcome = adapter.route(new RoutingRequest(
				intent,
				new RoutingConnectionSnapshot(intent.getConnectionUniqueId(), "whereareiam", "auth"),
				RoutingAttemptTrigger.ASYNC_CONNECT
		)).toCompletableFuture().join();

		assertEquals(RoutingAttemptFailureReason.SERVER_DISCONNECTED, outcome.getFailure().getReason());
		assertEquals("You are not whitelisted on this server!", outcome.getFailure().getDetail());
	}

	@DisplayName("Missing target servers become missing-server routing outcomes")
	@Test
	void missingTargetServersBecomeMissingServerRoutingOutcomes() {
		ProxyServer proxyServer = mock(ProxyServer.class);
		VelocityRoutingAdapter adapter = new VelocityRoutingAdapter(proxyServer);
		RoutingIntent intent = intent();

		RoutingOutcome outcome = adapter.route(new RoutingRequest(
				intent,
				new RoutingConnectionSnapshot(intent.getConnectionUniqueId(), "whereareiam", "auth"),
				RoutingAttemptTrigger.ASYNC_CONNECT
		)).toCompletableFuture().join();

		assertEquals(RoutingAttemptFailureReason.MISSING_SERVER, outcome.getFailure().getReason());
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
}
