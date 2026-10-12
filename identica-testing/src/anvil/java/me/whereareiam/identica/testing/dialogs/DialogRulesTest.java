package me.whereareiam.identica.testing.dialogs;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.addon.dialogs.config.DialogDocument;
import me.whereareiam.identica.addon.dialogs.config.RulesDocument;
import me.whereareiam.identica.testing.environment.Addon;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * An administrator changes the dialogs addon's rules and dialogs on the running proxy and reloads Identica: the next
 * players get the dialogs the first matching rule names, a step whose rule names none stays in chat, and a changed
 * dialog shows its new text.
 */
@Tag("account")
class DialogRulesTest {
	private static final String PASSWORD = "Secret-123";
	private static final String RULES = "rules.yml";
	private static final String REGISTER = "dialogs/register.yml";
	private static final String EVENT_HOST = "event.identica.test";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, autoSelectSingleProvider = true, addons = Addon.DIALOGS)
	void keepsAStepInChatWhenItsRuleShowsNoDialog(ScenarioContext anvil) {
		ProxyConfiguration.editGenerated(anvil, Addon.DIALOGS, RULES, RulesDocument.class, rules ->
				first(rules, rule(Map.of("step", "password-registration"), "none")));
		Administrator.at(anvil, PROXY).reloads();

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.seesNoDialog()
				.command("pass " + PASSWORD)
				.seesDialog("register-confirm")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.on(LOBBY)
				.hasNoDialog();
	}

	/**
	 * A rule for one hostname comes before the shipped rule: Alice, who joins through that hostname, gets the event's
	 * registration dialog, and Bob, who joins through another one, gets the shipped dialog.
	 */
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, autoSelectSingleProvider = true, addons = Addon.DIALOGS)
	void showsTheDialogOfTheRuleForTheHostnameThePlayerJoinedThrough(ScenarioContext anvil) {
		DialogDocument event = ProxyConfiguration.generated(anvil, Addon.DIALOGS, REGISTER, DialogDocument.class);
		event.setTitle("<gold><bold>Spring Event");
		ProxyConfiguration.putGenerated(anvil, Addon.DIALOGS, "dialogs/event-register.yml", event);
		ProxyConfiguration.editGenerated(anvil, Addon.DIALOGS, RULES, RulesDocument.class, rules ->
				first(rules, rule(Map.of("step", "password-registration", "host", EVENT_HOST), "event-register")));
		Administrator.at(anvil, PROXY).reloads();

		Journey.of(anvil, Client.offline("Alice").through(EVENT_HOST)).join()
				.on(AUTH)
				.seesDialog("event-register")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.seesDialog("register-confirm");
		Journey.offline(anvil, "Bob").join()
				.on(AUTH)
				.seesDialog("register");
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, autoSelectSingleProvider = true, addons = Addon.DIALOGS)
	void showsAChangedDialogAfterAReload(ScenarioContext anvil) {
		ProxyConfiguration.editGenerated(anvil, Addon.DIALOGS, REGISTER, DialogDocument.class, dialog ->
				dialog.setBody(List.of("<white>Pick a password for this network.", "{message}")));
		Administrator.at(anvil, PROXY).reloads();

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.seesDialog("register");
	}

	/**
	 * Puts a rule before the shipped ones, so it is tried first.
	 */
	private static void first(RulesDocument rules, RulesDocument.Rule rule) {
		List<RulesDocument.Rule> changed = new ArrayList<>(rules.getRules());
		changed.addFirst(rule);
		rules.setRules(changed);
	}

	private static RulesDocument.Rule rule(Map<String, String> when, String dialog) {
		RulesDocument.Rule rule = new RulesDocument.Rule();
		rule.setWhen(when);
		rule.setDialog(dialog);

		return rule;
	}
}
