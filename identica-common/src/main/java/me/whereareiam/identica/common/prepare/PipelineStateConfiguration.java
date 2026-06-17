package me.whereareiam.identica.common.prepare;

import com.google.inject.AbstractModule;
import me.whereareiam.identica.common.completion.DefaultCompletionPendingStore;
import me.whereareiam.identica.pipeline.completion.CompletionPendingStore;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;

public class PipelineStateConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(PrepareStateStore.class).to(DefaultPrepareStateStore.class).asEagerSingleton();
		bind(CompletionPendingStore.class).to(DefaultCompletionPendingStore.class).asEagerSingleton();
	}
}
