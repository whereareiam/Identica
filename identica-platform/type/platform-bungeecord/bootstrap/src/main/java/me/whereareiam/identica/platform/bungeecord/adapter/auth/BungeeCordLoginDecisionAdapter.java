package me.whereareiam.identica.platform.bungeecord.adapter.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.platform.bungeecord.actor.BungeeCordCommandPlayer;
import me.whereareiam.identica.platform.bungeecord.util.BaseComponentMapper;
import me.whereareiam.identica.platform.common.adapter.PlatformLoginDecisionAdapter;
import me.whereareiam.identica.platform.common.stage.login.LoginDecisionProcessor;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BungeeCordLoginDecisionAdapter implements PlatformLoginDecisionAdapter<PostLoginEvent> {
	private final @NotNull LoginDecisionProcessor processor;
	private final @NotNull BungeeAudiences audiences;

	@Override
	public void process(@NotNull PostLoginEvent event) {
		ProxiedPlayer player = event.getPlayer();
		LoginDecisionProcessor.Request request = new LoginDecisionProcessor.Request(
				player.getUniqueId(),
				player.getName(),
				resolveIp(player),
				resolveOrigin(player),
				event.getTarget() != null ? event.getTarget().getName() : null
		);

		processor.process(request, target(player))
				.toCompletableFuture()
				.join();
	}

	private @NotNull LoginDecisionProcessor.Target target(@NotNull ProxiedPlayer player) {
		return new LoginDecisionProcessor.Target() {
			@Override
			public void refuse(@NotNull Component message) {
				player.disconnect(BaseComponentMapper.map(message));
			}

			@Override
			public void deny(@NotNull Component message) {
				audiences.player(player).sendMessage(message);
				player.disconnect(BaseComponentMapper.map(message));
			}

			@Override
			public void requireReconnect(@NotNull Component message) {
				audiences.player(player).sendMessage(message);
				player.disconnect(BaseComponentMapper.map(message));
			}

			@Override
			public @NotNull Identity identity(
					@Nullable UUID accountUniqueId,
					@NotNull String username,
					ConnectionIdentity.@Nullable Origin origin
			) {
				return new BungeeCordCommandPlayer(
						player.getUniqueId(),
						accountUniqueId,
						player,
						audiences.player(player),
						username,
						origin
				);
			}
		};
	}

	private @Nullable String resolveIp(@NotNull ProxiedPlayer player) {
		SocketAddress address = player.getSocketAddress();
		if (!(address instanceof InetSocketAddress inetSocketAddress))
			return null;

		if (inetSocketAddress.getAddress() != null)
			return inetSocketAddress.getAddress().getHostAddress();

		return inetSocketAddress.getHostString();
	}

	private ConnectionIdentity.@Nullable Origin resolveOrigin(@NotNull ProxiedPlayer player) {
		if (player.getPendingConnection() == null || player.getPendingConnection().getVirtualHost() == null)
			return null;

		return new ConnectionIdentity.Origin(
				player.getPendingConnection().getVirtualHost().getHostString(),
				player.getPendingConnection().getVirtualHost().getPort()
		);
	}
}
