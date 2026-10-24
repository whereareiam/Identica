package me.whereareiam.identica.common.config.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import me.whereareiam.strata.MigrationAction;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Replaces each scenario's {@code journeyMode} and {@code journeyPolicy} in the engine document with {@code choice},
 * which only decides who picks among the options of a scenario. A registration that was {@code INTERACTIVE} let the
 * player choose the provider, so it becomes {@code PROMPTED}; every other scenario becomes {@code AUTOMATIC}, as nothing
 * asked the player to choose there. {@code autoSelectSingleProvider} is dropped: a choice with one option is never
 * asked. The {@code engine.journey.step.interactionRequired} message is dropped, as no journey refuses a player for
 * needing input any more, and the provider choice entries that are still the defaults name options instead of
 * providers.
 */
@RequiredArgsConstructor
final class JourneyChoiceMigration implements MigrationAction<ConfigContext> {
	static final int VERSION = 10;
	static final String NAME = "journey-choice";

	private static final String ENGINE = "engine";
	private static final String MESSAGES = "messages";
	private static final List<String> SCENARIOS = List.of("authentication", "registration", "migration");
	private static final String ENTRY_FORMAT = "/engine/journey/step/enrollment/entryFormat";
	private static final Map<String, Map.Entry<String, String>> ENTRY_DEFAULTS = Map.of(
			"format", Map.entry(
					"   <dark_gray><click:run_command:/identica enroll {providerId}>▪ <gray>[{providerName}]:</gray> <white>{description}</click>",
					"   <dark_gray><click:run_command:/identica enroll {optionId}>▪ <gray>[{optionName}]:</gray> <white>{description}</click>"
			),
			"emptyFormat", Map.entry(
					"   <dark_gray><click:run_command:/identica enroll {providerId}>▪ <gray>[{providerName}]:</gray></click>",
					"   <dark_gray><click:run_command:/identica enroll {optionId}>▪ <gray>[{optionName}]:</gray></click>"
			)
	);

	/** Extension of the documents, with its dot. */
	private final @NotNull String extension;

	@Override
	public void apply(@NotNull ConfigContext files) {
		migrateEngine(files);
		migrateMessages(files);
	}

	private void migrateEngine(@NotNull ConfigContext files) {
		String engine = ENGINE + extension;
		if (!files.exists(engine)) return;

		JsonNode document = files.document(engine);
		for (String name : SCENARIOS) {
			if (!(document.at("/scenarios/" + name) instanceof ObjectNode scenario)) continue;

			JsonNode mode = scenario.remove("journeyMode");
			scenario.remove("journeyPolicy");
			scenario.remove("autoSelectSingleProvider");
			if (!scenario.has("choice"))
				scenario.put("choice", asks(name, mode) ? "PROMPTED" : "AUTOMATIC");
		}
	}

	private static boolean asks(@NotNull String scenario, JsonNode mode) {
		return scenario.equals("registration")
				&& mode != null
				&& mode.isTextual()
				&& mode.asText().trim().equalsIgnoreCase("INTERACTIVE");
	}

	private void migrateMessages(@NotNull ConfigContext files) {
		String messages = MESSAGES + extension;
		if (!files.exists(messages)) return;

		files.remove(messages, "/engine/journey/step/interactionRequired");
		for (Map.Entry<String, Map.Entry<String, String>> entry : ENTRY_DEFAULTS.entrySet()) {
			String pointer = ENTRY_FORMAT + "/" + entry.getKey();
			JsonNode current = files.document(messages).at(pointer);
			if (current.isTextual() && current.asText().equals(entry.getValue().getKey()))
				files.put(messages, pointer, TextNode.valueOf(entry.getValue().getValue()));
		}
	}
}
