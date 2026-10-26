package me.whereareiam.identica.addon.dialogs.presentation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Picks the rule that decides: the first whose conditions all match the attributes of a message and its player.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RuleMatcher {
	/**
	 * Attribute of what the message asks for.
	 */
	static final String PROMPT = "prompt";
	/**
	 * Attribute of the message's ID.
	 */
	static final String MESSAGE = "message";
	/**
	 * Attribute of the player's pending scenario.
	 */
	static final String SCENARIO = "scenario";
	/**
	 * Attribute of the hostname the player connected through.
	 */
	static final String HOST = "host";

	static @NotNull Optional<DialogRule> first(@NotNull List<DialogRule> rules, @NotNull Map<String, String> attributes) {
		return rules.stream()
				.filter(rule -> matches(rule.getWhen(), attributes))
				.findFirst();
	}

	private static boolean matches(Map<String, String> conditions, Map<String, String> attributes) {
		for (Map.Entry<String, String> condition : conditions.entrySet()) {
			String attribute = attributes.get(condition.getKey());
			if (attribute == null || !attribute.equalsIgnoreCase(condition.getValue())) return false;
		}

		return true;
	}
}
