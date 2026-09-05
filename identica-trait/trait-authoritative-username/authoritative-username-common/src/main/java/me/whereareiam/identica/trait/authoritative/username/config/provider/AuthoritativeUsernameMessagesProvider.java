package me.whereareiam.identica.trait.authoritative.username.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.trait.authoritative.username.config.defaults.AuthoritativeUsernameMessagesDefaults;
import me.whereareiam.identica.trait.authoritative.username.model.AuthoritativeUsernameMessages;

import java.nio.file.Path;

@Singleton
public class AuthoritativeUsernameMessagesProvider extends ConfigProvider<AuthoritativeUsernameMessages> {
	@Inject
	public AuthoritativeUsernameMessagesProvider(
			@Named("usernamePath") Path featurePath,
			Registry<Reloadable> reloadables
	) {
		super(featurePath, "messages", AuthoritativeUsernameMessages.class, reloadables);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(AuthoritativeUsernameMessagesDefaults.class);
	}
}
