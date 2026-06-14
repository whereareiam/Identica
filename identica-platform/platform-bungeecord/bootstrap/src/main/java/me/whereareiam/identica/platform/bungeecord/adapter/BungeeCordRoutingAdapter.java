package me.whereareiam.identica.platform.bungeecord.adapter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.routing.RoutingConnectionSnapshot;
import me.whereareiam.identica.model.routing.execution.RoutingOutcome;
import me.whereareiam.identica.model.routing.execution.RoutingRequest;
import me.whereareiam.identica.platform.adapter.PlatformRoutingAdapter;
import me.whereareiam.identica.platform.bungeecord.util.BaseComponentMapper;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerConnectRequest;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BungeeCordRoutingAdapter implements PlatformRoutingAdapter {
	private final ProxyServer proxyServer;

	@Override
	public @NotNull Optional<RoutingConnectionSnapshot> snapshot(@NotNull UUID connectionUniqueId) {
		ProxiedPlayer player = proxyServer.getPlayer(connectionUniqueId);
		if (player == null) return Optional.empty();

		String currentServer = player.getServer() != null ? player.getServer().getInfo().getName() : null;
		return Optional.of(new RoutingConnectionSnapshot(connectionUniqueId, player.getName(), currentServer));
	}

	@Override
	public @NotNull CompletionStage<RoutingOutcome> route(@NotNull RoutingRequest request) {
		String targetServer = request.getIntent().getEndpoint().getServer();
		ServerInfo server = proxyServer.getServerInfo(targetServer);
		if (server == null) {
			return CompletableFuture.completedFuture(RoutingOutcome.failed(
					targetServer,
					RoutingAttemptFailureReason.MISSING_SERVER,
					null
			));
		}

		ProxiedPlayer player = proxyServer.getPlayer(request.getConnection().getConnectionUniqueId());
		if (player == null) {
			return CompletableFuture.completedFuture(RoutingOutcome.failed(
					targetServer,
					RoutingAttemptFailureReason.CONNECTION_RESULT_MISSING,
					"player-offline"
			));
		}

		CompletableFuture<RoutingOutcome> future = new CompletableFuture<>();
		player.connect(ServerConnectRequest.builder()
				.target(server)
				.reason(net.md_5.bungee.api.event.ServerConnectEvent.Reason.PLUGIN)
				.callback((result, throwable) -> completeConnect(targetServer, future, result, throwable))
				.build());
		return future;
	}

	@Override
	public void disconnect(@NotNull UUID connectionUniqueId, @NotNull Component message) {
		ProxiedPlayer player = proxyServer.getPlayer(connectionUniqueId);
		if (player != null)
			player.disconnect(BaseComponentMapper.map(message));
	}

	private void completeConnect(
			@NotNull String targetServer,
			@NotNull CompletableFuture<RoutingOutcome> future,
			ServerConnectRequest.Result result,
			Throwable throwable
	) {
		if (throwable != null) {
			future.completeExceptionally(throwable);
			return;
		}
		if (result == null) {
			future.complete(RoutingOutcome.failed(
					targetServer,
					RoutingAttemptFailureReason.CONNECTION_RESULT_MISSING,
					null
			));
			return;
		}

		switch (result) {
			case SUCCESS, ALREADY_CONNECTED ->
					future.complete(RoutingOutcome.accepted(targetServer));
			case ALREADY_CONNECTING ->
					future.complete(RoutingOutcome.failed(
							targetServer,
							RoutingAttemptFailureReason.CONNECTION_IN_PROGRESS,
							null
					));
			case EVENT_CANCEL ->
					future.complete(RoutingOutcome.failed(
							targetServer,
							RoutingAttemptFailureReason.CONNECTION_CANCELLED,
							null
					));
			case FAIL ->
					future.complete(RoutingOutcome.failed(
							targetServer,
							RoutingAttemptFailureReason.SERVER_DISCONNECTED,
							null
					));
		}
	}
}
