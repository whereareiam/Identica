package me.whereareiam.identica.addon.dialogs.platform.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reports when a player connected to a server, which closed every dialog on their client, and when they left. It
 * keeps the players whose connection to their current server has finished: between joining a server and Velocity's
 * post-connect event the player already has a current server, but a dialog shown then is shown once more after it.
 */
public final class VelocityConnectionListener {
	private final DialogPlatform.Listener listener;
	private final Set<UUID> arrived = ConcurrentHashMap.newKeySet();

	/**
	 * Creates the listener.
	 *
	 * @param listener where the listener reports
	 */
	public VelocityConnectionListener(@NotNull DialogPlatform.Listener listener) {
		this.listener = listener;
	}

	/**
	 * Returns whether the player's connection to their current server has finished.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 * @return whether the player has arrived on their current server
	 */
	public boolean hasArrived(@NotNull UUID connectionUniqueId) {
		return arrived.contains(connectionUniqueId);
	}

	@Subscribe
	public void onConnecting(ServerConnectedEvent event) {
		arrived.remove(event.getPlayer().getUniqueId());
	}

	@Subscribe
	public void onConnected(ServerPostConnectEvent event) {
		arrived.add(event.getPlayer().getUniqueId());
		listener.connected(event.getPlayer().getUniqueId());
	}

	@Subscribe
	public void onDisconnect(DisconnectEvent event) {
		arrived.remove(event.getPlayer().getUniqueId());
		listener.left(event.getPlayer().getUniqueId());
	}
}
