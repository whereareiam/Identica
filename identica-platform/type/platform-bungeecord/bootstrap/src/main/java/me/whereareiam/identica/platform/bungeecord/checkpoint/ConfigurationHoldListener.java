package me.whereareiam.identica.platform.bungeecord.checkpoint;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.checkpoint.Checkpoints;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.checkpoint.CheckpointSelection;
import me.whereareiam.identica.platform.bungeecord.BungeeCordIdentica;
import me.whereareiam.identica.platform.bungeecord.adapter.auth.BungeeCordResumeDecisionAdapter;
import me.whereareiam.identica.platform.common.checkpoint.LoadingScreenReplies;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PlayerConfigurationEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds a player in the configuration of their first server, while a checkpoint holds their waiting step on the
 * {@link CheckpointPlacement#LOADING_SCREEN}. BungeeCord connects the player to a server before the client leaves its
 * login, and lets the server's configuration finish only once {@link PlayerConfigurationEvent} has completed; until
 * then the client stays on the loading screen and the server keeps it connected.
 *
 * <p>Once the hold ends, the configuration finishes and the player enters that server. When the journey completes on
 * another server, routing switches the player there once they have entered the first one, as BungeeCord can switch a
 * player only from a server's world.</p>
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ConfigurationHoldListener implements DynamicListener<PlayerConfigurationEvent> {
	private final BungeeCordIdentica plugin;
	private final Checkpoints checkpoints;
	private final BungeeCordResumeDecisionAdapter resumeAdapter;
	private final LoadingScreenReplies replies;
	private final Map<UUID, BungeeCordHeldConnection> held = new ConcurrentHashMap<>();

	@Override
	public void onEvent(PlayerConfigurationEvent event) {
		if (event.getReason() != PlayerConfigurationEvent.Reason.LOGIN) return;

		ProxiedPlayer player = event.getPlayer();
		CheckpointSelection selection = checkpoints.selected(player.getUniqueId()).orElse(null);
		if (selection == null || selection.getPlacement() != CheckpointPlacement.LOADING_SCREEN) return;

		event.registerIntent(plugin);
		BungeeCordHeldConnection connection = new BungeeCordHeldConnection(player, resumeAdapter);
		held.put(player.getUniqueId(), connection);
		replies.held(player.getUniqueId());
		long since = System.currentTimeMillis();
		Logger.debug("Bungee checkpoint holds player=%s server=%s checkpoint=%s", player.getUniqueId(), serverName(player), selection.getCheckpoint().id());

		connection.destination().thenAccept(destination -> {
			held.remove(player.getUniqueId(), connection);
			replies.released(player.getUniqueId());
			Logger.debug("Bungee checkpoint hold ended player=%s destination=%s heldMillis=%s",
					player.getUniqueId(), destination, System.currentTimeMillis() - since);
			event.completeIntent(plugin);
		});
		checkpoints.arrive(connection);
	}

	/**
	 * Ends the hold of a player who left while held.
	 *
	 * @param connectionUniqueId the player's connection
	 */
	public void left(@NotNull UUID connectionUniqueId) {
		resumeAdapter.forget(connectionUniqueId);
		BungeeCordHeldConnection connection = held.remove(connectionUniqueId);
		if (connection != null) connection.release(null);
	}

	private static @Nullable String serverName(@NotNull ProxiedPlayer player) {
		Server server = player.getServer();
		return server != null ? server.getInfo().getName() : null;
	}
}
