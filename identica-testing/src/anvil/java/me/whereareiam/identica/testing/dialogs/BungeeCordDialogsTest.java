package me.whereareiam.identica.testing.dialogs;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Addon;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Platform;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * A player answers Credential's prompts through the dialogs addon on a BungeeCord proxy, which shows dialogs through
 * its own API instead of PacketEvents.
 */
@Tag("account")
class BungeeCordDialogsTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(
			platform = Platform.BUNGEECORD,
			mode = JourneyMode.INTERACTIVE,
			providers = Provider.CREDENTIAL,
			autoSelectSingleProvider = true,
			addons = Addon.DIALOGS
	)
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
				.dialogShowsAnswer("credential:password-invalid")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.on(LOBBY)
				.hasNoDialog();
	}

	/**
	 * The button that switches to chat is a custom click the BungeeCord side reads itself.
	 */
	@Test
	@Identica(
			platform = Platform.BUNGEECORD,
			mode = JourneyMode.INTERACTIVE,
			providers = Provider.CREDENTIAL,
			autoSelectSingleProvider = true,
			addons = Addon.DIALOGS
	)
	void answersInChatAfterSwitchingToChat(ScenarioContext anvil) {
		InGameDialogs.use(anvil);
		ChatButton.addTo(anvil, "register");

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.seesDialog("register")
				.presses(dialog -> dialog.getExitAction())
				.hasNoDialog()
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	/**
	 * The shipped dialogs' Leave button disconnects Alice with its reason.
	 */
	@Test
	@Identica(
			platform = Platform.BUNGEECORD,
			mode = JourneyMode.INTERACTIVE,
			providers = Provider.CREDENTIAL,
			autoSelectSingleProvider = true,
			addons = Addon.DIALOGS
	)
	void leavesThroughTheLeaveButton(ScenarioContext anvil) {
		InGameDialogs.use(anvil);
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.seesDialog("register")
				.presses(dialog -> dialog.getExitAction())
				.disconnectedBy(dialog -> dialog.getExitAction());
	}
}
