package me.whereareiam.identica.common.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.feature.polymorphic.PolymorphicFeature;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.common.config.defaults.PersistenceDefaults;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.model.config.persistence.Persistence;
import me.whereareiam.identica.model.config.persistence.SqlitePersistence;

import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class PersistenceProvider extends ConfigProvider<Persistence> {
	@Inject
	public PersistenceProvider(
			@Named("dataPath") Path dataPath,
			Registry<Reloadable> registry
	) {
		super(dataPath, "persistence", Persistence.class, registry);
	}

	@Override
	protected Configura configura() {
		return Config.configured()
				.withDefaults(PersistenceDefaults.class)
				.withFeature(PolymorphicFeature.defaults());
	}

	@Override
	protected Class<? extends Persistence> resolveType(Path path) {
		return resolvePersistenceClass(path);
	}

	/**
	 * A new installation uses SQLite. An existing file must name a type Identica knows: one it cannot read stops
	 * the startup instead of silently running on an empty SQLite database.
	 */
	private Class<? extends Persistence> resolvePersistenceClass(Path path) {
		if (!Files.exists(Path.of(path + configura().extension()))) return SqlitePersistence.class;

		return read(path, Persistence.class).getClass().asSubclass(Persistence.class);
	}
}
