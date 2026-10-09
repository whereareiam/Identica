package me.whereareiam.identica.feature.verification.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.feature.verification.config.VerificationCommands;
import me.whereareiam.identica.feature.verification.config.defaults.VerificationCommandsDefaults;

import java.nio.file.Path;

@Singleton
public class VerificationCommandsProvider extends ConfigProvider<VerificationCommands> {
	@Inject
	public VerificationCommandsProvider(
			@Named("verificationFeaturePath") Path verificationFeaturePath,
			Registry<Reloadable> registry
	) {
		super(verificationFeaturePath, "commands", VerificationCommands.class, registry);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(VerificationCommandsDefaults.class);
	}
}
