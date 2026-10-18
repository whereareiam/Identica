package me.whereareiam.identica.platform.velocity.checkpoint;

import com.velocitypowered.api.proxy.Player;
import me.whereareiam.identica.checkpoint.HeldConnection;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.platform.velocity.adapter.auth.VelocityResumeDecisionAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A player Velocity holds on the proxy before choosing their first server. The hold ends when Identica releases it or
 * the player leaves; Velocity then chooses the first server from the journey's completion routing.
 */
final class VelocityHeldConnection implements HeldConnection {
	private final Player player;
	private final VelocityResumeDecisionAdapter resumeAdapter;
	private final CompletableFuture<Void> released = new CompletableFuture<>();

	VelocityHeldConnection(@NotNull Player player, @NotNull VelocityResumeDecisionAdapter resumeAdapter) {
		this.player = player;
		this.resumeAdapter = resumeAdapter;
	}

	@Override
	public @NotNull UUID getConnectionUniqueId() {
		return player.getUniqueId();
	}

	@Override
	public @NotNull CompletionStage<ConnectionDecision> resume() {
		return CompletableFuture.supplyAsync(() -> resumeAdapter.resumeBeforeJoin(player));
	}

	@Override
	public void release(@Nullable String destination) {
		if (released.complete(null))
			Logger.debug("Velocity checkpoint released player=%s destination=%s", player.getUniqueId(), destination);
	}

	@Override
	public void handOver(@Nullable String destination) {
		resumeAdapter.forget(player);
		release(destination);
	}

	@Override
	public @NotNull CompletionStage<Void> released() {
		return released;
	}
}
