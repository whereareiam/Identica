package me.whereareiam.identica.addon.dialogs.platform.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import org.jetbrains.annotations.NotNull;

/**
 * Reports when a player leaves the proxy.
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
	public void onDisconnect(DisconnectEvent event) {
		listener.left(event.getPlayer().getUniqueId());
	}
}
