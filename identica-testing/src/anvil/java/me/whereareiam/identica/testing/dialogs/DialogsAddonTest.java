package me.whereareiam.identica.testing.dialogs;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Addon;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * A player answers Credential's prompts through the dialogs addon's shipped dialogs instead of typing commands.
 */
@Tag("account")
class DialogsAddonTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, autoSelectSingleProvider = true, addons = Addon.DIALOGS)
	void registersAndLogsInThroughDialogs(ScenarioContext anvil) {
		InGameDialogs.use(anvil);
		Journey alice = Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.seesDialog("register")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.seesDialog("register-confirm")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.hasNoDialog();

		alice.rejoin()
				.on(AUTH)
				.seesDialog("login")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", "wrong-password"))
				.seesDialog("login")
				.dialogShows(m -> m.credential().getScenario().getAuthentication().getStatus().getInvalid())
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.on(LOBBY)
				.hasNoDialog();
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, autoSelectSingleProvider = true, addons = Addon.DIALOGS)
	void answersInChatAfterSwitchingToChat(ScenarioContext anvil) {
		InGameDialogs.use(anvil);
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.seesDialog("register")
				.presses(dialog -> dialog.getExitAction())
				.hasNoDialog()
				.command("pass " + PASSWORD)
				.sees(m -> m.credential().getScenario().getRegistration().getConfirmPrompt())
				.seesNoDialog()
				.command("passconfirm " + PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	/**
	 * Choosing chat lasts until Alice reconnects: when she comes back, Identica's prompts are dialogs again.
	 */
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, autoSelectSingleProvider = true, addons = Addon.DIALOGS)
	void showsDialogsAgainAfterReconnectingFromChat(ScenarioContext anvil) {
		InGameDialogs.use(anvil);
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.seesDialog("register")
				.presses(dialog -> dialog.getExitAction())
				.hasNoDialog()
				.register(PASSWORD)
				.on(LOBBY)
				.leave()
				.rejoin()
				.on(AUTH)
				.seesDialog("login")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.on(LOBBY)
				.hasNoDialog();
	}

	/**
	 * Alice types her answers in chat while the dialogs are open: each step takes them, the next step's dialog
	 * replaces the previous one, and none is left once she is in.
	 */
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, autoSelectSingleProvider = true, addons = Addon.DIALOGS)
	void followsAnswersTypedInChatWhileADialogIsOpen(ScenarioContext anvil) {
		InGameDialogs.use(anvil);
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.seesDialog("register")
				.command("pass " + PASSWORD)
				.seesDialog("register-confirm")
				.command("passconfirm " + PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.hasNoDialog();
	}
}
