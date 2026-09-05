package me.whereareiam.identica.common.listener;

import com.google.inject.AbstractModule;
import me.whereareiam.identica.common.listener.session.SessionClosedDisconnectListener;
import me.whereareiam.identica.common.listener.session.SessionReplacedListener;
import me.whereareiam.identica.common.provider.ProviderEntrypointSelectionLifecycle;
import me.whereareiam.identica.listener.DynamicListenerRegistry;

public class ListenerConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(SessionClosedDisconnectListener.class).asEagerSingleton();
		bind(SessionReplacedListener.class).asEagerSingleton();
		bind(ProviderEntrypointSelectionLifecycle.class).asEagerSingleton();
		bind(DynamicListenerRegistry.class).to(DefaultDynamicListenerRegistry.class).asEagerSingleton();
	}
}
