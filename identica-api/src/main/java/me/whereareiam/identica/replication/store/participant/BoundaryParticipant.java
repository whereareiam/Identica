package me.whereareiam.identica.replication.store.participant;

import me.whereareiam.identica.type.event.EventOrder;
import org.jetbrains.annotations.NotNull;

/**
 * Base contract for runtime participants that react to one or more Identica
 * lifecycle boundaries.
 *
 * <p>Boundary participants are used for cleanup and invalidation behavior that
 * belongs to a specific connection, completion, termination, or account
 * lifecycle boundary without requiring each participant to register itself as a
 * direct event listener.</p>
 */
public interface BoundaryParticipant {
	/**
	 * Returns the execution order used when multiple participants observe the
	 * same boundary.
	 *
	 * @return participant dispatch order
	 */
	default @NotNull EventOrder order() {
		return EventOrder.NORMAL;
	}
}
