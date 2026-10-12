package me.whereareiam.identica.integration.statistics;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;

/**
 * Turns Identica's statistics on. A platform installs it and binds its {@link StatisticsPlatform}.
 */
public final class StatisticsConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		Multibinder.newSetBinder(binder(), RuntimeLifecycle.class).addBinding().to(StatisticsLifecycle.class);
	}
}
