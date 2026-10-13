package me.whereareiam.identica.adapter.database.connection;

import me.whereareiam.identica.model.config.persistence.H2Persistence;
import me.whereareiam.identica.model.config.persistence.SqlitePersistence;
import me.whereareiam.identica.model.config.persistence.external.MysqlPersistence;
import me.whereareiam.identica.model.config.persistence.external.PostgresPersistence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("JDBC URL Factory")
class JdbcUrlFactoryTest {
	@DisplayName("Uses identica.db when the SQLite file name is blank")
	@Test
	void sqliteDefaultsToIdenticaDbWhenBlank() {
		Path dataPath = Path.of("build", "test-data");
		SqlitePersistence persistence = new SqlitePersistence();
		persistence.setFile(" ");

		String url = JdbcUrlFactory.create(persistence, dataPath);

		assertEquals("jdbc:sqlite:" + dataPath.resolve("identica.db"), url);
	}

	@DisplayName("Uses the configured SQLite file name")
	@Test
	void sqliteUsesCustomFileName() {
		Path dataPath = Path.of("build", "test-data");
		SqlitePersistence persistence = new SqlitePersistence();
		persistence.setFile("custom.db");

		String url = JdbcUrlFactory.create(persistence, dataPath);

		assertEquals("jdbc:sqlite:" + dataPath.resolve("custom.db"), url);
	}

	@DisplayName("Builds an in-memory H2 JDBC URL when a mem target is provided")
	@Test
	void h2UsesMemTargetWhenProvided() {
		Path dataPath = Path.of("build", "test-data");
		H2Persistence persistence = new H2Persistence();
		persistence.setFile("mem:identica_test");
		persistence.setOptions("");

		String url = JdbcUrlFactory.create(persistence, dataPath);

		assertEquals("jdbc:h2:mem:identica_test", url);
	}

	@DisplayName("Builds a file-based H2 JDBC URL and appends options")
	@Test
	void h2UsesFileTargetWithOptions() {
		Path dataPath = Path.of("build", "test-data");
		H2Persistence persistence = new H2Persistence();
		persistence.setFile("identica");
		persistence.setOptions("MODE=PostgreSQL");

		String url = JdbcUrlFactory.create(persistence, dataPath);

		assertEquals("jdbc:h2:file:" + dataPath.resolve("identica").toAbsolutePath() + ";MODE=PostgreSQL", url);
	}

	@DisplayName("Gives H2 an absolute path for a file in a relative data directory")
	@Test
	void h2ResolvesTheFileToAnAbsolutePath() {
		Path dataPath = Path.of("plugins", "identica");
		H2Persistence persistence = new H2Persistence();

		String url = JdbcUrlFactory.create(persistence, dataPath);

		assertEquals("jdbc:h2:file:" + dataPath.toAbsolutePath().resolve("identica"), url);
	}

	@DisplayName("Keeps an absolute H2 file as configured")
	@Test
	void h2KeepsAnAbsoluteFile() {
		Path file = Path.of("srv", "identica").toAbsolutePath();
		H2Persistence persistence = new H2Persistence();
		persistence.setFile(file.toString());

		String url = JdbcUrlFactory.create(persistence, Path.of("plugins", "identica"));

		assertEquals("jdbc:h2:file:" + file, url);
	}

	@DisplayName("Formats the external PostgreSQL JDBC URL")
	@Test
	void postgresExternalUrlIsFormatted() {
		Path dataPath = Path.of("build", "test-data");
		PostgresPersistence persistence = new PostgresPersistence();
		persistence.setHost("db.local");
		persistence.setPort(5433);
		persistence.setDatabase("identica");

		String url = JdbcUrlFactory.create(persistence, dataPath);

		assertEquals("jdbc:postgresql://db.local:5433/identica", url);
	}

	@DisplayName("Formats the external MariaDB JDBC URL")
	@Test
	void mysqlExternalUrlIsFormatted() {
		Path dataPath = Path.of("build", "test-data");
		MysqlPersistence persistence = new MysqlPersistence();
		persistence.setHost("db.local");
		persistence.setPort(3307);
		persistence.setDatabase("identica");

		String url = JdbcUrlFactory.create(persistence, dataPath);

		assertEquals("jdbc:mariadb://db.local:3307/identica", url);
	}
}
