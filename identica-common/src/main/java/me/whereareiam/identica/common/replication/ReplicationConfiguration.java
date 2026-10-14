package me.whereareiam.identica.common.replication;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Names;
import me.whereareiam.identica.common.identity.DefaultReservationCache;
import me.whereareiam.identica.common.replication.event.DefaultReplicatedEventRegistry;
import me.whereareiam.identica.common.replication.event.ReplicatedEventBridge;
import me.whereareiam.identica.common.replication.lease.DefaultLeaseKeeper;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.identity.ReservationCache;
import me.whereareiam.identica.replication.ReplicationAdapter;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.event.ReplicatedEventRegistry;
import me.whereareiam.identica.replication.lease.LeaseKeeper;

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
		Multibinder.newSetBinder(binder(), RuntimeLifecycle.class).addBinding().to(ReplicatedEventBridge.class);
		bind(LeaseKeeper.class).to(DefaultLeaseKeeper.class).asEagerSingleton();
		Multibinder.newSetBinder(binder(), RuntimeLifecycle.class).addBinding().to(DefaultLeaseKeeper.class);
		bind(ReservationCache.class).to(DefaultReservationCache.class).asEagerSingleton();
	}
}
