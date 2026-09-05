package me.whereareiam.identica.feature.verification.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.config.defaults.VerificationMessagesDefaults;

import java.nio.file.Path;

@Singleton
public class VerificationMessagesProvider extends ConfigProvider<VerificationMessages> {
	@Inject
	public VerificationMessagesProvider(
			@Named("verificationFeaturePath") Path verificationFeaturePath,
			Registry<Reloadable> registry
	) {
		super(verificationFeaturePath, "messages", VerificationMessages.class, registry);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(VerificationMessagesDefaults.class);
	}
}
