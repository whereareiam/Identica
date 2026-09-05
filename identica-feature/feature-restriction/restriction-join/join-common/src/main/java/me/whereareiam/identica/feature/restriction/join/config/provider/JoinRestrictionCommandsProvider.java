package me.whereareiam.identica.feature.restriction.join.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionCommands;
import me.whereareiam.identica.feature.restriction.join.config.defaults.JoinRestrictionCommandsDefaults;

import java.nio.file.Path;

@Singleton
public class JoinRestrictionCommandsProvider extends ConfigProvider<JoinRestrictionCommands> {
	@Inject
	public JoinRestrictionCommandsProvider(
			@Named("joinRestrictionFeaturePath") Path joinRestrictionFeaturePath,
			Registry<Reloadable> reloadables
	) {
		super(joinRestrictionFeaturePath, "commands", JoinRestrictionCommands.class, reloadables);
	}

	@Override
	protected Configura configura() {
		return versioned(Config.configured().withDefaults(JoinRestrictionCommandsDefaults.class), JoinRestrictionCommands.class);
	}
}
