package me.whereareiam.identica.feature.restriction.config.migration;

import lombok.RequiredArgsConstructor;
import me.whereareiam.strata.MigrationAction;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import org.jetbrains.annotations.NotNull;

/**
 * Moves the restriction settings of the 3.0.0 development builds from May 25 to June 13, 2026 into the restriction
 * feature's document, {@code features/restriction/settings}, and removes them where they were. 2.2.1 had no
 * restrictions.
 * <ul>
 *     <li>The toggle lifetime, {@code connection.providerJoinRestrictionTtl} in the core's settings or
 *     {@code behavior.joinRestrictionToggleTtl} in {@code providers/providers}, becomes {@code toggleTtl}.</li>
 *     <li>The namespace {@code cache.providerJoinRestrictions} of the core's replication document becomes
 *     {@code replication.toggleNamespace}.</li>
 *     <li>{@code providers/capabilities/restriction/settings} of the later June builds, which has the current
 *     layout, moves as it is.</li>
 * </ul>
 */
@RequiredArgsConstructor
final class RestrictionLayoutMigration implements MigrationAction<ConfigContext> {
	static final int VERSION = 2;
	static final String NAME = "restriction-layout";

	private static final String SETTINGS = "features/restriction/settings";

	/** Extension of the documents, with its dot. */
	private final @NotNull String extension;

	@Override
	public void apply(@NotNull ConfigContext files) {
		String feature = SETTINGS + extension;
		String settings = "settings" + extension;
		String providers = "providers/providers" + extension;
		String replication = "replication" + extension;

		files.moveFile("providers/capabilities/restriction/settings" + extension, feature);
		files.moveIfAbsent(settings, "/connection/providerJoinRestrictionTtl", feature, "/toggleTtl");
		files.removeIfEmpty(settings, "/connection");
		files.moveIfAbsent(providers, "/behavior/joinRestrictionToggleTtl", feature, "/toggleTtl");
		files.moveIfAbsent(replication, "/cache/providerJoinRestrictions", feature, "/replication/toggleNamespace");

		// The join restriction's documents are below it, and the join restriction removes them.
		files.deleteDirectoryIfEmpty("providers/capabilities/restriction");
		files.deleteDirectoryIfEmpty("providers/capabilities");
	}
}
