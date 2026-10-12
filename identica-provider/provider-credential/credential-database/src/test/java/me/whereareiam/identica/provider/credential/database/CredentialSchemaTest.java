package me.whereareiam.identica.provider.credential.database;

import me.whereareiam.identica.adapter.database.testing.schema.DatabaseSchemaContract;
import me.whereareiam.identica.adapter.database.testing.schema.EarlierInstallation;
import me.whereareiam.identica.adapter.database.testing.schema.SchemaSnapshot;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabase;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabases;
import me.whereareiam.identica.database.schema.DatabaseSchema;
import me.whereareiam.strata.exception.MigrationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialSchemaTest extends DatabaseSchemaContract {
	private static final String RELEASE_ACCOUNT = "identica-2.2.1/account";
	private static final String RELEASE = "identica-2.2.1/credential";
	private static final String UPGRADER = "dfe90880-73a1-3a0e-a7e7-8cda1bb6cb69";
	private static final String UPGRADER_PASSWORD = "$2a$12$PmVAazF1bucN9B1.0J/Pl.NfXkoT.fm.y73Rl8otGtpUFyUI3axje";

	@Override
	protected DatabaseSchema schema() {
		return new CredentialSchema();
	}

	@Override
	protected List<String> developmentBuild() {
		return List.of("development-build/account", "development-build/credential");
	}

	@Override
	protected List<String> tables() {
		return List.of(
				"identica_provider_credential_accounts",
				"identica_provider_credential_accounts_history"
		);
	}

	@Override
	protected List<String> rows() {
		return List.of(
				"INSERT INTO identica_provider_credential_accounts (provider_id, provider_subject, password_hash, hashing_method, created_at, updated_at) VALUES ('credential', 'alice', 'hash', 'ARGON2', 1, NULL)",
				"INSERT INTO identica_provider_credential_accounts_history (provider_id, provider_subject, hashing_method, change_reason, changed_at) VALUES ('credential', 'alice', 'ARGON2', 'REGISTER', 1)"
		);
	}

	/**
	 * Identica 2.2.1 and the development builds of June, which kept the provider's migrations in
	 * {@code dialectica_schema_migrations}, and the later development builds, which did not.
	 */
	static Stream<Arguments> earlierInstallations() {
		List<List<String>> layouts = List.of(
				List.of(RELEASE_ACCOUNT, RELEASE),
				List.of("development-build-2026-06/account", "development-build-2026-06/credential"),
				List.of("development-build/account", "development-build/credential")
		);

		return TestDatabases.all().flatMap(database -> layouts.stream().map(layout -> Arguments.of(database, layout)));
	}

	@ParameterizedTest(name = "{1} on {0}")
	@MethodSource("earlierInstallations")
	void bringsAnEarlierInstallationToTheTablesOfANewOne(TestDatabase database, List<String> layout) {
		SchemaSnapshot expected = newInstallation(database);
		database.reset();
		layout.forEach(owner -> EarlierInstallation.create(database, owner));

		install(database);

		assertEquals(expected, database.connect(SchemaSnapshot::everyTable));
		assertEquals(List.of("1 baseline", "2 legacy-migration-history"), history(database));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void keepsThePasswordsOf221(TestDatabase database) {
		database.reset();
		EarlierInstallation.create(database, RELEASE_ACCOUNT);
		EarlierInstallation.create(database, RELEASE);

		install(database);

		assertEquals(List.of("credential " + UPGRADER + " " + UPGRADER_PASSWORD + " bcrypt"), rows(database,
				"SELECT provider_id, provider_subject, password_hash, hashing_method FROM identica_provider_credential_accounts WHERE provider_subject = '" + UPGRADER + "'"));
		assertEquals(List.of("2"), rows(database, "SELECT COUNT(*) FROM identica_provider_credential_accounts"));
		assertEquals(List.of("2"), rows(database, "SELECT COUNT(*) FROM identica_provider_credential_accounts_history"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void changesNothingWhenA221DatabaseIsInstalledAgain(TestDatabase database) {
		database.reset();
		EarlierInstallation.create(database, RELEASE_ACCOUNT);
		EarlierInstallation.create(database, RELEASE);
		install(database);
		List<Object> before = everything(database);

		install(database);

		assertEquals(before, everything(database));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void refusesTheTablesOfTheCrackedProviderOf1x(TestDatabase database) {
		database.reset();
		EarlierInstallation.create(database, RELEASE_ACCOUNT);
		execute(database, "CREATE TABLE identica_cracked_accounts (provider_id VARCHAR(64) NOT NULL, provider_subject VARCHAR(128) NOT NULL, PRIMARY KEY (provider_id, provider_subject))");

		assertRefused(database, "identica_cracked_accounts");
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void refusesCredentialMigrationsOf2xThatDidNotFinish(TestDatabase database) {
		database.reset();
		EarlierInstallation.create(database, RELEASE_ACCOUNT);
		EarlierInstallation.create(database, RELEASE);
		execute(database, "DELETE FROM dialectica_schema_migrations WHERE version = 2");

		assertRefused(database, "dialectica_schema_migrations");
	}

	/**
	 * Installing fails with a message that tells the administrator what to do, and leaves the
	 * credential tables as they were, so that 2.2.1 can still start on them.
	 */
	private void assertRefused(TestDatabase database, String keptTable) {
		MigrationException refusal = assertThrows(MigrationException.class, () -> install(database));

		String message = messages(refusal);
		assertTrue(message.contains("Start Identica 2.2.1 on this database once"), message);
		assertEquals(List.of(), history(database));
		assertTrue(database.connect(SchemaSnapshot::tableNames).contains(keptTable), keptTable + " must stay");
	}

	private static String messages(Throwable failure) {
		StringBuilder messages = new StringBuilder();
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) messages.append(cause.getMessage()).append('\n');

		return messages.toString();
	}

	private List<Object> everything(TestDatabase database) {
		List<Object> state = new ArrayList<>();
		state.add(database.connect(SchemaSnapshot::everyTable));
		state.add(history(database));
		for (String table : tables())
			state.add(rows(database, "SELECT * FROM " + table + " ORDER BY 1, 2"));

		return state;
	}

	private static void execute(TestDatabase database, String sql) {
		database.connect(connection -> {
			try (Statement statement = connection.createStatement()) {
				statement.execute(sql);
			}
			if (!connection.getAutoCommit()) connection.commit();
			return null;
		});
	}

	private static List<String> rows(TestDatabase database, String query) {
		return database.connect(connection -> {
			List<String> rows = new ArrayList<>();
			try (Statement statement = connection.createStatement();
				 ResultSet result = statement.executeQuery(query)) {
				int columns = result.getMetaData().getColumnCount();
				while (result.next()) {
					List<String> values = new ArrayList<>();
					for (int column = 1; column <= columns; column++) values.add(String.valueOf(result.getString(column)));
					rows.add(String.join(" ", values));
				}
			}

			return rows;
		});
	}
}
