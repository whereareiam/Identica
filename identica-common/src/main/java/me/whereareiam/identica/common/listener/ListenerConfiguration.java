package me.whereareiam.identica.common.listener;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.common.listener.session.SessionClosedDisconnectListener;
import me.whereareiam.identica.listener.DynamicListenerRegistry;

public class ListenerConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(SessionClosedDisconnectListener.class).asEagerSingleton();
		bind(DynamicListenerRegistry.class).to(DefaultDynamicListenerRegistry.class).asEagerSingleton();
		Multibinder.newSetBinder(binder(), RuntimeLifecycle.class).addBinding().to(ConnectionLifecycle.class);
	}
}
