package me.whereareiam.identica.provider.premium.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.provider.premium.config.PremiumCommands;
import me.whereareiam.identica.provider.premium.config.defaults.PremiumCommandsDefaults;

import java.nio.file.Path;

@Singleton
public class PremiumCommandsProvider extends ConfigProvider<PremiumCommands> {
	@Inject
	public PremiumCommandsProvider(
			@Named("workingPath") Path workingPath,
			Registry<Reloadable> reloadables
	) {
		super(workingPath, "commands", PremiumCommands.class, reloadables);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(PremiumCommandsDefaults.class);
	}
}
