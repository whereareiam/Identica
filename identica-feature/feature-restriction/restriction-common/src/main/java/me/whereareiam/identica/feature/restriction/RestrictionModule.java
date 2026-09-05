package me.whereareiam.identica.feature.restriction;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.feature.restriction.config.RestrictionSettings;
import me.whereareiam.identica.feature.restriction.config.provider.RestrictionSettingsProvider;
import me.whereareiam.identica.feature.restriction.registry.DefaultRestrictionSignalRegistry;
import me.whereareiam.identica.feature.restriction.registry.DefaultRestrictionTypeRegistry;
import me.whereareiam.identica.feature.restriction.registry.RestrictionSignalRegistry;
import me.whereareiam.identica.feature.restriction.registry.type.RestrictionTypeRegistry;
import me.whereareiam.identica.feature.restriction.registry.type.RestrictionTypeResolverRegistry;

import java.nio.file.Path;

@RequiredArgsConstructor
public class RestrictionModule extends AbstractModule {
	private final Path restrictionFeaturePath;

	@Override
	protected void configure() {
		bind(RestrictionSettingsProvider.class).asEagerSingleton();
		bind(RestrictionSettings.class).toProvider(RestrictionSettingsProvider.class);
		bind(RestrictionTypeRegistry.class).to(DefaultRestrictionTypeRegistry.class).asEagerSingleton();
		bind(RestrictionSignalRegistry.class).to(DefaultRestrictionSignalRegistry.class).asEagerSingleton();
		bind(RestrictionTypeResolverRegistry.class).to(DefaultRestrictionTypeResolverRegistry.class).asEagerSingleton();
		bind(RestrictionActivationStore.class).asEagerSingleton();
		bind(RestrictionService.class).to(DefaultRestrictionService.class).asEagerSingleton();
	}

	@Provides
	@Singleton
	@Named("restrictionFeaturePath")
	Path provideRestrictionFeaturePath() {
		return restrictionFeaturePath;
	}
}
