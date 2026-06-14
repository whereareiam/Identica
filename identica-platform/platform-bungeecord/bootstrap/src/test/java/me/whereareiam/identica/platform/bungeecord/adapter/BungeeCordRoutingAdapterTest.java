package me.whereareiam.identica.platform.bungeecord.adapter;

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
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerConnectRequest;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Bungee Routing Adapter")
class BungeeCordRoutingAdapterTest {
	@DisplayName("Failing connects become server-disconnected routing outcomes")
	@Test
	void failingConnectsBecomeServerDisconnectedRoutingOutcomes() {
		ProxyServer proxyServer = mock(ProxyServer.class);
		ProxiedPlayer player = mock(ProxiedPlayer.class);
		ServerInfo serverInfo = mock(ServerInfo.class);
		BungeeCordRoutingAdapter adapter = new BungeeCordRoutingAdapter(proxyServer);
		RoutingIntent intent = intent();
		RoutingRequest request = new RoutingRequest(
				intent,
				new RoutingConnectionSnapshot(intent.getConnectionUniqueId(), "whereareiam", "auth"),
				RoutingAttemptTrigger.ASYNC_CONNECT
		);

		when(proxyServer.getServerInfo("lobby")).thenReturn(serverInfo);
		when(proxyServer.getPlayer(intent.getConnectionUniqueId())).thenReturn(player);
		when(serverInfo.getName()).thenReturn("lobby");
		when(player.getUniqueId()).thenReturn(intent.getConnectionUniqueId());

		CompletableFuture<RoutingOutcome> future = new CompletableFuture<>();
		invokeCompleteConnect(adapter, future);
		RoutingOutcome outcome = future.join();

		assertEquals(RoutingAttemptFailureReason.SERVER_DISCONNECTED, outcome.getFailure().getReason());
        assertNull(outcome.getFailure().getDetail());
	}

	@DisplayName("Missing target servers become missing-server routing outcomes")
	@Test
	void missingTargetServersBecomeMissingServerRoutingOutcomes() {
		ProxyServer proxyServer = mock(ProxyServer.class);
		BungeeCordRoutingAdapter adapter = new BungeeCordRoutingAdapter(proxyServer);
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

	private void invokeCompleteConnect(
			BungeeCordRoutingAdapter adapter,
			CompletableFuture<RoutingOutcome> future
	) {
		try {
			Method method = BungeeCordRoutingAdapter.class.getDeclaredMethod(
					"completeConnect",
					String.class,
					CompletableFuture.class,
					ServerConnectRequest.Result.class,
					Throwable.class
			);
			method.setAccessible(true);
			method.invoke(adapter, "lobby", future, ServerConnectRequest.Result.FAIL, null);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}
}
