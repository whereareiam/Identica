package me.whereareiam.identica.common.config.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import me.whereareiam.strata.MigrationAction;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Brings the reservation commands to the layout in which they take a username instead of a reservation key.
 * <p>
 * In the commands document, the {@code key} argument of {@code admin-reservation-set},
 * {@code admin-reservation-info} and {@code admin-reservation-delete} becomes {@code username}, both in the
 * usage, which the command is registered from, and in the argument descriptions. The rest of a usage and a
 * description an operator wrote are kept.
 */
@RequiredArgsConstructor
final class ReservationUsernameMigration implements MigrationAction<ConfigContext> {
	static final int VERSION = 3;
	static final String NAME = "reservation-username";

	private static final String COMMANDS = "commands";
	private static final List<String> DEFINITIONS = List.of(
			"admin-reservation-set",
			"admin-reservation-info",
			"admin-reservation-delete"
	);

	/** Extension of the documents, with its dot. */
	private final @NotNull String extension;

	@Override
	public void apply(@NotNull ConfigContext files) {
		String commands = COMMANDS + extension;
		if (!files.exists(commands)) return;

		ObjectNode document = files.document(commands);
		for (String definition : DEFINITIONS) {
			String path = "/commands/" + definition;
			JsonNode usage = document.at(path + "/usage");
			if (usage.isTextual())
				files.put(commands, path + "/usage", TextNode.valueOf(usage.asText()
						.replace("<key>", "<username>")
						.replace("[key]", "[username]")));

			files.rename(commands, path + "/arguments/key", path + "/arguments/username");
		}
	}
}
