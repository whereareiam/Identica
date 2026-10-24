package me.whereareiam.identica.testing.database;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Platform;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.fixture.Authenticator;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An administrator deletes an account on SQLite, the database a new installation has. Every row that belongs to the
 * account goes with it through the foreign keys, as on the other databases: its username, provider link and profile,
 * the Credential account with its password history, and its two-step verification.
 */
@Tag("operations")
class AccountDeletionTest {
	private static final String PASSWORD = "Secret-123";
	private static final List<String> ACCOUNT_TABLES = List.of(
			"identica_accounts",
			"identica_account_usernames",
			"identica_account_username_history",
			"identica_provider_links",
			"identica_provider_profiles",
			"identica_provider_credential_accounts",
			"identica_provider_credential_accounts_history",
			"identica_verification_enrollments",
			"identica_verification_recovery_codes",
			"identica_verification_selections"
	);

	@Test
	@Identica(providers = Provider.CREDENTIAL, configure = Authenticator.ShortPeriod.class)
	void leavesNoRowOfADeletedAccountBehind(ScenarioContext anvil) throws SQLException {
		Journey alice = Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);
		Authenticator app = Authenticator.of(alice.command("2fa enroll totp")
				.sees(m -> m.verification().getMethods().getTotp().getPending())
				.reads("secret"));
		alice.command("2fa enroll confirm " + app.code())
				.sees(m -> m.verification().getCommands().getConfirm().getRecoveryCodes().getBody());
		alice.command("2fa enroll confirm saved")
				.sees(m -> m.verification().getCommands().getConfirm().getEnabled())
				.leave();

		Administrator.at(anvil, PROXY)
				.setsPassword("Alice", "Changed-456")
				.deletes("Alice");

		Path database = Platform.dataDirectory(anvil, PROXY).resolve("identica.db");
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
			assertEquals(List.of(), foreignKeyViolations(connection));
			for (String table : ACCOUNT_TABLES) assertEquals(0, count(connection, table), table);
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
