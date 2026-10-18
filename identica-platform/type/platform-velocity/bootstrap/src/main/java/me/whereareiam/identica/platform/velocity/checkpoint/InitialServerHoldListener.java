package me.whereareiam.identica.platform.velocity.checkpoint;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.AwaitingEventExecutor;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.checkpoint.Checkpoints;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.checkpoint.CheckpointSelection;
import me.whereareiam.identica.platform.common.checkpoint.LoadingScreenReplies;
import me.whereareiam.identica.platform.velocity.adapter.auth.VelocityResumeDecisionAdapter;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds a player on the proxy before Velocity chooses their first server, while a checkpoint holds their waiting step
 * on the {@link CheckpointPlacement#LOADING_SCREEN}. The client is already in its configuration phase then, with no
 * server connected, so no server sees the player until the hold ends; Velocity then chooses the first server as usual,
 * from the journey's completion routing. Cookie requests keep the client connected meanwhile.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class InitialServerHoldListener implements AwaitingEventExecutor<PlayerChooseInitialServerEvent> {
	private final Checkpoints checkpoints;
	private final VelocityResumeDecisionAdapter resumeAdapter;
	private final VelocityCheckpointAdapter adapter;
	private final ProxyHoldKeepAlive keepAlive;
	private final LoadingScreenReplies replies;
	private final Map<UUID, VelocityHeldConnection> held = new ConcurrentHashMap<>();

	@Override
	public EventTask executeAsync(PlayerChooseInitialServerEvent event) {
		Player player = event.getPlayer();
		CheckpointSelection selection = checkpoints.selected(player.getUniqueId()).orElse(null);
		if (selection == null || selection.getPlacement() != CheckpointPlacement.LOADING_SCREEN) return null;

		VelocityHeldConnection connection = new VelocityHeldConnection(player, resumeAdapter);
		held.put(player.getUniqueId(), connection);
		replies.held(player.getUniqueId());
		ScheduledTask alive = keepAlive.keepAlive(player);
		long since = System.currentTimeMillis();
		Logger.debug("Velocity checkpoint holds player=%s checkpoint=%s", player.getUniqueId(), selection.getCheckpoint().id());
		checkpoints.arrive(connection);

		return EventTask.resumeWhenComplete(connection.released().toCompletableFuture().thenRun(() -> {
			alive.cancel();
			held.remove(player.getUniqueId(), connection);
			replies.released(player.getUniqueId());
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
		UUID connectionUniqueId = event.getPlayer().getUniqueId();
		resumeAdapter.forget(event.getPlayer());
		adapter.left(connectionUniqueId);
		VelocityHeldConnection connection = held.remove(connectionUniqueId);
		if (connection != null) connection.release(null);
	}
}
