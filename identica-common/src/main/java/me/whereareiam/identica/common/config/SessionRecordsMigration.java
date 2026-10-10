package me.whereareiam.identica.common.config;

import lombok.RequiredArgsConstructor;
import me.whereareiam.strata.MigrationAction;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import org.jetbrains.annotations.NotNull;

/**
 * Brings the session settings to the layout in which a session is stored once and expires with the proxy
 * holding it.
 * <p>
 * {@code sessions.activeTtl} in the settings document is removed, not renamed: it was the lifetime of a
 * cache entry refreshed twice in it, typically hours, and {@code sessions.heartbeatTimeout} is the time
 * after which the sessions of a stopped proxy disappear, so its default applies. In the replication
 * document the session namespaces are renamed after what they hold now, keeping the names an operator
 * chose: {@code session} becomes {@code records}, {@code user} becomes {@code accounts} and
 * {@code subject} becomes {@code subjects}. {@code servers} is removed with the proxy presence entries it
 * named.
 */
@RequiredArgsConstructor
final class SessionRecordsMigration implements MigrationAction<ConfigContext> {
	static final int VERSION = 2;
	static final String NAME = "session-records";

	private static final String SETTINGS = "settings";
	private static final String REPLICATION = "replication";
	private static final String NAMESPACES = "/cache/sessions/";

	/** Extension of the documents, with its dot. */
	private final @NotNull String extension;

	@Override
	public void apply(@NotNull ConfigContext files) {
		String settings = SETTINGS + extension;
		if (files.exists(settings))
			files.remove(settings, "/sessions/activeTtl");

		String replication = REPLICATION + extension;
		if (!files.exists(replication)) return;

		files.rename(replication, NAMESPACES + "session", NAMESPACES + "records");
		files.rename(replication, NAMESPACES + "user", NAMESPACES + "accounts");
		files.rename(replication, NAMESPACES + "subject", NAMESPACES + "subjects");
		files.remove(replication, NAMESPACES + "servers");
	}
}
