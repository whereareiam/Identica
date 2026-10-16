package me.whereareiam.identica.testing.dialogs;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.addon.dialogs.config.RulesDocument;
import me.whereareiam.identica.testing.environment.Addon;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Administrator;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * Turns the dialogs addon's loading screen off, so players are asked on the step's server, as journeys about the
 * dialogs shown in the game need.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class InGameDialogs {
	static void use(ScenarioContext anvil) {
		ProxyConfiguration.editGenerated(anvil, Addon.DIALOGS, "rules.yml", RulesDocument.class, rules -> rules.getLoadingScreen().setEnabled(false));
		Administrator.at(anvil, PROXY).reloads();
	}
}
