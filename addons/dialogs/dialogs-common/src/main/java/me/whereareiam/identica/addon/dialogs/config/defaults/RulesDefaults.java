package me.whereareiam.identica.addon.dialogs.config.defaults;

import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.addon.dialogs.config.RulesDocument;

import java.util.List;
import java.util.Map;

/**
 * The shipped rules: a dialog for every step of the official providers that waits for a password or a two-factor
 * code. A rule for a provider that is not installed never matches.
 */
public class RulesDefaults implements DefaultsProvider<RulesDocument> {
	@Override
	public RulesDocument supply(RulesDocument rules) {
		RulesDocument.LoadingScreen loadingScreen = new RulesDocument.LoadingScreen();
		loadingScreen.setEnabled(true);
		loadingScreen.setWhen(Map.of());
		rules.setLoadingScreen(loadingScreen);
		rules.setRules(List.of(
				step("password-authentication-password", "login"),
				step("password-credential-presence", "login"),
				step("password-migration-authentication", "login"),
				step("password-registration", "register"),
				step("password-migration-registration", "register"),
				step("password-registration-confirm", "register-confirm"),
				step("password-migration-confirm", "register-confirm"),
				step("password-authentication-verification", "verification"),
				step("premium-authentication-verification", "verification")
		));
		return rules;
	}

	private static RulesDocument.Rule step(String step, String dialog) {
		RulesDocument.Rule rule = new RulesDocument.Rule();
		rule.setWhen(Map.of("step", step));
		rule.setDialog(dialog);
		return rule;
	}
}
