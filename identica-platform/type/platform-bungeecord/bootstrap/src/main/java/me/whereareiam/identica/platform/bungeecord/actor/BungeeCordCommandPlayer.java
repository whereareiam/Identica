package me.whereareiam.identica.platform.bungeecord.actor;

import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.platform.bungeecord.util.BaseComponentMapper;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Locale;
import java.util.UUID;

public class BungeeCordCommandPlayer extends Identity {
	private final ProxiedPlayer player;
	private final Audience audience;

	public BungeeCordCommandPlayer(
			@NotNull ProxiedPlayer player,
			@NotNull Audience audience
	) {
		this(player.getUniqueId(), null, player, audience, player.getName(), null);
	}

	public BungeeCordCommandPlayer(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@NotNull ProxiedPlayer player,
			@NotNull Audience audience,
			@NotNull String username,
			@Nullable Origin origin
	) {
		super(connectionUniqueId, accountUniqueId, username, resolveIp(player), origin);
		this.player = player;
		this.audience = audience;
	}

	public ProxiedPlayer getSource() {
		return player;
	}

	@Override
	public void sendMessage(@NotNull Component message) {
		audience.sendMessage(message);
	}

	@Override
	public void sendTitle(@NotNull Title title) {
		audience.showTitle(title);
	}

	@Override
	public void disconnect(@NotNull Component reason) {
		audience.sendMessage(reason);
		player.disconnect(BaseComponentMapper.map(reason));
	}

	@Override
	public boolean hasPermission(@NotNull String permission) {
		if (permission.isBlank()) return true;
		return player.hasPermission(permission);
	}

	@Override
	public @NotNull Locale getLocale() {
		return player.getLocale() == null ? Locale.ENGLISH : player.getLocale();
	}

	@Override
	public @NotNull Audience getAudience() {
		return audience;
	}

	private static String resolveIp(@NotNull ProxiedPlayer player) {
		SocketAddress address = player.getSocketAddress();
		if (!(address instanceof InetSocketAddress inetSocketAddress))
			return null;

		if (inetSocketAddress.getAddress() != null)
			return inetSocketAddress.getAddress().getHostAddress();

		return inetSocketAddress.getHostString();
	}
}
