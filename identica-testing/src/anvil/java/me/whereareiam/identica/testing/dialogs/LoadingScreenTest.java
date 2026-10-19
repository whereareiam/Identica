package me.whereareiam.identica.testing.dialogs;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.addon.dialogs.config.RulesDocument;
import me.whereareiam.identica.testing.environment.Addon;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * The dialogs addon asks a player on the loading screen on Velocity, before they enter any server: Identica holds
 * them on the proxy until they are in, and then sends them straight to where their journey completes.
 */
@Tag("account")
class LoadingScreenTest {
	private static final String PASSWORD = "Secret-123";
	/**
	 * Longer than Velocity's read timeout of 30 seconds, which closes a connection whose client sends nothing.
	 */
	private static final Duration PAST_THE_READ_TIMEOUT = Duration.ofSeconds(35);
	private static final Duration REGISTRATION_TTL = Duration.ofSeconds(10);

	/**
	 * Alice registers and later logs in on the loading screen, stays connected there while she takes her time, and
	 * never sees the authentication server or Identica's chat prompts.
	 */
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, autoSelectSingleProvider = true, addons = Addon.DIALOGS)
	void registersAndLogsInOnTheLoadingScreen(ScenarioContext anvil) {
		Journey alice = Journey.offline(anvil, "Alice").joinsHeld()
				.seesDialog("register")
				.onLoadingScreen()
				.staysHeld(PAST_THE_READ_TIMEOUT)
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.seesDialog("register-confirm")
				.onLoadingScreen()
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.on(LOBBY)
				.doesNotSee(m -> m.credential().getScenario().getRegistration().getPrompt())
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.hasNoDialog()
				.neverJoins(AUTH);

		alice.leave()
				.attemptAgain()
				.seesDialog("login")
				.onLoadingScreen()
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", "wrong-password"))
				.seesDialog("login")
				.onLoadingScreen()
				.dialogShowsAnswer("credential:password-invalid")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.on(LOBBY)
				.doesNotSee(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.hasNoDialog()
				.neverJoins(AUTH);
	}

	/**
	 * Alice repeats her password wrongly on the loading screen: the registration dialog comes back there with its
	 * text for differing passwords, and she registers with a new password and its repetition.
	 */
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, autoSelectSingleProvider = true, addons = Addon.DIALOGS)
	void registersAfterRepeatingThePasswordWronglyOnTheLoadingScreen(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").joinsHeld()
				.seesDialog("register")
				.onLoadingScreen()
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.seesDialog("register-confirm")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", "Other-456"))
				.seesDialog("register")
				.onLoadingScreen()
				.dialogShowsAnswer("credential:passwords-differ")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.seesDialog("register-confirm")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.neverJoins(AUTH);
	}

	/**
	 * Alice does not answer: once her registration expires, the proxy disconnects her from the loading screen.
	 */
	@Test
	@Identica(
			mode = JourneyMode.INTERACTIVE,
			providers = Provider.CREDENTIAL,
			autoSelectSingleProvider = true,
			addons = Addon.DIALOGS,
			configure = ShortRegistration.class
	)
	void disconnectsAPlayerWhoseRegistrationExpires(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").joinsHeld()
				.seesDialog("register")
				.onLoadingScreen()
				.kickedWith(m -> m.identica().getScenarios().getRegistration().getPipelineExpired())
				.neverJoins(AUTH)
				.neverJoins(LOBBY);
	}

	/**
	 * A rule shows no dialog for registration: the proxy lets Alice go to the authentication server, where she is
	 * asked in chat, and the next step's dialog is shown there.
	 */
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, autoSelectSingleProvider = true, addons = Addon.DIALOGS)
	void sendsAPlayerToTheAuthenticationServerForAStepWithoutADialog(ScenarioContext anvil) {
		ProxyConfiguration.editGenerated(anvil, Addon.DIALOGS, "rules.yml", RulesDocument.class, rules -> {
			List<RulesDocument.Rule> changed = new ArrayList<>(rules.getRules());
			RulesDocument.Rule chat = new RulesDocument.Rule();
			chat.setWhen(Map.of("step", "password-registration"));
			chat.setDialog("none");
			changed.addFirst(chat);
			rules.setRules(changed);
		});
		Administrator.at(anvil, PROXY).reloads();

		Journey.offline(anvil, "Alice").attempt()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.command("pass " + PASSWORD)
				.seesDialog("register-confirm")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.on(LOBBY)
				.hasNoDialog();
	}

	/**
	 * Alice switches to chat on the loading screen, which shows none: she goes on to the authentication server and
	 * answers there.
	 */
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, autoSelectSingleProvider = true, addons = Addon.DIALOGS)
	void sendsAPlayerWhoSwitchesToChatToTheAuthenticationServer(ScenarioContext anvil) {
		ChatButton.addTo(anvil, "register");

		Journey.offline(anvil, "Alice").joinsHeld()
				.seesDialog("register")
				.onLoadingScreen()
				.presses(dialog -> dialog.getExitAction())
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY);
	}

	/**
	 * Alice presses Leave on the loading screen: the proxy disconnects her with the button's text.
	 */
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, autoSelectSingleProvider = true, addons = Addon.DIALOGS)
	void leavesTheLoadingScreenThroughTheLeaveButton(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").joinsHeld()
				.seesDialog("register")
				.onLoadingScreen()
				.presses(dialog -> dialog.getExitAction())
				.disconnectedBy(dialog -> dialog.getExitAction())
				.neverJoins(AUTH)
				.neverJoins(LOBBY);
	}

	private static final class ShortRegistration implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.engine().getScenarios().getRegistration().setPipelineTtl(REGISTRATION_TTL);
		}
	}
}
