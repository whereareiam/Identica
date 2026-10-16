package me.whereareiam.identica.adapter.database.schema;

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
 * The account schema's third migration on an SQLite database that Identica wrote without foreign keys.
 */
@DisplayName("Orphaned Profiles Migration")
class OrphanedProfilesMigrationTest {
	@DisplayName("Deletes the profiles whose link is gone and keeps the others")
	@Test
	void deletesOnlyOrphanedProfiles(@TempDir Path directory) throws SQLException {
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("identica.db"))) {
			execute(connection,
					"CREATE TABLE identica_provider_links (provider_id VARCHAR(64), provider_subject VARCHAR(255))",
					"CREATE TABLE identica_provider_profiles (provider_id VARCHAR(64), provider_subject VARCHAR(255), provider_username VARCHAR(64))",
					"INSERT INTO identica_provider_links VALUES ('credential', 'alice')",
					"INSERT INTO identica_provider_profiles VALUES ('credential', 'alice', 'Alice')",
					"INSERT INTO identica_provider_profiles VALUES ('credential', 'bob', 'Bob')",
					"INSERT INTO identica_provider_profiles VALUES ('premium', 'alice', 'Alice')");

			AccountSchema.deleteOrphanedProfiles(new SqlContext(connection));
			AccountSchema.deleteOrphanedProfiles(new SqlContext(connection));

			assertEquals(List.of("credential alice"), rows(connection, "SELECT provider_id, provider_subject FROM identica_provider_profiles"));
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
			OrphanedProfilesMigrationTest.execute(connection, sql);
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
