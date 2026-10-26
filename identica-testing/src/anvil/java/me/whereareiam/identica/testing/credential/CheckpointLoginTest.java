package me.whereareiam.identica.testing.credential;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Addon;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.HOLD_HOST;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * A returning player logs in while the example addon's checkpoint holds them before any server. Credential's
 * answer to a wrong password cannot reach the loading screen, so it waits until the player's client joined a
 * server's world and arrives there with the completion.
 */
@Tag("account")
class CheckpointLoginTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(providers = Provider.CREDENTIAL, addons = Addon.EXAMPLE)
	void logsInWhileHeldAndReadsTheAnswersOnceJoined(ScenarioContext anvil) {
		Accounts.credentialByAdmin(anvil, "Alice", PASSWORD);
		Journey alice = Journey.of(anvil, Client.offline("Alice").through(HOLD_HOST)).joinsHeld();
		Administrator console = Administrator.at(anvil, PROXY);

		console.answersFor("Alice", "login Wrong-456");
		alice.isHeld()
				.doesNotSee(m -> m.credential().getScenario().getAuthentication().getStatus().getInvalid());
		console.answersFor("Alice", "login " + PASSWORD);

		alice.on(LOBBY)
				.sees(m -> m.credential().getScenario().getAuthentication().getStatus().getInvalid())
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}
}
