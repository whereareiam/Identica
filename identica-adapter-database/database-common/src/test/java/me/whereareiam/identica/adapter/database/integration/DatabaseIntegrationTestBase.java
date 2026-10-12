package me.whereareiam.identica.adapter.database.integration;

import me.whereareiam.identica.adapter.database.testing.DatabaseContainers;
import me.whereareiam.identica.adapter.database.testing.DatabaseFixture;
import me.whereareiam.identica.adapter.database.testing.H2DatabaseFixture;
import me.whereareiam.identica.adapter.database.testing.MariaDbDatabaseFixture;
import me.whereareiam.identica.adapter.database.testing.PostgresDatabaseFixture;
import me.whereareiam.identica.adapter.database.testing.SqliteDatabaseFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.stream.Stream;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class DatabaseIntegrationTestBase {
	@Container
static PostgreSQLContainer postgresContainer = DatabaseContainers.postgres("postgres:18.1")
			.withDatabaseName("identica")
			.withUsername("identica")
			.withPassword("identica");

	@Container
static MariaDBContainer mariaDbContainer = DatabaseContainers.mariadb("mariadb:12")
			.withDatabaseName("identica")
			.withUsername("identica")
			.withPassword("identica");

	private List<DatabaseFixture> fixtures;

	@BeforeAll
	void setUpFixtures() {
		fixtures = List.of(
				new PostgresDatabaseFixture(postgresContainer),
				new MariaDbDatabaseFixture(mariaDbContainer),
				new H2DatabaseFixture(),
				new SqliteDatabaseFixture()
		);
	}

	@AfterAll
	void shutdownFixtures() {
		if (fixtures == null) return;
		fixtures.forEach(DatabaseFixture::shutdown);
	}

	protected Stream<DatabaseFixture> fixtures() {
		return fixtures.stream();
	}
}
