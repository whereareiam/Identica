package me.whereareiam.identica.common.config.migration;

import lombok.RequiredArgsConstructor;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import me.whereareiam.strata.MigrationAction;
import org.jetbrains.annotations.NotNull;

/**
 * Replaces the username conflict rules of 2.2.1 and the 3.0.0 development builds with the current defaults.
 * <p>
 * Those rules only renamed the name a player was shown under while another player with the same name was online,
 * through {@code format_display} resolvers listed under {@code rules.username}. The current rules decide who keeps
 * a stored username and match on what providers guarantee, so the old ones have no counterpart: the
 * {@code identity/username/conflicts} document written as {@code rules.username} is deleted and written again with
 * the defaults on the next load, and {@code providers/conflicts}, which only held rules of other kinds that nothing
 * reads, is deleted. The messages only those rules used, {@code pipeline.policy.conflictDenied} and
 * {@code pipeline.policy.entrypointRequired} in {@code identity/username/messages}, are removed.
 */
@RequiredArgsConstructor
final class UsernameConflictRulesMigration implements MigrationAction<ConfigContext> {
	static final int VERSION = 8;
	static final String NAME = "username-conflict-rules";

	private static final String CONFLICTS = "identity/username/conflicts";
	private static final String PROVIDER_CONFLICTS = "providers/conflicts";
	private static final String MESSAGES = "identity/username/messages";

	/** Extension of the documents, with its dot. */
	private final @NotNull String extension;

	@Override
	public void apply(@NotNull ConfigContext files) {
		String conflicts = CONFLICTS + extension;
		if (files.exists(conflicts) && files.document(conflicts).path("rules").isObject()) files.delete(conflicts);

		String providerConflicts = PROVIDER_CONFLICTS + extension;
		if (files.exists(providerConflicts)) files.delete(providerConflicts);

		String messages = MESSAGES + extension;
		if (!files.exists(messages)) return;

		files.remove(messages, "/pipeline/policy/conflictDenied");
		files.remove(messages, "/pipeline/policy/entrypointRequired");
		files.removeIfEmpty(messages, "/pipeline/policy");
		files.removeIfEmpty(messages, "/pipeline");
	}
}
