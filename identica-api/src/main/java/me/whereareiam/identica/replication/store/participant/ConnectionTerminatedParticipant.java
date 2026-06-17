package me.whereareiam.identica.replication.store.participant;

import me.whereareiam.identica.event.connection.lifecycle.ConnectionTerminatedEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Participant notified when Identica terminates a connection journey before it
 * reaches completion.
 */
public interface ConnectionTerminatedParticipant extends BoundaryParticipant {
	/**
	 * Handles a terminated connection lifecycle boundary.
	 *
	 * @param event termination event
	 */
	void onConnectionTerminated(@NotNull ConnectionTerminatedEvent event);
}
