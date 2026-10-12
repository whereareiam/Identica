package me.whereareiam.identica.addon.dialogs.config.defaults;

import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.addon.dialogs.config.RulesDocument;

import java.util.List;
import java.util.Map;

/**
 * The shipped rules: a dialog for every prompt of the official providers that asks for a password or a two-factor
 * code. A rule for a provider that is not installed never matches.
 */
public class RulesDefaults implements DefaultsProvider<RulesDocument> {
	@Override
	public RulesDocument supply(RulesDocument rules) {
		rules.setRules(List.of(
				prompt("credential:password", "login"),
				prompt("credential:new-password", "register"),
				prompt("credential:repeat-password", "register-confirm"),
				prompt("verification:code", "verification")
		));
		return rules;
	}

	private static RulesDocument.Rule prompt(String prompt, String dialog) {
		RulesDocument.Rule rule = new RulesDocument.Rule();
		rule.setWhen(Map.of("prompt", prompt));
		rule.setDialog(dialog);
		return rule;
	}
}
