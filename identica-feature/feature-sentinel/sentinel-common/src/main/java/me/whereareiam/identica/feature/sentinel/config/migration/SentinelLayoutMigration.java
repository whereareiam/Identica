package me.whereareiam.identica.feature.sentinel.config.migration;

import lombok.RequiredArgsConstructor;
import me.whereareiam.strata.MigrationAction;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import org.jetbrains.annotations.NotNull;

/**
 * Moves the sentinel settings and messages into the sentinel feature's documents under {@code features/sentinel/},
 * from every place an earlier version kept them, and removes them there:
 * <ul>
 *     <li>{@code connection.sentinels} in the core's settings of 2.2.1 and the May 2026 development builds, and
 *     {@code sentinels} in those of the June builds;</li>
 *     <li>the state namespace, {@code cache.sentinels} in the core's replication document, and {@code cache.sentinels}
 *     in the feature's own settings of the first June builds, which becomes {@code replication.state};</li>
 *     <li>the reason shown to a locked out player, {@code connection.resumeSentineled} in the core's messages of
 *     2.2.1 and the May builds and {@code engine.resumeSentineled} in those of the June builds, which becomes
 *     {@code resumeSpam.denied}.</li>
 * </ul>
 * It also removes the {@code _version} Configura 1.x wrote into the feature's messages.
 */
@RequiredArgsConstructor
final class SentinelLayoutMigration implements MigrationAction<ConfigContext> {
	static final int VERSION = 2;
	static final String NAME = "sentinel-layout";

	private static final String SETTINGS = "features/sentinel/settings";
	private static final String MESSAGES = "features/sentinel/messages";

	/** Extension of the documents, with its dot. */
	private final @NotNull String extension;

	@Override
	public void apply(@NotNull ConfigContext files) {
		String feature = SETTINGS + extension;
		String featureMessages = MESSAGES + extension;
		String settings = "settings" + extension;
		String replication = "replication" + extension;
		String messages = "messages" + extension;

		files.moveSection(settings, "/connection/sentinels", feature, "/sentinels");
		files.moveSection(settings, "/sentinels", feature, "/sentinels");
		files.moveIfAbsent(feature, "/cache/sentinels", feature, "/replication/state");
		files.removeIfEmpty(feature, "/cache");
		files.moveIfAbsent(replication, "/cache/sentinels", feature, "/replication/state");

		files.moveIfAbsent(messages, "/connection/resumeSentineled", featureMessages, "/resumeSpam/denied");
		files.removeIfEmpty(messages, "/connection");
		files.moveIfAbsent(messages, "/engine/resumeSentineled", featureMessages, "/resumeSpam/denied");
		files.removeVersion(featureMessages);
	}
}
