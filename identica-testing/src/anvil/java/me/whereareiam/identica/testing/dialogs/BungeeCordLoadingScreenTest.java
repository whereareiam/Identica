package me.whereareiam.identica.testing.dialogs;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Addon;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Platform;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * The dialogs addon asks a player on the loading screen on BungeeCord: Identica holds them in the configuration of
 * the first server BungeeCord connects them to, BungeeCord's own dialog API shows them the dialogs there, and the
 * clicks of their buttons reach the addon.
 */
@Tag("account")
class BungeeCordLoadingScreenTest {
	private static final String PASSWORD = "Secret-123";

	/**
	 * Alice registers and later logs in on the loading screen and never sees the authentication server.
	 */
	@Test
	@Identica(
			platform = Platform.BUNGEECORD,
			providers = Provider.CREDENTIAL,
			addons = Addon.DIALOGS
	)
	void registersAndLogsInOnTheLoadingScreen(ScenarioContext anvil) {
		Journey alice = Journey.offline(anvil, "Alice").joinsHeld()
				.seesDialog("register")
				.onLoadingScreen()
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.seesDialog("register-confirm")
				.onLoadingScreen()
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.hasNoDialog()
				.neverJoins(AUTH);

		alice.leave()
				.attemptAgain()
				.seesDialog("login")
				.onLoadingScreen()
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", "wrong-password"))
				.seesDialog("login")
				.dialogShowsAnswer("credential:password-invalid")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.on(LOBBY)
				.hasNoDialog()
				.neverJoins(AUTH);
	}

	/**
	 * Alice presses Leave on the loading screen: the proxy disconnects her with the button's text.
	 */
	@Test
	@Identica(
			platform = Platform.BUNGEECORD,
			providers = Provider.CREDENTIAL,
			addons = Addon.DIALOGS
	)
	void leavesTheLoadingScreenThroughTheLeaveButton(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").joinsHeld()
				.seesDialog("register")
				.onLoadingScreen()
				.presses(dialog -> dialog.getExitAction())
				.disconnectedBy(dialog -> dialog.getExitAction());
	}
}
