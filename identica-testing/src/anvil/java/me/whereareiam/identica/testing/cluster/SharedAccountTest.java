package me.whereareiam.identica.testing.cluster;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.IdenticaCluster;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_A;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_B;

/**
 * An account belongs to the network, not to the proxy it was created through.
 */
class SharedAccountTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@IdenticaCluster
	void asksForThePasswordOfAnAccountRegisteredThroughTheOtherProxy(ScenarioContext anvil) {
		returnsThroughTheOtherProxy(anvil);
	}

	@Test
	@IdenticaCluster(replication = false)
	void asksForThePasswordWhenTheProxiesOnlyShareTheDatabase(ScenarioContext anvil) {
		returnsThroughTheOtherProxy(anvil);
	}

	private static void returnsThroughTheOtherProxy(ScenarioContext anvil) {
		Accounts.credential(anvil, "Alice", PASSWORD, PROXY_A);

		Journey.offline(anvil, "Alice", PROXY_B).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@IdenticaCluster
	void disconnectsAPlayerWhoseAccountIsDeletedOnTheOtherProxy(ScenarioContext anvil) {
		Journey alice = Accounts.credential(anvil, "Alice", PASSWORD, PROXY_B).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);

		Administrator.at(anvil, PROXY_A).deletes("Alice");

		alice.kickedWith(m -> m.identica().getCommands().getAdmin().getDelete().getDisconnect())
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());
	}
}
