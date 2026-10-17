package me.whereareiam.identica.common.config.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import me.whereareiam.strata.MigrationAction;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import org.jetbrains.annotations.NotNull;

/**
 * Gives every command of the commands document that still has the cooldown 2.2.1 and the development builds wrote,
 * enabled for two seconds in the group {@code global}, a cooldown of its own. Cooldowns had no effect until now, so
 * that group was never chosen for what it does; left as it is, every command would refuse a player for two seconds
 * after any other. A cooldown with another duration or group, or a disabled one, stays as it is.
 */
@RequiredArgsConstructor
final class CommandCooldownsMigration implements MigrationAction<ConfigContext> {
	static final int VERSION = 10;
	static final String NAME = "command-cooldowns";

	private static final String COMMANDS = "commands";

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
