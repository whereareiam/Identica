package me.whereareiam.identica.provider.premium.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.provider.premium.config.defaults.PremiumMessagesDefaults;

import java.nio.file.Path;

@Singleton
public class PremiumMessagesProvider extends ConfigProvider<PremiumMessages> {
	@Inject
	public PremiumMessagesProvider(
			@Named("workingPath") Path workingPath,
			Registry<Reloadable> reloadables
	) {
		super(workingPath, "messages", PremiumMessages.class, reloadables);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(PremiumMessagesDefaults.class);
	}
}
