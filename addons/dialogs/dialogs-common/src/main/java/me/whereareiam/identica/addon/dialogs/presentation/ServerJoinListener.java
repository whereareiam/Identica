package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.ServerJoinedEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Tells the presenter when a player's client is in a server's world, from Identica's {@link ServerJoinedEvent}, which
 * every platform reports at the moment its client can show a dialog.
 */
public final class ServerJoinListener implements EventListener {
	private final DialogPresenter presenter;

	/**
	 * Creates the listener.
	 *
	 * @param presenter the presenter to tell
	 */
	public ServerJoinListener(@NotNull DialogPresenter presenter) {
		this.presenter = presenter;
	}

	/**
	 * Shows the dialog the player should see now that their client is in the server's world.
	 *
	 * @param event the player's client joined a server's world
	 */
	@IdenticEvent
	public void onServerJoined(ServerJoinedEvent event) {
		UUID connectionUniqueId = event.getIdentity().getConnectionUniqueId();
		if (connectionUniqueId == null) return;

		presenter.joined(connectionUniqueId);
	}
}
