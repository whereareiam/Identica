package me.whereareiam.identica.feature.restriction.join;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import me.whereareiam.identica.feature.ProviderFeatureContribution;

public class JoinRestrictionProviderModule extends AbstractModule {
	@Override
	protected void configure() {
		Multibinder.newSetBinder(binder(), ProviderFeatureContribution.class)
				.addBinding()
				.to(LinkedJoinSignalContribution.class);
	}
}
