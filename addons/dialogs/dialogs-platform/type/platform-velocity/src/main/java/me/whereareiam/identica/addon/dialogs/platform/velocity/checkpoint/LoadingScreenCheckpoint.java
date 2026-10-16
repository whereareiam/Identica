package me.whereareiam.identica.addon.dialogs.platform.velocity.checkpoint;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.checkpoint.Checkpoint;
import me.whereareiam.identica.checkpoint.CheckpointStop;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.checkpoint.CheckpointRequest;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asks a player on the loading screen: Identica holds the player on the proxy before their first server, the addon
 * shows its dialogs there, and the player joins once the journey has finished. It takes a player whose client shows
 * dialogs, Minecraft 1.21.6 and later, when the addon's {@code loadingScreen} rule asks for the connection.
 */
public final class LoadingScreenCheckpoint implements Checkpoint {
	/**
	 * ID of the checkpoint.
	 */
	public static final String ID = "loading-screen";

	private final Map<UUID, ProtocolVersion> versions = new ConcurrentHashMap<>();
	private final Map<UUID, CheckpointStop> stops = new ConcurrentHashMap<>();
	private volatile @Nullable DialogPlatform.Listener addon;

	/**
	 * Starts deciding with the addon's rules.
	 *
	 * @param addon the addon's side of the platform
	 */
	public void start(@NotNull DialogPlatform.Listener addon) {
		this.addon = addon;
	}

	@Override
	public @NotNull String id() {
		return ID;
	}

	@Override
	public @NotNull CheckpointPlacement placement() {
		return CheckpointPlacement.PROXY;
	}

	@Override
	public boolean accepts(@NotNull CheckpointRequest request) {
		DialogPlatform.Listener current = addon;
		ProtocolVersion version = versions.get(request.getConnectionUniqueId());
		return current != null
				&& version != null
				&& version.noLessThan(ProtocolVersion.MINECRAFT_1_21_6)
				&& current.beforeJoin(request);
	}

	@Override
	public void arrived(@NotNull CheckpointStop stop) {
		UUID connectionUniqueId = stop.getConnectionUniqueId();
		stops.put(connectionUniqueId, stop);
		stop.released().whenComplete((ignored, failure) -> stops.remove(connectionUniqueId, stop));
		stop.resume().whenComplete((decision, failure) -> {
			if (failure != null) Logger.severe(failure, "Could not continue the journey of a player on the loading screen");
		});
	}

	/**
	 * Returns the stop of a player held on the loading screen.
	 *
	 * @param connectionUniqueId the player's connection
	 * @return the stop, or empty when the player is not held here
	 */
	public @NotNull Optional<CheckpointStop> held(@NotNull UUID connectionUniqueId) {
		return Optional.ofNullable(stops.get(connectionUniqueId));
	}

	/**
	 * Remembers the client version of a player who logs in, before Identica decides where their first step waits.
	 */
	@Subscribe(order = PostOrder.FIRST)
	public void onLogin(LoginEvent event) {
		versions.put(event.getPlayer().getUniqueId(), event.getPlayer().getProtocolVersion());
	}

	@Subscribe
	public void onDisconnect(DisconnectEvent event) {
		versions.remove(event.getPlayer().getUniqueId());
		stops.remove(event.getPlayer().getUniqueId());
	}
}
