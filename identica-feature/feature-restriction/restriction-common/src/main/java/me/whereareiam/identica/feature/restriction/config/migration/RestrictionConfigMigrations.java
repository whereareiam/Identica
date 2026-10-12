package me.whereareiam.identica.feature.restriction.config.migration;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.configura.Configura;
import me.whereareiam.strata.MigrationStream;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import me.whereareiam.strata.adapter.configura.ConfiguraTarget;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.OptionalInt;

/**
 * The configuration migrations of the restriction feature, which bring its documents of an earlier installation into
 * the layout this build reads. Its module adds them to the streams Identica runs before any document is read,
 * after the core's. The version is kept as {@code _version} at the top of {@code features/restriction/settings}.
 * <p>
 * To add a migration, append it with the next number and never change or renumber one that was released.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RestrictionConfigMigrations {
	/** Name under which Strata tracks these migrations; it never changes. */
	public static final String ID = "identica/feature/restriction/config";

	private static final String VERSION_DOCUMENT = "features/restriction/settings";
	private static final String CORE_SETTINGS = "settings";

	/**
	 * Declares the migrations for a data directory.
	 *
	 * @param configura reads and writes the documents; it must have a {@code StrataFeature}
	 * @param dataPath  Identica's data directory
	 * @return migrations to run through Strata before any document is loaded
	 */
	public static @NotNull MigrationStream<ConfigContext> on(@NotNull Configura configura, @NotNull Path dataPath) {
		String extension = configura.extension();
		String versionFile = VERSION_DOCUMENT + extension;
		String settings = CORE_SETTINGS + extension;

		return MigrationStream.<ConfigContext>builder()
				.id(ID)
				.target(new ConfiguraTarget(configura, dataPath, versionFile))
				// A directory with the core's settings is an existing installation, which these migrations look
				// through from the start; an empty one is new.
				.baseline((files, latest) -> files.exists(settings) || files.exists(versionFile) ? OptionalInt.of(0) : OptionalInt.empty())
				// Changes nothing: the development builds before migrations wrote version 1 into this document.
				.migration(1, "initial-layout", files -> {})
				.migration(RestrictionLayoutMigration.VERSION, RestrictionLayoutMigration.NAME, new RestrictionLayoutMigration(extension))
				.build();
	}
}
