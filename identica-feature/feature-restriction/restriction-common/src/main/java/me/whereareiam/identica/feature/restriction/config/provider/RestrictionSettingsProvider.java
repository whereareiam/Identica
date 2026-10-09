package me.whereareiam.identica.feature.restriction.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.feature.restriction.config.RestrictionSettings;
import me.whereareiam.identica.feature.restriction.config.defaults.RestrictionSettingsDefaults;

import java.nio.file.Path;

@Singleton
public class RestrictionSettingsProvider extends ConfigProvider<RestrictionSettings> {
	@Inject
	public RestrictionSettingsProvider(
			@Named("restrictionFeaturePath") Path restrictionFeaturePath,
			Registry<Reloadable> reloadables
	) {
		super(restrictionFeaturePath, "settings", RestrictionSettings.class, reloadables);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(RestrictionSettingsDefaults.class);
	}
}
