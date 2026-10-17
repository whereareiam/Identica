package me.whereareiam.identica.common.checkpoint;

import com.google.inject.AbstractModule;
import me.whereareiam.identica.checkpoint.Checkpoints;

public class CheckpointConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(DefaultCheckpoints.class).asEagerSingleton();
		bind(Checkpoints.class).to(DefaultCheckpoints.class);
	}
}
