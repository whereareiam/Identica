package me.whereareiam.identica.feature.sentinel.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.feature.sentinel.config.defaults.SentinelSettingsDefaults;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelSettings;

import java.nio.file.Path;

@Singleton
public class SentinelSettingsProvider extends ConfigProvider<SentinelSettings> {
	@Inject
	public SentinelSettingsProvider(
			@Named("sentinelFeaturePath") Path sentinelFeaturePath,
			Registry<Reloadable> registry
	) {
		super(sentinelFeaturePath, "settings", SentinelSettings.class, registry);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(SentinelSettingsDefaults.class);
	}
}
