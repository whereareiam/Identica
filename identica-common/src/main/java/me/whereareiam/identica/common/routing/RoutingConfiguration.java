package me.whereareiam.identica.common.routing;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.routing.RoutingAttemptService;
import me.whereareiam.identica.routing.RoutingCoordinator;
import me.whereareiam.identica.routing.RoutingIntentStore;

public class RoutingConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(RoutingIntentStore.class).to(DefaultRoutingIntentStore.class).asEagerSingleton();
		bind(RoutingPlanner.class).asEagerSingleton();
		bind(DefaultRoutingCoordinator.class).asEagerSingleton();
		bind(RoutingCoordinator.class).to(DefaultRoutingCoordinator.class);
		bind(RoutingAttemptService.class).to(DefaultRoutingCoordinator.class);
		bind(RoutingRetryCoordinator.class).asEagerSingleton();
		Multibinder.newSetBinder(binder(), RuntimeLifecycle.class).addBinding().to(RoutingRetryCoordinator.class);
		bind(RoutingTargetMissingListener.class).asEagerSingleton();
	}
}
