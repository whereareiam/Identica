package me.whereareiam.identica.adapter.database.connection;

import me.whereareiam.identica.type.DatabaseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Database server requirement")
class DatabaseServerRequirementTest {
	@DisplayName("Accepts MySQL 8.0.19 and newer")
	@ParameterizedTest
	@ValueSource(strings = {"8.0.19", "8.0.36", "8.0.40-log", "8.4.3", "9.1.0", "8.0.36-28", "10.0"})
	void acceptsSupportedMysql(String version) {
		assertDoesNotThrow(() -> DatabaseServerRequirement.verify(DatabaseType.MYSQL, "MySQL", version));
	}

	@DisplayName("Refuses MySQL older than 8.0.19, naming the found and the required version")
	@ParameterizedTest
	@ValueSource(strings = {"8.0.18", "8.0.12", "5.7.44", "5.7.44-log", "5.6.51", "8.0"})
	void refusesOlderMysql(String version) {
		UnsupportedDatabaseServerException refusal = assertThrows(UnsupportedDatabaseServerException.class,
				() -> DatabaseServerRequirement.verify(DatabaseType.MYSQL, "MySQL", version));

		assertTrue(refusal.getMessage().contains("MySQL " + version), refusal.getMessage());
		assertTrue(refusal.getMessage().contains("8.0.19 or newer"), refusal.getMessage());
	}

	@DisplayName("Refuses MySQL whose version cannot be read")
	@Test
	void refusesUnreadableMysqlVersion() {
		UnsupportedDatabaseServerException refusal = assertThrows(UnsupportedDatabaseServerException.class,
				() -> DatabaseServerRequirement.verify(DatabaseType.MYSQL, "MySQL", "unknown"));

		assertTrue(refusal.getMessage().contains("8.0.19 or newer"), refusal.getMessage());
	}

	@DisplayName("Has no minimum for MariaDB, whose versions are its own")
	@ParameterizedTest
	@ValueSource(strings = {"5.5.68-MariaDB", "10.3.39-MariaDB", "12.0.2-MariaDB-ubu2404"})
	void acceptsMariadb(String version) {
		assertDoesNotThrow(() -> DatabaseServerRequirement.verify(DatabaseType.MYSQL, "MariaDB", version));
	}

	@DisplayName("Leaves the other database types alone")
	@Test
	void ignoresOtherTypes() {
		assertDoesNotThrow(() -> DatabaseServerRequirement.verify(DatabaseType.POSTGRES, "PostgreSQL", "9.6.24"));
		assertDoesNotThrow(() -> DatabaseServerRequirement.verify(DatabaseType.H2, "H2", "2.3.232 (2024-08-11)"));
		assertDoesNotThrow(() -> DatabaseServerRequirement.verify(DatabaseType.SQLITE, "SQLite", "3.46.1"));
	}
}
