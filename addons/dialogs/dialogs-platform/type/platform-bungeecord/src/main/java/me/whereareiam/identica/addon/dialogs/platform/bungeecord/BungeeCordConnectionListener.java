package me.whereareiam.identica.addon.dialogs.platform.bungeecord;

import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Reports when a player's client leaves its server's world for another server and when the player leaves the proxy.
 * For a client with dialogs, BungeeCord switches the current server while the client starts configuring for the new
 * one, before it joins that server's world, which Identica reports with its {@code ServerJoinedEvent}.
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
	public void onServerSwitch(ServerSwitchEvent event) {
		listener.switchingServer(event.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onDisconnect(PlayerDisconnectEvent event) {
		listener.left(event.getPlayer().getUniqueId());
	}
}
