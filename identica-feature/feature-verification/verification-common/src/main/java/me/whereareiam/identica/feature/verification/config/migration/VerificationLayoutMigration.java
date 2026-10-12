package me.whereareiam.identica.feature.verification.config.migration;

import lombok.RequiredArgsConstructor;
import me.whereareiam.strata.MigrationAction;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Moves the verification settings, messages and commands of 2.2.1 and of the development builds up to June 8,
 * 2026 into the verification feature's documents under {@code features/verification/}, and removes them from the
 * core's documents:
 * <ul>
 *     <li>the {@code verification} document becomes {@code features/verification/settings};</li>
 *     <li>{@code commands.verification} and {@code commands.admin.verification} in the core's messages become
 *     {@code commands} and {@code commands.admin} of the feature's messages, and the method messages under
 *     {@code commands.verification.methods} become its {@code methods};</li>
 *     <li>the verification command definitions leave the core's commands for the feature's, under the same
 *     names.</li>
 * </ul>
 * It also removes the {@code _version} Configura 1.x wrote into the feature's messages and commands. What a
 * provider entry says about verification is moved by the core, as it belongs to {@code providers/providers}.
 */
@RequiredArgsConstructor
final class VerificationLayoutMigration implements MigrationAction<ConfigContext> {
	static final int VERSION = 2;
	static final String NAME = "verification-layout";

	private static final String SETTINGS = "features/verification/settings";
	private static final String MESSAGES = "features/verification/messages";
	private static final String COMMANDS = "features/verification/commands";
	private static final List<String> DEFINITIONS = List.of(
			"verification",
			"verification-confirm",
			"verification-disable",
			"verification-enroll",
			"verification-enroll-cancel",
			"verification-enroll-confirm",
			"verification-status",
			"verification-use",
			"admin-verification-reset"
	);

	/** Extension of the documents, with its dot. */
	private final @NotNull String extension;

	@Override
	public void apply(@NotNull ConfigContext files) {
		String settings = SETTINGS + extension;
		String featureMessages = MESSAGES + extension;
		String featureCommands = COMMANDS + extension;
		String messages = "messages" + extension;
		String commands = "commands" + extension;

		files.moveFile("verification" + extension, settings);

		files.moveSection(messages, "/commands/verification/methods", featureMessages, "/methods");
		files.moveSection(messages, "/commands/verification", featureMessages, "/commands");
		files.moveSection(messages, "/commands/admin/verification", featureMessages, "/commands/admin");

		for (String definition : DEFINITIONS)
			files.moveIfAbsent(commands, "/commands/" + definition, featureCommands, "/commands/" + definition);
		files.removeIfEmpty(commands, "/commands");

		files.removeVersion(featureMessages);
		files.removeVersion(featureCommands);
	}
}
