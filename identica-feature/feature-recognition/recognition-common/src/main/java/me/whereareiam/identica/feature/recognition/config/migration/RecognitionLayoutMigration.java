package me.whereareiam.identica.feature.recognition.config.migration;

import lombok.RequiredArgsConstructor;
import me.whereareiam.strata.MigrationAction;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import org.jetbrains.annotations.NotNull;

/**
 * Moves the recognition settings into the recognition feature's document, {@code features/recognition/settings},
 * from every place an earlier version kept them, the newest first, and removes them there:
 * <ul>
 *     <li>{@code providers/capabilities/recognition/settings} of the June 2026 development builds after June 8,
 *     which has the current layout;</li>
 *     <li>{@code sessions.recognition} in the core's settings of the June 8 builds;</li>
 *     <li>{@code connection.sessions.recognition} in the core's settings of 2.2.1, where the untrusted addresses
 *     are {@code untrustedIps}, and of the May 2026 builds, where they are {@code eligibility.untrustedIps};</li>
 *     <li>the snapshot namespace, {@code cache.sessions.recognition.snapshot} in the core's replication document,
 *     which becomes {@code replication.snapshotNamespace}.</li>
 * </ul>
 * What a provider entry says about recognition is moved by the core, as it belongs to {@code providers/providers}.
 */
@RequiredArgsConstructor
final class RecognitionLayoutMigration implements MigrationAction<ConfigContext> {
	static final int VERSION = 2;
	static final String NAME = "recognition-layout";

	private static final String FEATURE = "features/recognition/settings";

	/** Extension of the documents, with its dot. */
	private final @NotNull String extension;

	@Override
	public void apply(@NotNull ConfigContext files) {
		String feature = FEATURE + extension;
		String settings = "settings" + extension;
		String replication = "replication" + extension;

		files.moveFile("providers/capabilities/recognition/settings" + extension, feature);
		files.deleteDirectoryIfEmpty("providers/capabilities/recognition");
		files.deleteDirectoryIfEmpty("providers/capabilities");

		files.moveSection(settings, "/sessions/recognition", feature, "");
		files.moveIfAbsent(settings, "/connection/sessions/recognition/untrustedIps", settings, "/connection/sessions/recognition/eligibility/untrustedIps");
		files.moveSection(settings, "/connection/sessions/recognition", feature, "");

		files.moveIfAbsent(replication, "/cache/sessions/recognition/snapshot", feature, "/replication/snapshotNamespace");
		files.removeIfEmpty(replication, "/cache/sessions/recognition");
	}
}
