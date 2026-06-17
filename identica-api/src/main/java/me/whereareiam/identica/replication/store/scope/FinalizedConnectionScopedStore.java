package me.whereareiam.identica.replication.store.scope;

import me.whereareiam.identica.replication.store.StateStore;
import me.whereareiam.identica.replication.store.participant.ConnectionCompletedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionTerminatedParticipant;

/**
 * Store whose state is invalidated when a connection journey reaches a final
 * completed or terminated state.
 */
public interface FinalizedConnectionScopedStore extends StateStore, ConnectionCompletedParticipant, ConnectionTerminatedParticipant {
}
