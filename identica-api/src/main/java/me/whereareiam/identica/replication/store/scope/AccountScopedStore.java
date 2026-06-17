package me.whereareiam.identica.replication.store.scope;

import me.whereareiam.identica.replication.store.StateStore;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;

/**
 * Store whose state is invalidated by account clear or delete lifecycle
 * operations.
 */
public interface AccountScopedStore extends StateStore, AccountLifecycleParticipant {
}
