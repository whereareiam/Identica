package me.whereareiam.identica.addon.dialogs.platform.bungeecord.listener;

import me.whereareiam.identica.addon.dialogs.platform.PlatformListener;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Reports when a player connected to a server, which closed every dialog on their client, and when they left. It
 * keeps the players whose switch to their current server has finished, so a dialog is not shown during the switch
 * and once more after it.
 */
public final class BungeeCordConnectionListener implements Listener {
	private final PlatformListener listener;
	private final Set<UUID> arrived;

	/**
	 * Creates the listener.
	 *
	 * @param listener where the listener reports
	 * @param arrived the players whose switch to their current server has finished, which the listener keeps
	 */
	public BungeeCordConnectionListener(@NotNull PlatformListener listener, @NotNull Set<UUID> arrived) {
		this.listener = listener;
		this.arrived = arrived;
	}

	@EventHandler
	public void onConnecting(ServerConnectedEvent event) {
		arrived.remove(event.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onServerSwitch(ServerSwitchEvent event) {
		arrived.add(event.getPlayer().getUniqueId());
		listener.connected(event.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onDisconnect(PlayerDisconnectEvent event) {
		arrived.remove(event.getPlayer().getUniqueId());
		listener.left(event.getPlayer().getUniqueId());
	}
}
