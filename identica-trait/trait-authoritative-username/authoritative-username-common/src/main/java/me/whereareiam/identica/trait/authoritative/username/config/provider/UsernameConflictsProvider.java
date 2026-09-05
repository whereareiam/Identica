package me.whereareiam.identica.trait.authoritative.username.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.trait.authoritative.username.config.defaults.UsernameConflictsDefaults;
import me.whereareiam.identica.model.config.provider.Conflicts;

import java.nio.file.Path;

/**
 * Feature-owned username rules, overridden per key by the generic provider conflicts config.
 */
@Singleton
public class UsernameConflictsProvider extends ConfigProvider<Conflicts> {
	@Inject
	public UsernameConflictsProvider(
			@Named("usernamePath") Path featurePath,
			Registry<Reloadable> reloadables
	) {
		super(featurePath, "conflicts", Conflicts.class, reloadables);
	}

	@Override
	protected Configura configura() {
		return versioned(Config.configured().withDefaults(UsernameConflictsDefaults.class), Conflicts.class);
	}
}
