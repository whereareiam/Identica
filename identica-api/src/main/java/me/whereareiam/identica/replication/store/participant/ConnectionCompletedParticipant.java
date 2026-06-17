package me.whereareiam.identica.replication.store.participant;

import me.whereareiam.identica.event.connection.lifecycle.ConnectionCompletedEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Participant notified when a connection journey fully completes.
 */
public interface ConnectionCompletedParticipant extends BoundaryParticipant {
	/**
	 * Handles a completed connection lifecycle boundary.
	 *
	 * @param event completion event
	 */
	void onConnectionCompleted(@NotNull ConnectionCompletedEvent event);
}
