package me.whereareiam.identica.common.config.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.exception.ConfigException;
import me.whereareiam.configura.feature.polymorphic.PolymorphicFeature;
import me.whereareiam.configura.feature.polymorphic.api.annotation.Polymorphic;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.common.config.defaults.PersistenceDefaults;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.model.config.persistence.Persistence;
import me.whereareiam.identica.model.config.persistence.SqlitePersistence;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

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

	private Class<? extends Persistence> resolvePersistenceClass(Path path) {
		requireKnownType(path);

		try {
			return read(path, Persistence.class).getClass().asSubclass(Persistence.class);
		} catch (RuntimeException ignored) {
			return SqlitePersistence.class;
		}
	}

	/**
	 * Rejects a discriminator that has no mapping before the document is bound. Configura's polymorphic
	 * deserializer binds an unmapped value back to the abstract base type and recurses until the stack overflows.
	 */
	private void requireKnownType(Path path) {
		Polymorphic polymorphic = Persistence.class.getAnnotation(Polymorphic.class);

		JsonNode document;
		try {
			document = configura().readNode(path);
		} catch (RuntimeException ignored) {
			return;
		}

		JsonNode type = document.get(polymorphic.discriminator());
		if (type == null || type.isNull()) return;

		List<String> accepted = Arrays.stream(polymorphic.mappings()).map(Polymorphic.Type::value).toList();
		if (type.isTextual() && accepted.contains(type.asText())) return;

		throw new ConfigException(
				"Unknown persistence." + polymorphic.discriminator() + " " + type
						+ " in the persistence configuration. Accepted values: " + String.join(", ", accepted)
		);
	}
}
