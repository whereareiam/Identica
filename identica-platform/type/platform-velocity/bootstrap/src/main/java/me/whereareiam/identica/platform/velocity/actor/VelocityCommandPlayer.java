package me.whereareiam.identica.platform.velocity.actor;

import com.velocitypowered.api.proxy.Player;
import me.whereareiam.identica.identity.actor.Identity;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

public class VelocityCommandPlayer extends Identity {
	private final Player player;

	public VelocityCommandPlayer(@NotNull Player player) {
		this(player.getUniqueId(), null, player, player.getUsername(), null);
	}

	public VelocityCommandPlayer(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@NotNull Player player,
			@NotNull String username,
			@Nullable Origin origin
	) {
		super(connectionUniqueId, accountUniqueId, username, resolveIp(player), origin);
		this.player = player;
	}

	public Player getSource() {
		return player;
	}

	@Override
	public void sendMessage(@NotNull Component message) {
		player.sendMessage(message);
	}

	@Override
	public void sendTitle(@NotNull Title title) {
		player.showTitle(title);
	}

	@Override
	public void disconnect(@NotNull Component reason) {
		player.disconnect(reason);
	}

	@Override
	public boolean hasPermission(@NotNull String permission) {
		if (permission.isBlank()) return true;
		return player.hasPermission(permission);
	}

	@Override
	public @NotNull Locale getLocale() {
		return player.getEffectiveLocale() == null
				? Locale.ENGLISH
				: player.getEffectiveLocale();
	}

	@Override
	public @NotNull Audience getAudience() {
		return player;
	}

	private static String resolveIp(@NotNull Player player) {
		if (player.getRemoteAddress() == null) return null;
		if (player.getRemoteAddress().getAddress() != null)
			return player.getRemoteAddress().getAddress().getHostAddress();

		return player.getRemoteAddress().getHostString();
	}
}
