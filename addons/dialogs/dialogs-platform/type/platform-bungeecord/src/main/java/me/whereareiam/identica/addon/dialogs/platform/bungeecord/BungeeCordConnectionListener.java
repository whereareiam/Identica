package me.whereareiam.identica.addon.dialogs.platform.bungeecord;

import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Reports when a player leaves the proxy.
 */
public final class BungeeCordConnectionListener implements Listener {
	private final DialogPlatform.Listener listener;

	/**
	 * Creates the listener.
	 *
	 * @param listener where the listener reports
	 */
	public BungeeCordConnectionListener(@NotNull DialogPlatform.Listener listener) {
		this.listener = listener;
	}

	@EventHandler
	public void onDisconnect(PlayerDisconnectEvent event) {
		listener.left(event.getPlayer().getUniqueId());
	}
}
