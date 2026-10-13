package me.whereareiam.identica.common.conflict;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import me.whereareiam.identica.conflict.ConflictService;

public class ConflictConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		Multibinder.newSetBinder(binder(), ConflictDocument.class);
		bind(ConflictService.class).to(DefaultConflictService.class).asEagerSingleton();
	}
}
