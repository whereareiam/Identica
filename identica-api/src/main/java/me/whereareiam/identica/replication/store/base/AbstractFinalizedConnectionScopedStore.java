package me.whereareiam.identica.replication.store.base;

import me.whereareiam.identica.Registry;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.store.participant.ConnectionCompletedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionTerminatedParticipant;
import me.whereareiam.identica.replication.store.scope.FinalizedConnectionScopedStore;
import org.jetbrains.annotations.NotNull;

/**
 * Shared base for replication-backed stores cleared when a connection is
 * completed or terminated.
 */
public abstract class AbstractFinalizedConnectionScopedStore
		extends AbstractStore
		implements FinalizedConnectionScopedStore {
	protected AbstractFinalizedConnectionScopedStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Registry<ConnectionCompletedParticipant> completedParticipants,
			@NotNull Registry<ConnectionTerminatedParticipant> terminatedParticipants
	) {
		super(replicationSystem);
		completedParticipants.register(this);
		terminatedParticipants.register(this);
	}
}
