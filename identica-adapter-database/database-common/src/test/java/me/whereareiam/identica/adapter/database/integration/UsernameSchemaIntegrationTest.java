package me.whereareiam.identica.adapter.database.integration;

import me.whereareiam.identica.adapter.database.testing.DatabaseFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Username Schema")
class UsernameSchemaIntegrationTest extends DatabaseIntegrationTestBase {
	private static final String HISTORY_TABLE = "identica_account_username_history";
	private static final String HISTORY_INDEX = "identica_account_username_history_unique_id_changed_at_idx";

	@DisplayName("Creates the username tables with the core schema")
	@ParameterizedTest(name = "{0}")
	@MethodSource("fixtures")
	void createsTables(DatabaseFixture fixture) {
		fixture.useHandle(handle -> {
			handle.createQuery("SELECT COUNT(*) FROM identica_account_usernames").mapTo(Long.class).one();
			handle.createQuery("SELECT COUNT(*) FROM " + HISTORY_TABLE).mapTo(Long.class).one();
		});
	}

	@DisplayName("Creates the history index on MySQL")
	@ParameterizedTest(name = "{0}")
	@MethodSource("mysqlFixtures")
	void createsHistoryIndexOnMysql(DatabaseFixture fixture) throws SQLException {
		Set<String> indexes = fixture.jdbi().withHandle(handle -> indexNames(handle.getConnection().getMetaData()));
		assertTrue(indexes.contains(HISTORY_INDEX), "Missing " + HISTORY_INDEX + ": " + indexes);
	}

	private Stream<DatabaseFixture> mysqlFixtures() {
		return fixtures().filter(fixture -> fixture.name().equals("mariadb"));
	}

	private static Set<String> indexNames(DatabaseMetaData metaData) throws SQLException {
		Set<String> names = new HashSet<>();
		for (String table : Set.of(HISTORY_TABLE, HISTORY_TABLE.toUpperCase(Locale.ROOT))) {
			try (ResultSet indexes = metaData.getIndexInfo(null, null, table, false, false)) {
				while (indexes.next()) {
					String name = indexes.getString("INDEX_NAME");
					if (name != null) names.add(name.toLowerCase(Locale.ROOT));
				}
			}
		}
		return names;
	}
}
