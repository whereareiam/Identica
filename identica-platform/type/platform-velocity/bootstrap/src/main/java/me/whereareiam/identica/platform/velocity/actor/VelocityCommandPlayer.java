package me.whereareiam.identica.platform.velocity.actor;

import com.velocitypowered.api.proxy.Player;
import me.whereareiam.identica.checkpoint.Checkpoints;
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
	private final Checkpoints checkpoints;

	public VelocityCommandPlayer(@NotNull Player player, @NotNull Checkpoints checkpoints) {
		this(player.getUniqueId(), null, player, player.getUsername(), null, checkpoints);
	}

	public VelocityCommandPlayer(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@NotNull Player player,
			@NotNull String username,
			@Nullable Origin origin,
			@NotNull Checkpoints checkpoints
	) {
		super(connectionUniqueId, accountUniqueId, username, resolveIp(player), origin);
		this.player = player;
		this.checkpoints = checkpoints;
	}

	public Player getSource() {
		return player;
	}

	/**
	 * Sends a message, except while a checkpoint holds the player before their first server: the client cannot show
	 * chat then, and Velocity would deliver it once the player joined, out of date. The checkpoint shows the player
	 * Identica's prompts and answers itself.
	 */
	@Override
	public void sendMessage(@NotNull Component message) {
		if (checkpoints.holds(getConnectionUniqueId())) return;

		player.sendMessage(message);
	}

	@Override
	public void sendTitle(@NotNull Title title) {
		if (checkpoints.holds(getConnectionUniqueId())) return;

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
