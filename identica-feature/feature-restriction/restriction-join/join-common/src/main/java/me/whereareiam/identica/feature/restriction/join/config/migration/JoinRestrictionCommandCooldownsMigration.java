package me.whereareiam.identica.feature.restriction.join.config.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import me.whereareiam.strata.MigrationAction;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import org.jetbrains.annotations.NotNull;

/**
 * Gives every command of {@code features/restriction/join/commands} that still has the cooldown the development
 * builds wrote, enabled for two seconds in the group {@code global}, a cooldown of its own, as the core's migration
 * does for its commands document. A cooldown with another duration or group, or a disabled one, stays as it is.
 */
@RequiredArgsConstructor
final class JoinRestrictionCommandCooldownsMigration implements MigrationAction<ConfigContext> {
	static final int VERSION = 3;
	static final String NAME = "command-cooldowns";

	private static final String COMMANDS = "features/restriction/join/commands";

	/** Extension of the documents, with its dot. */
	private final @NotNull String extension;

	@Override
	public void apply(@NotNull ConfigContext files) {
		String commands = COMMANDS + extension;
		if (!files.exists(commands) || !(files.document(commands).get("commands") instanceof ObjectNode definitions)) return;

		for (JsonNode definition : definitions) {
			if (definition.path("cooldown") instanceof ObjectNode cooldown && isEarlierDefault(cooldown))
				cooldown.put("group", "");
		}
	}

	private static boolean isEarlierDefault(@NotNull ObjectNode cooldown) {
		return cooldown.path("enabled").asBoolean(false)
				&& cooldown.path("duration").asInt(0) == 2
				&& cooldown.path("group").asText("").equals("global");
	}
}
