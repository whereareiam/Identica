package me.whereareiam.identica.replication.store.scope;

import me.whereareiam.identica.replication.store.StateStore;
import me.whereareiam.identica.replication.store.participant.ConnectionDisconnectedParticipant;

/**
 * Store whose state is invalidated when a physical connection disconnects from
 * the proxy.
 */
public interface DisconnectScopedStore extends StateStore, ConnectionDisconnectedParticipant {
}
