package me.whereareiam.identica.addon.dialogs.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.addon.dialogs.model.LoadingScreenRule;
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
	private @Nullable LoadingScreen loadingScreen;

	/**
	 * Returns the rules as the addon uses them.
	 *
	 * @return the rules in order
	 */
	public @NotNull List<DialogRule> toRules() {
		return Objects.requireNonNullElse(rules, List.<Rule>of()).stream().map(Rule::toRule).toList();
	}

	/**
	 * Returns whether and for which connections players are asked on the loading screen.
	 *
	 * @return the loading-screen rule; without one in the file, the loading screen is not used
	 */
	public @NotNull LoadingScreenRule toLoadingScreen() {
		if (loadingScreen == null) return LoadingScreenRule.builder().enabled(false).build();

		return LoadingScreenRule.builder()
				.enabled(Boolean.TRUE.equals(loadingScreen.enabled))
				.when(Objects.requireNonNullElse(loadingScreen.when, Map.of()))
				.build();
	}

	/**
	 * Asking players on the loading screen, before they enter any server's world. A player whose client has no
	 * dialogs, or whose step has no dialog, answers on the step's server.
	 */
	@Getter
	@Setter
	@ToString
	public static class LoadingScreen {
		private @Nullable Boolean enabled;
		private @Nullable Map<String, String> when;
	}

	/**
	 * One rule. A rule without {@code on} reacts to waiting Identica steps; {@code dialog: none} shows no dialog.
	 */
	@Getter
	@Setter
	@ToString
	public static class Rule {
		private @Nullable String on;
		private @Nullable Map<String, String> when;
		private @Nullable String dialog;

		DialogRule toRule() {
			String shown = dialog == null || dialog.isBlank() || dialog.equalsIgnoreCase("none") ? null : dialog;
			return DialogRule.builder()
					.on(Objects.requireNonNullElse(on, "step-waiting"))
					.when(Objects.requireNonNullElse(when, Map.of()))
					.dialog(shown)
					.build();
		}
	}
}
