package me.whereareiam.identica.testing.database;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.DATA;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An administrator deletes an account on SQLite, the database a new installation has. Every row that belongs to the
 * account goes with it, including the Credential password history and the provider profile, as on the other
 * databases.
 */
@Tag("operations")
class AccountDeletionTest {
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void leavesNoRowOfADeletedAccountBehind(ScenarioContext anvil) throws SQLException {
		Accounts.credentialByAdmin(anvil, "Alice", "Secret-123");
		Administrator.at(anvil, PROXY)
				.setsPassword("Alice", "Changed-456")
				.deletes("Alice");

		Path database = anvil.processes().proxy(PROXY).workDirectory().resolve(DATA).resolve("identica.db");
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
			assertEquals(List.of(), foreignKeyViolations(connection));
			assertEquals(0, count(connection, "identica_provider_profiles"));
			assertEquals(0, count(connection, "identica_provider_credential_accounts_history"));
		}
	}

	private static List<String> foreignKeyViolations(Connection connection) throws SQLException {
		List<String> violations = new ArrayList<>();
		try (ResultSet rows = connection.createStatement().executeQuery("PRAGMA foreign_key_check")) {
			while (rows.next()) violations.add(rows.getString(1) + " -> " + rows.getString(3));
		}

		return violations;
	}

	private static int count(Connection connection, String table) throws SQLException {
		try (ResultSet rows = connection.createStatement().executeQuery("SELECT COUNT(*) FROM " + table)) {
			rows.next();
			return rows.getInt(1);
		}
	}
}
