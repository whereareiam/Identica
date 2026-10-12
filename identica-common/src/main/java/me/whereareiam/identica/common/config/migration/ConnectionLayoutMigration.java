package me.whereareiam.identica.common.config.migration;

import lombok.RequiredArgsConstructor;
import me.whereareiam.strata.MigrationAction;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Splits the {@code connection} sections of 2.2.1 and of the May 2026 development builds into the core documents
 * that hold those settings and messages now. What a feature collects from them stays for that feature's own
 * migrations, and a {@code connection} section goes once nothing is left in it.
 * <p>
 * In the settings document, {@code connection} becomes:
 * <ul>
 *     <li>{@code handshakeInstructionTtl} and {@code prepareStateTtl} of the engine's {@code behavior}, the
 *     second as {@code bridgeTtl}, which the same stores read;</li>
 *     <li>{@code attemptTtl} of the {@code behavior} of {@code providers/providers};</li>
 *     <li>{@code reservationTtl} and {@code uniqueIdMode} of the settings' {@code identity};</li>
 *     <li>{@code routing}, which becomes the routing document;</li>
 *     <li>{@code sessions.concurrencyPolicy} of the settings' {@code sessions};</li>
 *     <li>a scenario's own settings, which become those of the engine's {@code scenarios}.</li>
 * </ul>
 * {@code sessions.activeTtl} and a scenario's {@code advanceLockTtl}, also in the engine document of the June
 * builds, have no counterpart and are dropped.
 * <p>
 * In the messages document, a scenario's messages under {@code connection} become those under {@code scenarios},
 * and {@code connection.journey}, the core's messages of {@code connection.prepare} and
 * {@code connection.concurrentLoginKick} become those under {@code engine}. A scenario's {@code advanceBusy} is
 * dropped with the advance lock. The session command's {@code commands.admin.sessions.info} becomes
 * {@code commands.admin.sessions.status}.
 */
@RequiredArgsConstructor
final class ConnectionLayoutMigration implements MigrationAction<ConfigContext> {
	static final int VERSION = 5;
	static final String NAME = "connection-layout";

	private static final List<String> SCENARIOS = List.of("authentication", "registration", "migration");
	private static final List<String> SCENARIO_SETTINGS = List.of(
			"pipelineTtl",
			"allowResume",
			"journeyMode",
			"journeyPolicy",
			"pipelineConcurrencyPolicy",
			"autoSelectSingleProvider"
	);

	/** Extension of the documents, with its dot. */
	private final @NotNull String extension;

	@Override
	public void apply(@NotNull ConfigContext files) {
		moveSettings(files);
		moveMessages(files);
	}

	private void moveSettings(ConfigContext files) {
		String settings = "settings" + extension;
		String engine = "engine" + extension;

		files.moveIfAbsent(settings, "/connection/handshakeInstructionTtl", engine, "/behavior/handshakeInstructionTtl");
		files.moveIfAbsent(settings, "/connection/prepareStateTtl", engine, "/behavior/bridgeTtl");
		files.moveIfAbsent(settings, "/connection/attemptTtl", "providers/providers" + extension, "/behavior/attemptTtl");
		files.moveIfAbsent(settings, "/connection/reservationTtl", settings, "/identity/reservationTtl");
		files.moveIfAbsent(settings, "/connection/uniqueIdMode", settings, "/identity/uniqueIdMode");
		files.moveSection(settings, "/connection/routing", "routing" + extension, "");
		files.moveIfAbsent(settings, "/connection/sessions/concurrencyPolicy", settings, "/sessions/concurrencyPolicy");
		files.remove(settings, "/connection/sessions/activeTtl");
		files.removeIfEmpty(settings, "/connection/sessions");
		files.remove(settings, "/sessions/activeTtl");

		for (String scenario : SCENARIOS) {
			String section = "/connection/scenarios/" + scenario;
			files.remove(settings, section + "/advanceLockTtl");
			files.remove(engine, "/scenarios/" + scenario + "/advanceLockTtl");
			for (String setting : SCENARIO_SETTINGS)
				files.moveIfAbsent(settings, section + "/" + setting, engine, "/scenarios/" + scenario + "/" + setting);
			files.removeIfEmpty(settings, section);
		}
		files.removeIfEmpty(settings, "/connection");
	}

	private void moveMessages(ConfigContext files) {
		String messages = "messages" + extension;

		for (String scenario : SCENARIOS) {
			files.remove(messages, "/connection/" + scenario + "/advanceBusy");
			files.remove(messages, "/scenarios/" + scenario + "/advanceBusy");
			files.moveSection(messages, "/connection/" + scenario, messages, "/scenarios/" + scenario);
		}

		files.moveSection(messages, "/connection/journey", messages, "/engine/journey");
		files.moveIfAbsent(messages, "/connection/prepare/handshakeDenied", messages, "/engine/prepare/handshakeDenied");
		files.moveSection(messages, "/connection/prepare/errors", messages, "/engine/prepare/errors");
		files.moveIfAbsent(messages, "/connection/concurrentLoginKick", messages, "/engine/concurrentLoginKick");
		files.removeIfEmpty(messages, "/connection/prepare");
		files.removeIfEmpty(messages, "/connection");

		files.moveSection(messages, "/commands/admin/sessions/info", messages, "/commands/admin/sessions/status");
	}
}
