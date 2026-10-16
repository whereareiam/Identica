package me.whereareiam.identica.addon.dialogs.platform.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import me.whereareiam.identica.addon.dialogs.platform.PlatformListener;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Reports when a player connected to a server, which closed every dialog on their client, and when they left. It
 * keeps the players whose connection to their current server has finished: between joining a server and Velocity's
 * post-connect event the player already has a current server, but a dialog shown then is shown once more after it.
 */
public final class VelocityConnectionListener {
	private final PlatformListener listener;
	private final Set<UUID> arrived;

	/**
	 * Creates the listener.
	 *
	 * @param listener where the listener reports
	 * @param arrived the players whose connection to their current server has finished, which the listener keeps
	 */
	public VelocityConnectionListener(@NotNull PlatformListener listener, @NotNull Set<UUID> arrived) {
		this.listener = listener;
		this.arrived = arrived;
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
