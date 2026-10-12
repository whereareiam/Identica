package me.whereareiam.identica.adapter.replication;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Names;
import me.whereareiam.identica.adapter.replication.provider.JedisPoolProvider;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.replication.ReplicationAdapter;

public class ReplicationConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		OptionalBinder.newOptionalBinder(binder(), Key.get(ReplicationAdapter.class, Names.named("replicationAdapter")))
				.setBinding()
				.to(RedisReplicationAdapter.class);
		Multibinder.newSetBinder(binder(), RuntimeLifecycle.class).addBinding().to(JedisPoolProvider.class);
	}
}
