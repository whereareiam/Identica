package me.whereareiam.identica.feature.sentinel.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.feature.sentinel.config.defaults.SentinelMessagesDefaults;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelMessages;

import java.nio.file.Path;

@Singleton
public class SentinelMessagesProvider extends ConfigProvider<SentinelMessages> {
	@Inject
	public SentinelMessagesProvider(
			@Named("sentinelFeaturePath") Path sentinelFeaturePath,
			Registry<Reloadable> registry
	) {
		super(sentinelFeaturePath, "messages", SentinelMessages.class, registry);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(SentinelMessagesDefaults.class);
	}
}
