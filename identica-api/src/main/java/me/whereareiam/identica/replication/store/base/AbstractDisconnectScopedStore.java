package me.whereareiam.identica.replication.store.base;

import me.whereareiam.identica.Registry;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.store.participant.ConnectionDisconnectedParticipant;
import me.whereareiam.identica.replication.store.scope.DisconnectScopedStore;
import org.jetbrains.annotations.NotNull;

/**
 * Shared base for stores that clear state when a connection disconnects.
 */
public abstract class AbstractDisconnectScopedStore extends AbstractStore implements DisconnectScopedStore {
	protected AbstractDisconnectScopedStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Registry<ConnectionDisconnectedParticipant> participants
	) {
		super(replicationSystem);
		participants.register(this);
	}
}
