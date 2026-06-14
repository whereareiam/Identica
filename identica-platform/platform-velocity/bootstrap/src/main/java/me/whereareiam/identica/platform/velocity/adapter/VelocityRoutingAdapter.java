package me.whereareiam.identica.platform.velocity.adapter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.routing.RoutingConnectionSnapshot;
import me.whereareiam.identica.model.routing.execution.RoutingOutcome;
import me.whereareiam.identica.model.routing.execution.RoutingRequest;
import me.whereareiam.identica.platform.adapter.PlatformRoutingAdapter;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class VelocityRoutingAdapter implements PlatformRoutingAdapter {
	private final ProxyServer proxyServer;

	@Override
	public @NotNull Optional<RoutingConnectionSnapshot> snapshot(@NotNull UUID connectionUniqueId) {
		Player player = proxyServer.getPlayer(connectionUniqueId).orElse(null);
		if (player == null) return Optional.empty();

		String currentServer = player.getCurrentServer()
				.map(server -> server.getServerInfo().getName())
				.orElse(null);
		return Optional.of(new RoutingConnectionSnapshot(connectionUniqueId, player.getUsername(), currentServer));
	}

	@Override
	public @NotNull CompletionStage<RoutingOutcome> route(@NotNull RoutingRequest request) {
		String targetServer = request.getIntent().getEndpoint().getServer();
		Optional<RegisteredServer> server = proxyServer.getServer(targetServer);
		if (server.isEmpty()) {
			return CompletableFuture.completedFuture(RoutingOutcome.failed(
					targetServer,
					RoutingAttemptFailureReason.MISSING_SERVER,
					null
			));
		}

		Player player = proxyServer.getPlayer(request.getConnection().getConnectionUniqueId()).orElse(null);
		if (player == null) {
			return CompletableFuture.completedFuture(RoutingOutcome.failed(
					targetServer,
					RoutingAttemptFailureReason.CONNECTION_RESULT_MISSING,
					"player-offline"
			));
		}

		return player.createConnectionRequest(server.get()).connect()
				.thenApply(result -> normalize(targetServer, result));
	}

	@Override
	public void disconnect(@NotNull UUID connectionUniqueId, @NotNull Component message) {
        proxyServer.getPlayer(connectionUniqueId).ifPresent(player -> player.disconnect(message));
    }

	private @NotNull RoutingOutcome normalize(
			@NotNull String targetServer,
			ConnectionRequestBuilder.Result result
	) {
		if (result == null) {
			return RoutingOutcome.failed(
					targetServer,
					RoutingAttemptFailureReason.CONNECTION_RESULT_MISSING,
					null
			);
		}

		return switch (result.getStatus()) {
			case SUCCESS, ALREADY_CONNECTED ->
					RoutingOutcome.accepted(targetServer);
			case SERVER_DISCONNECTED ->
					RoutingOutcome.failed(
							targetServer,
							RoutingAttemptFailureReason.SERVER_DISCONNECTED,
							reasonText(result.getReasonComponent().orElse(null))
					);
			case CONNECTION_CANCELLED ->
					RoutingOutcome.failed(
							targetServer,
							RoutingAttemptFailureReason.CONNECTION_CANCELLED,
							null
					);
			case CONNECTION_IN_PROGRESS ->
					RoutingOutcome.failed(
							targetServer,
							RoutingAttemptFailureReason.CONNECTION_IN_PROGRESS,
							null
					);
		};
	}

	private @Nullable String reasonText(@Nullable Component component) {
		if (component == null) return null;
		String reason = PlainTextComponentSerializer.plainText().serialize(component);
		return reason.isBlank() ? null : reason;
	}
}
