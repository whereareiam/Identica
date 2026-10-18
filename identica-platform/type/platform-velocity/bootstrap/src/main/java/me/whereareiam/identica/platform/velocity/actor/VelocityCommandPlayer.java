package me.whereareiam.identica.platform.velocity.actor;

import com.velocitypowered.api.proxy.Player;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.platform.common.checkpoint.LoadingScreenReplies;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class VelocityCommandPlayer extends Identity {
	private final Player player;
	private final LoadingScreenReplies replies;

	public VelocityCommandPlayer(@NotNull Player player, @NotNull LoadingScreenReplies replies) {
		this(player.getUniqueId(), null, player, player.getUsername(), null, replies);
	}

	public VelocityCommandPlayer(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@NotNull Player player,
			@NotNull String username,
			@Nullable Origin origin,
			@NotNull LoadingScreenReplies replies
	) {
		super(connectionUniqueId, accountUniqueId, username, resolveIp(player), origin);
		this.player = player;
		this.replies = replies;
	}

	public Player getSource() {
		return player;
	}

	/**
	 * Sends a message text, or while Velocity holds the player on the loading screen, defers it until the player's
	 * client joined a server's world.
	 */
	@Override
	public void send(@NotNull String message, @NotNull Map<String, String> placeholders) {
		if (message.isBlank()) return;
		if (replies.defer(getConnectionUniqueId(), Serializer.render(message, placeholders))) return;

		super.send(message, placeholders);
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
