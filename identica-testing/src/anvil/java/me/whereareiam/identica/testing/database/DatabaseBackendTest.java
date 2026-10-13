package me.whereareiam.identica.testing.database;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.model.config.persistence.H2Persistence;
import me.whereareiam.identica.model.config.persistence.Persistence;
import me.whereareiam.identica.model.config.persistence.external.MysqlPersistence;
import me.whereareiam.identica.testing.environment.Database;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.type.DatabaseType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Identica starts on a database other than SQLite, creates its tables there, and a player registers and logs in
 * with Credential, the network's only provider. The MariaDB server runs in a container, so its test needs Docker.
 */
@Tag("operations")
class DatabaseBackendTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, database = Database.MARIADB)
	void registersAndLogsInOnMariaDb(ScenarioContext anvil) {
		registersAndLogsIn(anvil, MysqlPersistence.class, DatabaseType.MYSQL);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, database = Database.H2)
	void registersAndLogsInOnH2(ScenarioContext anvil) {
		registersAndLogsIn(anvil, H2Persistence.class, DatabaseType.H2);
	}

	/**
	 * Registers and logs a player in, then checks that the proxy kept the database it was given, reading
	 * {@code persistence.yml} as that backend's model.
	 */
	private static void registersAndLogsIn(ScenarioContext anvil, Class<? extends Persistence> model, DatabaseType type) {
		Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());

		assertEquals(type, ProxyConfiguration.generated(anvil, "persistence.yml", model).getType(),
				"The proxy must keep the database it was given");
	}
}
