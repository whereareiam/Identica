package me.whereareiam.identica.replication.store;

import me.whereareiam.identica.replication.store.participant.BoundaryParticipant;

/**
 * Base contract for Identica components that own mutable state backed by local
 * or replicated storage.
 *
 * <p>State stores may optionally participate in lifecycle boundaries such as
 * disconnect, completion, termination, or account lifecycle invalidation.</p>
 */
public interface StateStore extends BoundaryParticipant {
}
