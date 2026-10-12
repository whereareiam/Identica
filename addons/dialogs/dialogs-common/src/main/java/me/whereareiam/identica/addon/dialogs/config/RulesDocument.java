package me.whereareiam.identica.addon.dialogs.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The file {@code rules}: when to show which dialog, tried in order. Fields are not set until the file or the
 * defaults set them, because Configura fills in only fields that are not set.
 */
@Getter
@Setter
@ToString
public class RulesDocument {
	private @Nullable List<Rule> rules;

	/**
	 * Returns the rules as the addon uses them.
	 *
	 * @return the rules in order
	 */
	public @NotNull List<DialogRule> toRules() {
		return Objects.requireNonNullElse(rules, List.<Rule>of()).stream().map(Rule::toRule).toList();
	}

	/**
	 * One rule; {@code dialog: none} shows no dialog.
	 */
	@Getter
	@Setter
	@ToString
	public static class Rule {
		private @Nullable Map<String, String> when;
		private @Nullable String dialog;

		DialogRule toRule() {
			String shown = dialog == null || dialog.isBlank() || dialog.equalsIgnoreCase("none") ? null : dialog;
			return DialogRule.builder()
					.when(Objects.requireNonNullElse(when, Map.of()))
					.dialog(shown)
					.build();
		}
	}
}
