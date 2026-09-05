package me.whereareiam.identica.common.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.common.config.defaults.RoutingDefaults;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.model.config.Routing;

import java.nio.file.Path;

@Singleton
public class RoutingProvider extends ConfigProvider<Routing> {
	@Inject
	public RoutingProvider(
			@Named("dataPath") Path dataPath,
			Registry<Reloadable> registry
	) {
		super(dataPath, "routing", Routing.class, registry);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(RoutingDefaults.class);
	}
}
