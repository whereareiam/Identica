package me.whereareiam.identica.testing.migration;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * An administrator moves a Credential player's account to Premium and takes the move back before the player
 * returns.
 */
class AdministeredMigrationTest {
	private static final String PASSWORD = "Secret-123";

	/**
	 * Starting the move disconnects Alice. A second start finds the move already started, and once it is cancelled
	 * there is nothing left to cancel. Alice is told on her next join and logs in with her password as before.
	 */
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE)
	void tellsAPlayerWhoseMoveWasCancelledOnTheNextJoin(ScenarioContext anvil) {
		Journey alice = Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);

		Administrator administrator = Administrator.at(anvil, PROXY).startsMigrationOf("Alice", Provider.PREMIUM);
		alice.disconnected();
		administrator.findsMigrationAlreadyStartedOf("Alice", Provider.PREMIUM)
				.cancelsMigrationOf("Alice")
				.findsNoMigrationToCancelOf("Alice");

		alice.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.sees(m -> m.identica().getScenarios().getMigration().getCancelled())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}
}
