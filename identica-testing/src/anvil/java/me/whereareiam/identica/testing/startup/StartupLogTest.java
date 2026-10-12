package me.whereareiam.identica.testing.startup;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.IdenticaNetwork;
import me.whereareiam.identica.testing.journey.Administrator;
import org.junit.jupiter.api.Test;

/**
 * What Identica does while it starts reaches the proxy console.
 */
class StartupLogTest {
	@Test
	@Identica
	void logsInstallingTheAccountSchema(ScenarioContext anvil) {
		Administrator.at(anvil, IdenticaNetwork.PROXY)
				.seesOnConsole("Database schema identica/account: applied");
	}
}
