package me.whereareiam.identica.platform.bungeecord.checkpoint;

import me.whereareiam.identica.checkpoint.HeldConnection;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.platform.bungeecord.adapter.auth.BungeeCordResumeDecisionAdapter;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A player BungeeCord holds in the configuration of their first server. The hold ends when Identica releases it or
 * the player leaves; the release names where routing sends the player next.
 */
final class BungeeCordHeldConnection implements HeldConnection {
	private final ProxiedPlayer player;
	private final BungeeCordResumeDecisionAdapter resumeAdapter;
	private final CompletableFuture<String> released = new CompletableFuture<>();

	BungeeCordHeldConnection(@NotNull ProxiedPlayer player, @NotNull BungeeCordResumeDecisionAdapter resumeAdapter) {
		this.player = player;
		this.resumeAdapter = resumeAdapter;
	}

	@Override
	public @NotNull UUID getConnectionUniqueId() {
		return player.getUniqueId();
	}

	/**
	 * Returns the journey's decision from when BungeeCord connected the player to the server they configure with:
	 * the journey resumed then, before the hold began, so it continues from there.
	 */
	@Override
	public @NotNull CompletionStage<ConnectionDecision> resume() {
		return CompletableFuture.completedFuture(resumeAdapter.resumedOnConnect(player));
	}

	@Override
	public void release(@Nullable String destination) {
		if (released.complete(destination))
			Logger.debug("Bungee checkpoint released player=%s destination=%s", player.getUniqueId(), destination);
	}

	@Override
	public void handOver(@Nullable String destination) {
		release(destination);
	}

	@Override
	public @NotNull CompletionStage<Void> released() {
		return released.thenApply(destination -> null);
	}

	/**
	 * Completes with the server routing sends the player to once the hold ended, or {@code null} when it sends the
	 * player nowhere or the player left.
	 *
	 * @return the destination
	 */
	@NotNull CompletionStage<String> destination() {
		return released;
	}
}
