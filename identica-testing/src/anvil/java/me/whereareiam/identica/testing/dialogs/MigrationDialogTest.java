package me.whereareiam.identica.testing.dialogs;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Addon;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * A premium player moves their account to Credential and sets its password in the dialogs addon's registration
 * dialogs, which the shipped rules show for Credential's migration steps as well.
 */
@Tag("account")
class MigrationDialogTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(addons = Addon.DIALOGS)
	void setsThePasswordOfAMovedAccountInTheRegistrationDialogs(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		InGameDialogs.use(anvil);
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.command("credential")
				.sees(m -> m.credential().getCommands().getCredential().getConfirm())
				.command("credential confirm")
				.kickedWith(m -> m.credential().getCommands().getCredential().getConfirmed())
				.rejoin()
				.on(AUTH)
				.seesDialog("register")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.seesDialog("register-confirm")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getMigration().getBody())
				.hasNoDialog();
	}
}
