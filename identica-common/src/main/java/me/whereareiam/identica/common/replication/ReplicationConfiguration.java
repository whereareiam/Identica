package me.whereareiam.identica.common.replication;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Names;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.common.identity.DefaultReservationCache;
import me.whereareiam.identica.common.replication.event.DefaultReplicatedEventRegistry;
import me.whereareiam.identica.common.replication.event.ReplicatedEventBridge;
import me.whereareiam.identica.common.replication.store.BoundaryParticipantCoordinator;
import me.whereareiam.identica.common.replication.store.DefaultScopedParticipantRegistry;
import me.whereareiam.identica.identity.ReservationCache;
import me.whereareiam.identica.replication.ReplicationAdapter;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.event.ReplicatedEventRegistry;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionCompletedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionDisconnectedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionTerminatedParticipant;

public class ReplicationConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		OptionalBinder.newOptionalBinder(binder(), Key.get(ReplicationAdapter.class, Names.named("replicationAdapter")))
				.setDefault()
				.to(NoopReplicationAdapter.class)
				.asEagerSingleton();

		bind(ReplicationAdapter.class).to(DefaultReplicationAdapter.class).asEagerSingleton();
		bind(ReplicationSystem.class).to(DefaultReplicationSystem.class).asEagerSingleton();
		bind(ReplicatedEventRegistry.class).to(DefaultReplicatedEventRegistry.class).asEagerSingleton();
		bind(ReplicatedEventBridge.class).asEagerSingleton();
		bind(DefaultScopedParticipantRegistry.class).asEagerSingleton();
		bind(BoundaryParticipantCoordinator.class).asEagerSingleton();
		bind(ReservationCache.class).to(DefaultReservationCache.class).asEagerSingleton();
	}

	@Provides
	@Singleton
	Registry<ConnectionDisconnectedParticipant> provideConnectionDisconnectedParticipants(DefaultScopedParticipantRegistry registry) {
		return registry.disconnected();
	}

	@Provides
	@Singleton
	Registry<ConnectionCompletedParticipant> provideConnectionCompletedParticipants(DefaultScopedParticipantRegistry registry) {
		return registry.completed();
	}

	@Provides
	@Singleton
	Registry<ConnectionTerminatedParticipant> provideConnectionTerminatedParticipants(DefaultScopedParticipantRegistry registry) {
		return registry.terminated();
	}

	@Provides
	@Singleton
	Registry<AccountLifecycleParticipant> provideAccountLifecycleParticipants(DefaultScopedParticipantRegistry registry) {
		return registry.accounts();
	}
}
