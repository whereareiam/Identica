package me.whereareiam.identica.provider.password.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.provider.password.config.PasswordSettings;
import me.whereareiam.identica.provider.password.config.defaults.PasswordSettingsDefaults;

import java.nio.file.Path;

@Singleton
public class PasswordSettingsProvider extends ConfigProvider<PasswordSettings> {
	@Inject
	public PasswordSettingsProvider(
			@Named("workingPath") Path workingPath,
			Registry<Reloadable> reloadables
	) {
		super(
				workingPath,
				"settings",
				PasswordSettings.class,
				reloadables,
				configure(PasswordSettingsDefaults.class, PasswordSettings.class)
		);
	}
}
