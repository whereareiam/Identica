package me.whereareiam.identica.adapter.command;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.command.CommandService;

public class CommandConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(CommandService.class).to(DefaultCommandService.class).asEagerSingleton();
		Multibinder.newSetBinder(binder(), RuntimeLifecycle.class).addBinding().to(DefaultCommandService.class);
	}
}
