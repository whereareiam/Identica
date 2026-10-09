package me.whereareiam.identica.feature.restriction.join.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionSettings;
import me.whereareiam.identica.feature.restriction.join.config.defaults.JoinRestrictionSettingsDefaults;

import java.nio.file.Path;

@Singleton
public class JoinRestrictionSettingsProvider extends ConfigProvider<JoinRestrictionSettings> {
	@Inject
	public JoinRestrictionSettingsProvider(
			@Named("joinRestrictionFeaturePath") Path joinRestrictionFeaturePath,
			Registry<Reloadable> reloadables
	) {
		super(joinRestrictionFeaturePath, "settings", JoinRestrictionSettings.class, reloadables);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(JoinRestrictionSettingsDefaults.class);
	}
}
