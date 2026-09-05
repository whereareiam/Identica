package me.whereareiam.identica.common.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.common.config.defaults.CommandsDefaults;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.model.config.Commands;

import java.nio.file.Path;

@Singleton
public class CommandsProvider extends ConfigProvider<Commands> {
	@Inject
	public CommandsProvider(
			@Named("dataPath") Path dataPath,
			Registry<Reloadable> registry
	) {
		super(dataPath, "commands", Commands.class, registry);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(CommandsDefaults.class);
	}
}
