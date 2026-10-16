package me.whereareiam.identica.provider.credential.database;

import me.whereareiam.strata.adapter.database.DatabaseContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Credential schema's third migration on an SQLite database that Identica wrote without foreign keys.
 */
@DisplayName("Orphaned History Migration")
class OrphanedHistoryMigrationTest {
	@DisplayName("Deletes the password history of credential accounts that are gone and keeps the rest")
	@Test
	void deletesOnlyOrphanedHistory(@TempDir Path directory) throws SQLException {
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("identica.db"))) {
			execute(connection,
					"CREATE TABLE identica_provider_credential_accounts (provider_id VARCHAR(64), provider_subject VARCHAR(255))",
					"CREATE TABLE identica_provider_credential_accounts_history (provider_id VARCHAR(64), provider_subject VARCHAR(255), hashing_method VARCHAR(32))",
					"INSERT INTO identica_provider_credential_accounts VALUES ('credential', 'alice')",
					"INSERT INTO identica_provider_credential_accounts_history VALUES ('credential', 'alice', 'bcrypt')",
					"INSERT INTO identica_provider_credential_accounts_history VALUES ('credential', 'bob', 'bcrypt')",
					"INSERT INTO identica_provider_credential_accounts_history VALUES ('credential', 'carol', 'argon2')");

			CredentialSchema.deleteOrphanedHistory(new SqlContext(connection));
			CredentialSchema.deleteOrphanedHistory(new SqlContext(connection));

			assertEquals(List.of("credential alice"), rows(connection, "SELECT provider_id, provider_subject FROM identica_provider_credential_accounts_history"));
		}
	}

	private static void execute(Connection connection, String... statements) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			for (String sql : statements) statement.execute(sql);
		}
	}

	private static List<String> rows(Connection connection, String query) throws SQLException {
		List<String> rows = new ArrayList<>();
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(query)) {
			while (result.next()) rows.add(result.getString(1) + " " + result.getString(2));
		}

		return rows;
	}

	/**
	 * Runs a migration's statements on one connection, as Strata's database context does.
	 */
	private record SqlContext(Connection connection) implements DatabaseContext {
		@Override
		public @NotNull Connection getConnection() {
			return connection;
		}

		@Override
		public @NotNull String getDialect() {
			return "sqlite";
		}

		@Override
		public void execute(@NotNull String sql) throws SQLException {
			OrphanedHistoryMigrationTest.execute(connection, sql);
		}

		@Override
		public int update(@NotNull String sql, Object... parameters) throws SQLException {
			try (PreparedStatement statement = connection.prepareStatement(sql)) {
				for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
				return statement.executeUpdate();
			}
		}

		@Override
		public boolean tableExists(@NotNull String table) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean columnExists(@NotNull String table, @NotNull String column) {
			throw new UnsupportedOperationException();
		}
	}
}
