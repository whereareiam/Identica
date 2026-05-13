package me.whereareiam.identica.provider.password.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.provider.password.config.PasswordCommands;
import me.whereareiam.identica.provider.password.config.defaults.PasswordCommandsDefaults;

import java.nio.file.Path;

@Singleton
public class PasswordCommandsProvider extends ConfigProvider<PasswordCommands> {
	@Inject
	public PasswordCommandsProvider(
			@Named("workingPath") Path workingPath,
			Registry<Reloadable> reloadables
	) {
		super(
				workingPath,
				"commands",
				PasswordCommands.class,
				reloadables,
				configure(PasswordCommandsDefaults.class, PasswordCommands.class)
		);
	}
}
