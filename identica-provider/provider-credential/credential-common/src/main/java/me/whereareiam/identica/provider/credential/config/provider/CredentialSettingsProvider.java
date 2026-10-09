package me.whereareiam.identica.provider.credential.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import me.whereareiam.identica.provider.credential.config.defaults.CredentialSettingsDefaults;

import java.nio.file.Path;

@Singleton
public class CredentialSettingsProvider extends ConfigProvider<CredentialSettings> {
	@Inject
	public CredentialSettingsProvider(
			@Named("workingPath") Path workingPath,
			Registry<Reloadable> reloadables
	) {
		super(workingPath, "settings", CredentialSettings.class, reloadables);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(CredentialSettingsDefaults.class);
	}
}
