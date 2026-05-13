package me.whereareiam.identica.provider.password.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.password.config.defaults.PasswordMessagesDefaults;

import java.nio.file.Path;

@Singleton
public class PasswordMessagesProvider extends ConfigProvider<PasswordMessages> {
	@Inject
	public PasswordMessagesProvider(
			@Named("workingPath") Path workingPath,
			Registry<Reloadable> reloadables
	) {
		super(
				workingPath,
				"messages",
				PasswordMessages.class,
				reloadables,
				configure(PasswordMessagesDefaults.class, PasswordMessages.class)
		);
	}
}
