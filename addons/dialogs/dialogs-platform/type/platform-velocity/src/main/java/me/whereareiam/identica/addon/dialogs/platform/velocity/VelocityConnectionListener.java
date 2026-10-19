package me.whereareiam.identica.addon.dialogs.platform.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import org.jetbrains.annotations.NotNull;

/**
 * Reports when a player's client leaves its server's world for another server and when the player leaves the proxy.
 * That the client is in a server's world again comes from Identica's {@code ServerJoinedEvent}.
 */
public final class VelocityConnectionListener {
	private final DialogPlatform.Listener listener;

	/**
	 * Creates the listener.
	 *
	 * @param listener where the listener reports
	 */
	public VelocityConnectionListener(@NotNull DialogPlatform.Listener listener) {
		this.listener = listener;
	}

	@Subscribe
	public void onConnecting(ServerConnectedEvent event) {
		listener.switchingServer(event.getPlayer().getUniqueId());
	}

	@Subscribe
	public void onDisconnect(DisconnectEvent event) {
		listener.left(event.getPlayer().getUniqueId());
	}
}
