package me.whereareiam.identica.adapter.database.integration;

import com.zaxxer.hikari.HikariDataSource;
import me.whereareiam.identica.adapter.database.DefaultDatabaseService;
import me.whereareiam.identica.adapter.database.connection.DataSourceFactory;
import me.whereareiam.identica.adapter.database.connection.UnsupportedDatabaseServerException;
import me.whereareiam.identica.database.schema.AccountTable;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.logging.LoggingHelper;
import me.whereareiam.identica.model.config.persistence.external.MysqlPersistence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opens Identica on the oldest MySQL it supports and on the release before it.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("MySQL server requirement")
class MysqlServerRequirementIntegrationTest {
	// Identica reaches MySQL through the MariaDB driver, which signs in with the native password plugin.
	@Container
	static MySQLContainer oldest = new MySQLContainer("mysql:8.0.19")
			.withCommand("--default-authentication-plugin=mysql_native_password");

	@Container
	static MySQLContainer tooOld = new MySQLContainer("mysql:8.0.18")
			.withCommand("--default-authentication-plugin=mysql_native_password");

	@TempDir
	Path dataPath;

	@DisplayName("Opens MySQL 8.0.19 and installs the core schema")
	@Test
	void opensOldestSupportedMysql() throws SQLException {
		MysqlPersistence persistence = persistence(oldest);

		DefaultDatabaseService database = new DefaultDatabaseService(() -> persistence, dataPath);
		database.initialize();
		try {
			assertTrue(database.isInitialized());
		} finally {
			database.shutdown();
		}

		assertTrue(hasAccountTable(persistence));
	}

	@DisplayName("Refuses MySQL 8.0.18 before creating any table and logs why")
	@Test
	void refusesOlderMysql() throws SQLException {
		MysqlPersistence persistence = persistence(tooOld);
		List<String> severe = new ArrayList<>();
		Logger.init(severeLines(severe));

		DefaultDatabaseService database = new DefaultDatabaseService(() -> persistence, dataPath);
		UnsupportedDatabaseServerException refusal;
		try {
			refusal = assertThrows(UnsupportedDatabaseServerException.class, database::initialize);
		} finally {
			Logger.init(null);
		}

		assertTrue(refusal.getMessage().contains("MySQL 8.0.18"), refusal.getMessage());
		assertTrue(refusal.getMessage().contains("MySQL 8.0.19 or newer"), refusal.getMessage());
		assertEquals(List.of(refusal.getMessage()), severe);
		assertFalse(hasAccountTable(persistence));
	}

	private static LoggingHelper severeLines(List<String> lines) {
		return new LoggingHelper() {
			@Override
			public void info(String message, Object... objects) {
			}

			@Override
			public void warn(String message, Object... objects) {
			}

			@Override
			public void severe(String message, Object... objects) {
				lines.add(message.formatted(objects));
			}

			@Override
			public void debug(String message, Object... objects) {
			}

			@Override
			public void trace(String message, Object... objects) {
			}
		};
	}

	private static MysqlPersistence persistence(MySQLContainer container) {
		MysqlPersistence persistence = new MysqlPersistence();
		persistence.setHost(container.getHost());
		persistence.setPort(container.getMappedPort(3306));
		persistence.setDatabase(container.getDatabaseName());
		persistence.setUsername(container.getUsername());
		persistence.setPassword(container.getPassword());
		return persistence;
	}

	private boolean hasAccountTable(MysqlPersistence persistence) throws SQLException {
		DataSource dataSource = DataSourceFactory.create(persistence, dataPath);
		try (Connection connection = dataSource.getConnection();
			 ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, AccountTable.NAME, new String[]{"TABLE"})) {
			return tables.next();
		} finally {
			((HikariDataSource) dataSource).close();
		}
	}
}
