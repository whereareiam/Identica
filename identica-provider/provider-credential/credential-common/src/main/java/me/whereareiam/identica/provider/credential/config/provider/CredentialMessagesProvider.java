package me.whereareiam.identica.provider.credential.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.config.defaults.CredentialMessagesDefaults;

import java.nio.file.Path;

@Singleton
public class CredentialMessagesProvider extends ConfigProvider<CredentialMessages> {
	@Inject
	public CredentialMessagesProvider(
			@Named("workingPath") Path workingPath,
			Registry<Reloadable> reloadables
	) {
		super(workingPath, "messages", CredentialMessages.class, reloadables);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(CredentialMessagesDefaults.class);
	}
}
