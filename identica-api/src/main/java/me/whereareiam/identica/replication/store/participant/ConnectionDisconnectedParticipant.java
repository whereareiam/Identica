package me.whereareiam.identica.replication.store.participant;

import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Participant notified when a live player connection physically disconnects
 * from the proxy.
 */
public interface ConnectionDisconnectedParticipant extends BoundaryParticipant {
	/**
	 * Handles a physical connection disconnect boundary.
	 *
	 * @param event disconnect event
	 */
	void onConnectionDisconnected(@NotNull ConnectionDisconnectedEvent event);
}
