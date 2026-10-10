package me.whereareiam.identica.common.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.configura.Configura;
import me.whereareiam.strata.MigrationStream;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import me.whereareiam.strata.adapter.configura.ConfiguraTarget;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * The migrations of Identica's configuration: the numbered changes that bring the documents of an
 * older installation into the layout this build reads. This is where the next one is added.
 * <p>
 * They apply to the whole data directory: the core documents, {@code providers/providers} and
 * {@code providers/conflicts}, and the documents of the compiled features and traits under
 * {@code features/} and {@code identity/}. On startup the ones an installation has not seen yet run
 * in order, before any document is loaded. The number of the last one that ran is kept as
 * {@code _version} at the top of the settings document.
 * <p>
 * To add a migration, append it with the next number and never change or renumber one that was
 * released. A migration with real logic becomes a class of its own that is listed here; only a
 * migration that changes nothing is written inline.
 * <p>
 * The directories below {@code providers/} that belong to a provider are not covered. A provider
 * that has to migrate its documents declares its own migrations on its working directory.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfigMigrations {
	/** Name under which Strata tracks these migrations; it never changes. */
	public static final String ID = "identica/config";

	private static final String VERSION_DOCUMENT = "settings";

	/**
	 * Declares the migrations for a data directory.
	 *
	 * @param configura reads and writes the documents; it must have a {@code StrataFeature}, which
	 *                  keeps {@code _version} in a document when Identica rewrites it
	 * @param dataPath Identica's data directory
	 * @return migrations to run through Strata before any document is loaded
	 */
	public static @NotNull MigrationStream<ConfigContext> on(@NotNull Configura configura, @NotNull Path dataPath) {
		String versionFile = VERSION_DOCUMENT + configura.extension();

		return MigrationStream.<ConfigContext>builder()
				.id(ID)
				.target(new ConfiguraTarget(configura, dataPath, versionFile))
				// Asked once for a directory without a version: one without settings is a new installation,
				// whose documents are about to be written in their current layout, so nothing has to run.
				.baseline((files, latest) -> files.exists(versionFile) ? 0 : latest)
				// Changes nothing: version 1 is the layout every installation had before migrations existed.
				.migration(1, "initial-layout", files -> {})
				.build();
	}
}
