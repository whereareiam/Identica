package me.whereareiam.identica.adapter.database.schema;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.dialectica.entity.annotation.Table;
import me.whereareiam.strata.MigrationStream;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Map;

/**
 * Checks that the owner of a schema uses the Dialectica and Strata Identica loaded. Another plugin that bundles
 * either library without relocating it can hand its own copy to the owner's classes; Identica then does not
 * recognise the owner's annotations and migrations, and fails with a message that names neither plugin.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SharedLibraries {
	/** A class of each library, by the library's name. */
	private static final Map<String, Class<?>> LIBRARIES = Map.of(
			"Dialectica", Table.class,
			"Strata", MigrationStream.class
	);

	/**
	 * Fails when the owner's classes see another copy of Dialectica or Strata than Identica's.
	 *
	 * @param owner a class of the schema's owner, such as its {@code DatabaseSchema}
	 * @throws IllegalStateException naming the jars of both copies
	 */
	static void requireShared(@NotNull Class<?> owner) {
		ClassLoader loader = owner.getClassLoader();
		if (loader == null) return;

		for (Map.Entry<String, Class<?>> library : LIBRARIES.entrySet()) {
			Class<?> identica = library.getValue();
			Class<?> seen = find(identica.getName(), loader);
			if (seen == null || seen == identica) continue;

			throw new IllegalStateException(String.format(
					"%s in %s uses %s from %s, not the copy Identica loaded from %s. A plugin bundles %s without "
							+ "relocating it; remove that plugin's copy or have it relocate %s.",
					owner.getName(),
					origin(owner),
					library.getKey(),
					origin(seen),
					origin(identica),
					library.getKey(),
					library.getKey()
			));
		}
	}

	/**
	 * Describes where Identica's own copies of the libraries come from.
	 */
	static @NotNull String describe() {
		StringBuilder origins = new StringBuilder();
		for (Map.Entry<String, Class<?>> library : LIBRARIES.entrySet()) {
			if (!origins.isEmpty()) origins.append(", ");
			origins.append(library.getKey()).append(" from ").append(origin(library.getValue()));
		}

		return origins.toString();
	}

	private static Class<?> find(@NotNull String name, @NotNull ClassLoader loader) {
		try {
			return Class.forName(name, false, loader);
		} catch (ClassNotFoundException | LinkageError ignored) {
			return null;
		}
	}

	private static @NotNull String origin(@NotNull Class<?> type) {
		CodeSource source = type.getProtectionDomain().getCodeSource();
		if (source == null || source.getLocation() == null) return "an unknown location";

		try {
			Path path = Path.of(source.getLocation().toURI());
			return path.getFileName() != null ? path.getFileName().toString() : path.toString();
		} catch (Exception ignored) {
			return source.getLocation().toString();
		}
	}
}
