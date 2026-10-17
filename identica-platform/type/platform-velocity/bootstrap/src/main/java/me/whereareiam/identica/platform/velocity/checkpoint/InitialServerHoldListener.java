package me.whereareiam.identica.platform.velocity.checkpoint;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.AwaitingEventExecutor;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.checkpoint.Checkpoint;
import me.whereareiam.identica.checkpoint.Checkpoints;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.platform.velocity.adapter.auth.VelocityResumeDecisionAdapter;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds a player on the proxy before Velocity chooses their first server, while a checkpoint with
 * {@link CheckpointPlacement#PROXY} has their waiting step. A 1.20.2+ client is already in its configuration phase
 * then, with no server connected, so no server sees the player until the hold ends; Velocity then chooses the first
 * server as usual, from the journey's completion routing.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class InitialServerHoldListener implements AwaitingEventExecutor<PlayerChooseInitialServerEvent> {
	private final ProxyServer proxy;
	private final Checkpoints checkpoints;
	private final VelocityResumeDecisionAdapter resumeAdapter;
	private final ProxyHoldKeepAlive keepAlive;
	private final Map<UUID, VelocityHeldConnection> held = new ConcurrentHashMap<>();

	@Override
	public EventTask executeAsync(PlayerChooseInitialServerEvent event) {
		Player player = event.getPlayer();
		Checkpoint checkpoint = checkpoints.selected(player.getUniqueId()).orElse(null);
		if (checkpoint == null || checkpoint.placement() != CheckpointPlacement.PROXY) return null;

		VelocityHeldConnection connection = new VelocityHeldConnection(proxy, player, resumeAdapter);
		held.put(player.getUniqueId(), connection);
		ScheduledTask alive = keepAlive.keepAlive(player);
		long since = System.currentTimeMillis();
		Logger.debug("Velocity checkpoint holds player=%s checkpoint=%s", player.getUniqueId(), checkpoint.id());
		checkpoints.arrive(connection);

		return EventTask.resumeWhenComplete(connection.released().toCompletableFuture().thenRun(() -> {
			alive.cancel();
			held.remove(player.getUniqueId(), connection);
			Logger.debug("Velocity checkpoint hold ended player=%s heldMillis=%s", player.getUniqueId(), System.currentTimeMillis() - since);
			// Velocity still connects a player who left during the hold unless no server is chosen.
			if (!player.isActive()) event.setInitialServer(null);
		}));
	}

	/**
	 * Ends the hold of a player who left while held.
	 *
	 * @param event the player's disconnect
	 */
	public void left(DisconnectEvent event) {
		resumeAdapter.forget(event.getPlayer());
		VelocityHeldConnection connection = held.remove(event.getPlayer().getUniqueId());
		if (connection != null) connection.release(null);
	}
}
