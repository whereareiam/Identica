package me.whereareiam.identica.testing.dialogs;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Addon;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.fixture.Authenticator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * A player who protects their account with an authenticator app types its code into the dialogs addon's
 * {@code verification} dialog, after Credential's password or straight after joining with a premium account.
 */
@Tag("account")
class VerificationDialogTest {
	private static final String PASSWORD = "Secret-123";

	/**
	 * A wrong code shows the verification dialog again with Identica's answer, and the right one lets Alice in.
	 */
	@Test
	@Identica(
			mode = JourneyMode.INTERACTIVE,
			providers = Provider.CREDENTIAL,
			addons = Addon.DIALOGS,
			configure = Authenticator.ShortPeriod.class
	)
	void showsAWrongCodeInTheVerificationDialog(ScenarioContext anvil) {
		Journey alice = Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.login(PASSWORD)
				.on(LOBBY);
		Authenticator app = Authenticator.enroll(alice).app();

		alice.leave()
				.rejoin()
				.on(AUTH)
				.seesDialog("login")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("password", PASSWORD))
				.seesDialog("verification")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("code", app.wrongCode()))
				.seesDialog("verification")
				.dialogShowsAnswer("verification:code-invalid")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("code", app.nextCode()))
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody())
				.hasNoDialog();
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, addons = Addon.DIALOGS, configure = Authenticator.ShortPeriod.class)
	void asksAPremiumPlayerForTheCodeInTheVerificationDialog(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");
		Journey steve = Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody());
		Authenticator app = Authenticator.enroll(steve).app();

		steve.leave()
				.rejoin()
				.on(AUTH)
				.seesDialog("verification")
				.presses(dialog -> dialog.getActions().getFirst(), Map.of("code", app.nextCode()))
				.on(LOBBY)
				.hasNoDialog();
	}
}
