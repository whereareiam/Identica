package me.whereareiam.identica.testing.environment;

import me.whereareiam.anvil.integration.junit.ScenarioResources;
import me.whereareiam.identica.adapter.database.testing.DatabaseContainers;
import me.whereareiam.identica.model.config.persistence.Persistence;
import me.whereareiam.identica.model.config.persistence.SqlitePersistence;
import me.whereareiam.identica.model.config.persistence.external.MysqlPersistence;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mariadb.MariaDBContainer;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Database a test proxy stores its accounts in. Each one writes the proxy's {@code persistence.yml}.
 */
public enum Database {
	/** The SQLite file in Identica's data directory, as a new installation has it. */
	SQLITE {
		@Override
		Persistence persistence(ScenarioResources resources) {
			return new SqlitePersistence();
		}
	},
	/** A MariaDB server in a container that stops with the network, through Identica's MySQL backend; needs Docker. */
	MARIADB {
		@Override
		Persistence persistence(ScenarioResources resources) {
			assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "A MariaDB database needs Docker");
			MariaDBContainer container = resources.own(DatabaseContainers.mariadb("mariadb:12"));
			container.start();

			MysqlPersistence persistence = new MysqlPersistence();
			persistence.setHost(container.getHost());
			persistence.setPort(container.getFirstMappedPort());
			persistence.setDatabase(container.getDatabaseName());
			persistence.setUsername(container.getUsername());
			persistence.setPassword(container.getPassword());
			return persistence;
		}
	};

	/**
	 * Provides the database, owning any server it starts in the network's resources, and returns the proxy's
	 * {@code persistence.yml} for it.
	 */
	abstract Persistence persistence(ScenarioResources resources);
}
