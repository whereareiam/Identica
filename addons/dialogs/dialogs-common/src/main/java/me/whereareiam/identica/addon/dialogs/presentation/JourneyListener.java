package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.ServerJoinedEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Tells the presenter when a player's client is in a server's world, from {@link ServerJoinedEvent}, so it shows a
 * dialog a plugin showed that player directly meanwhile. Identica shows the dialogs of its own messages itself.
 */
public final class JourneyListener implements EventListener {
	private final DialogPresenter presenter;

	/**
	 * Creates the listener.
	 *
	 * @param presenter the presenter to tell
	 */
	public JourneyListener(@NotNull DialogPresenter presenter) {
		this.presenter = presenter;
	}

	@IdenticEvent
	public void onServerJoined(ServerJoinedEvent event) {
		UUID connectionUniqueId = event.getIdentity().getConnectionUniqueId();
		if (connectionUniqueId == null) return;

		presenter.joined(connectionUniqueId);
	}

}
