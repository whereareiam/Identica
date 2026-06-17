package me.whereareiam.identica.replication.store.base;

import me.whereareiam.identica.Registry;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;
import me.whereareiam.identica.replication.store.scope.AccountScopedStore;
import org.jetbrains.annotations.NotNull;

/**
 * Shared base for stores cleared by account lifecycle boundaries.
 */
public abstract class AbstractAccountScopedStore extends AbstractStore implements AccountScopedStore {
	protected AbstractAccountScopedStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Registry<AccountLifecycleParticipant> participants
	) {
		super(replicationSystem);
		participants.register(this);
	}
}
