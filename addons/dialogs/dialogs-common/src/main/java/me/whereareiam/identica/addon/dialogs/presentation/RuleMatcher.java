package me.whereareiam.identica.addon.dialogs.presentation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.addon.dialogs.trigger.TriggerContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the rule that decides a moment: the first that names the moment's trigger and whose conditions all match
 * its attributes, ignoring case. A condition on an attribute the trigger does not report never matches.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RuleMatcher {
	static @NotNull Optional<DialogRule> first(@NotNull List<DialogRule> rules, @NotNull TriggerContext context) {
		return rules.stream()
				.filter(rule -> rule.getOn().equalsIgnoreCase(context.getTrigger()))
				.filter(rule -> matches(rule.getWhen(), context.getAttributes()))
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
