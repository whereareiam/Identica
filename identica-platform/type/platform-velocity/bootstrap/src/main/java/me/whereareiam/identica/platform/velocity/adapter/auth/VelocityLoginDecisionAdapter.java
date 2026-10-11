package me.whereareiam.identica.platform.velocity.adapter.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.platform.common.adapter.PlatformLoginDecisionAdapter;
import me.whereareiam.identica.platform.common.stage.login.LoginDecisionProcessor;
import me.whereareiam.identica.platform.velocity.actor.VelocityCommandPlayer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class VelocityLoginDecisionAdapter implements PlatformLoginDecisionAdapter<LoginEvent> {
	private final @NotNull LoginDecisionProcessor processor;

	@Override
	public void process(@NotNull LoginEvent event) {
		Player player = event.getPlayer();
		// The username is the one the profile stage saw, so the connection key matches the one stored there.
		LoginDecisionProcessor.Request request = new LoginDecisionProcessor.Request(
				player.getUniqueId(),
				player.getUsername(),
				resolveIp(player),
				resolveOrigin(player),
				player.getCurrentServer()
						.map(server -> server.getServerInfo().getName())
						.orElse(null)
		);

		processor.process(request, target(event))
				.toCompletableFuture()
				.join();
	}

	private @NotNull LoginDecisionProcessor.Target target(@NotNull LoginEvent event) {
		Player player = event.getPlayer();
		return new LoginDecisionProcessor.Target() {
			@Override
			public void refuse(@NotNull Component message) {
				event.setResult(ResultedEvent.ComponentResult.denied(message));
			}

			@Override
			public void deny(@NotNull Component message) {
				event.setResult(ResultedEvent.ComponentResult.denied(message));
			}

			@Override
			public void requireReconnect(@NotNull Component message) {
				event.setResult(ResultedEvent.ComponentResult.denied(message));
			}

			@Override
			public @NotNull Identity identity(
					@Nullable UUID accountUniqueId,
					@NotNull String username,
					ConnectionIdentity.@Nullable Origin origin
			) {
				return new VelocityCommandPlayer(player.getUniqueId(), accountUniqueId, player, username, origin);
			}
		};
	}

	private @Nullable String resolveIp(@NotNull Player player) {
		if (player.getRemoteAddress() == null)
			return null;

		if (player.getRemoteAddress().getAddress() != null)
			return player.getRemoteAddress().getAddress().getHostAddress();

		return player.getRemoteAddress().getHostString();
	}

	private ConnectionIdentity.@Nullable Origin resolveOrigin(@NotNull Player player) {
		InetSocketAddress virtualHost = player.getVirtualHost().orElse(null);
		if (virtualHost == null) return null;

		return new ConnectionIdentity.Origin(virtualHost.getHostString(), virtualHost.getPort());
	}
}
