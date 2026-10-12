package me.whereareiam.identica.feature.restriction.join.config.migration;

import lombok.RequiredArgsConstructor;
import me.whereareiam.strata.MigrationAction;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Moves the join restriction of the 3.0.0 development builds from May 25 to June 13, 2026 into the join
 * restriction's documents under {@code features/restriction/join/}, and removes it where it was. 2.2.1 had no join
 * restriction.
 * <ul>
 *     <li>A scenario's {@code allowProviderRestrictionResumeBypass}, in {@code connection.scenarios} of the core's
 *     settings or in {@code scenarios} of its engine document, becomes {@code resumeBypass.authentication} or
 *     {@code resumeBypass.registration}; the migration scenario's has no counterpart and is dropped.</li>
 *     <li>The messages under {@code commands.admin.providerRestriction} of the core's messages become the join
 *     restriction's {@code commands}, and the reason a player is refused with,
 *     {@code connection.providerRestriction.denied} or {@code providers.providerRestriction.denied}, becomes its
 *     {@code denied}. The unused {@code prepare.providerRestricted} is dropped.</li>
 *     <li>The command definitions {@code admin-provider-restriction-enable}, {@code -disable} and {@code -status}
 *     of the core's commands become {@code admin-provider-restriction-join-enable}, {@code -disable} and
 *     {@code -status}.</li>
 *     <li>The documents under {@code providers/capabilities/restriction/join/} of the later June builds, which have
 *     the current layout, move as they are.</li>
 * </ul>
 * It also removes the {@code _version} Configura 1.x wrote into the join restriction's messages and commands. What a
 * provider entry says about its join restriction is moved by the core, as it belongs to {@code providers/providers}.
 */
@RequiredArgsConstructor
final class JoinRestrictionLayoutMigration implements MigrationAction<ConfigContext> {
	static final int VERSION = 2;
	static final String NAME = "join-restriction-layout";

	private static final String SETTINGS = "features/restriction/join/settings";
	private static final String MESSAGES = "features/restriction/join/messages";
	private static final String COMMANDS = "features/restriction/join/commands";
	private static final String CAPABILITIES = "providers/capabilities/restriction/join";
	private static final String BYPASS = "allowProviderRestrictionResumeBypass";
	private static final List<String> DEFINITIONS = List.of("enable", "disable", "status");

	/** Extension of the documents, with its dot. */
	private final @NotNull String extension;

	@Override
	public void apply(@NotNull ConfigContext files) {
		String feature = SETTINGS + extension;
		String featureMessages = MESSAGES + extension;
		String featureCommands = COMMANDS + extension;
		String settings = "settings" + extension;
		String engine = "engine" + extension;
		String messages = "messages" + extension;
		String commands = "commands" + extension;

		for (String scenario : List.of("authentication", "registration")) {
			files.moveIfAbsent(settings, "/connection/scenarios/" + scenario + "/" + BYPASS, feature, "/resumeBypass/" + scenario);
			files.removeIfEmpty(settings, "/connection/scenarios/" + scenario);
			files.moveIfAbsent(engine, "/scenarios/" + scenario + "/" + BYPASS, feature, "/resumeBypass/" + scenario);
		}
		files.remove(settings, "/connection/scenarios/migration/" + BYPASS);
		files.removeIfEmpty(settings, "/connection/scenarios/migration");
		files.remove(engine, "/scenarios/migration/" + BYPASS);

		files.moveFile(CAPABILITIES + "/messages" + extension, featureMessages);
		files.moveSection(messages, "/commands/admin/providerRestriction", featureMessages, "/commands");
		files.moveIfAbsent(messages, "/connection/providerRestriction/denied", featureMessages, "/denied");
		files.removeIfEmpty(messages, "/connection/providerRestriction");
		files.moveIfAbsent(messages, "/providers/providerRestriction/denied", featureMessages, "/denied");
		files.removeIfEmpty(messages, "/providers/providerRestriction");
		files.remove(messages, "/connection/prepare/providerRestricted");
		files.removeIfEmpty(messages, "/connection/prepare");
		files.remove(messages, "/engine/prepare/providerRestricted");
		files.removeIfEmpty(messages, "/engine/prepare");

		files.moveFile(CAPABILITIES + "/commands" + extension, featureCommands);
		for (String definition : DEFINITIONS)
			files.moveIfAbsent(commands, "/commands/admin-provider-restriction-" + definition, featureCommands, "/commands/admin-provider-restriction-join-" + definition);
		files.removeIfEmpty(commands, "/commands");

		files.deleteDirectoryIfEmpty(CAPABILITIES);
		files.deleteDirectoryIfEmpty("providers/capabilities/restriction");
		files.deleteDirectoryIfEmpty("providers/capabilities");

		files.removeVersion(featureMessages);
		files.removeVersion(featureCommands);
	}
}
