package me.whereareiam.identica.feature.verification.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.feature.verification.config.defaults.VerificationDefaults;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;

import java.nio.file.Path;

@Singleton
public class VerificationSettingsProvider extends ConfigProvider<VerificationSettings> {
	@Inject
	public VerificationSettingsProvider(
			@Named("verificationFeaturePath") Path verificationFeaturePath,
			Registry<Reloadable> registry
	) {
		super(verificationFeaturePath, "settings", VerificationSettings.class, registry);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(VerificationDefaults.class);
	}
}
