package me.whereareiam.identica.adapter.database.schema;

import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.schema.ColumnDefinition;
import me.whereareiam.dialectica.schema.TableDefinition;
import me.whereareiam.dialectica.schema.change.AddColumn;
import me.whereareiam.dialectica.schema.change.CreateTable;
import me.whereareiam.dialectica.schema.type.ColumnType;
import me.whereareiam.identica.adapter.database.testing.schema.DatabaseSchemaContract;
import me.whereareiam.identica.adapter.database.testing.schema.EarlierInstallation;
import me.whereareiam.identica.adapter.database.testing.schema.SchemaSnapshot;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabase;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabases;
import me.whereareiam.identica.database.schema.DatabaseSchema;
import me.whereareiam.strata.adapter.database.DatabaseContext;
import org.h2.jdbcx.JdbcDataSource;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountSchemaTest extends DatabaseSchemaContract {
	private static final String ACCOUNT = "'00000000-0000-0000-0000-000000000001'";
	private static final String RELEASE = "identica-2.2.1/account";
	private static final String DEVELOPMENT_BUILD = "development-build/account";
	private static final String USERNAMES = "identica_account_usernames";
	private static final String STATE = "identica_account_username_state";

	private static final String UPGRADER = "dfe90880-73a1-3a0e-a7e7-8cda1bb6cb69";
	private static final String ALICE = "10920508-d5d8-3eed-93d2-92f193afe7d7";
	private static final String BOB = "faa5dca3-c3d4-354b-ae1b-dde9e5a14b3b";
	private static final String CAROL = "0af3f783-cbb9-32f0-953c-0d7e29e82d58";
	private static final String DAVE = "80333097-598c-3d5f-9b99-4ef1a3920f06";
	private static final String LOWERCASE_DAVE = "c2583fbf-5ec9-3d18-b0b1-9b4ceaff58d6";

	@Override
	protected DatabaseSchema schema() {
		return new AccountSchema();
	}

	@Override
	protected List<String> developmentBuild() {
		return List.of(DEVELOPMENT_BUILD);
	}

	/**
	 * Every existing installation starts at 0 and runs the username layout, which creates only what
	 * it lacks.
	 */
	@Override
	protected int developmentBuildVersion() {
		return 0;
	}

	@Override
	protected List<String> tables() {
		return List.of(
				"identica_accounts",
				"identica_provider_links",
				"identica_provider_profiles",
				"identica_account_reservations",
				USERNAMES,
				"identica_account_username_history"
		);
	}

	/**
	 * Development builds kept the username in the accounts and its source in a username state.
	 */
	@Override
	protected List<String> developmentBuildTables() {
		return List.of(
				"identica_accounts",
				"identica_provider_links",
				"identica_provider_profiles",
				"identica_account_reservations",
				STATE,
				"identica_account_username_history"
		);
	}

	/**
	 * Every account of a development build gets a username, and the states merge into them.
	 */
	@Override
	protected Map<String, Long> upgradedRows(Map<String, Long> developmentBuild) {
		Map<String, Long> rows = new LinkedHashMap<>(developmentBuild);
		rows.remove(STATE);
		rows.put(USERNAMES, developmentBuild.get("identica_accounts"));

		return rows;
	}

	@Override
	protected List<String> rows() {
		return List.of(
				"INSERT INTO identica_accounts (unique_id, username, created_at, last_seen_at) VALUES (" + ACCOUNT + ", 'Alice', 1, 2)",
				"INSERT INTO identica_provider_links (provider_id, provider_subject, unique_id, is_primary, linked_at, last_seen_at) VALUES ('example', 'alice', " + ACCOUNT + ", TRUE, 1, 2)",
				"INSERT INTO identica_provider_profiles (provider_id, provider_subject, provider_username) VALUES ('example', 'alice', 'Alice')",
				"INSERT INTO identica_account_reservations (reservation_key, unique_id, created_at, expires_at) VALUES ('alice', " + ACCOUNT + ", 1, 2)",
				"INSERT INTO identica_account_username_state (unique_id, source, updated_at) VALUES (" + ACCOUNT + ", 'PROVIDER', 3)",
				"INSERT INTO identica_account_username_history (unique_id, provider_id, old_username, new_username, source, changed_at) VALUES (" + ACCOUNT + ", NULL, 'Alice', 'Alicia', 'PROVIDER', 4)"
		);
	}

	/**
	 * Development builds left the username history index out on SQLite; the username layout adds it.
	 * The username table takes the username from the accounts and replaces the username state, whose
	 * key, reference to the account and column types it shares, as each database's metadata
	 * describes them; its key is unique.
	 */
	@Override
	protected SchemaSnapshot upgraded(TestDatabase database, SchemaSnapshot developmentBuild) {
		SchemaSnapshot.Table state = developmentBuild.tables().get(STATE);
		String username = developmentBuild.column("identica_accounts", "username");
		String varchar = username.substring("username ".length(), username.lastIndexOf(" not null"));
		List<String> columns = List.of(
				developmentBuild.column(STATE, "unique_id"),
				username,
				"username_key " + varchar + " null",
				developmentBuild.column(STATE, "source"),
				"provider_id " + varchar + " null",
				developmentBuild.column(STATE, "updated_at").replaceFirst("^updated_at", "changed_at")
		);
		Set<String> indexes = new TreeSet<>(state.indexes());
		indexes.add("unique username_key");

		return developmentBuild.withIndex("identica_account_username_history", "unique_id", "changed_at")
				.withoutColumn("identica_accounts", "username")
				.withoutTable(STATE)
				.withTable(USERNAMES, new SchemaSnapshot.Table(columns, state.primaryKey(), indexes, state.foreignKeys()));
	}

	/**
	 * Every earlier layout of the account tables, each on every database: Identica 2.2.1, the
	 * development builds before and after the MySQL fix, and a 2.2.1 database that a development
	 * build was started on, which then created its username tables next to the old ones.
	 */
	static Stream<Arguments> earlierInstallations() {
		List<List<String>> layouts = List.of(
				List.of(RELEASE),
				List.of("development-build-2026-06/account"),
				List.of(DEVELOPMENT_BUILD),
				List.of(RELEASE, DEVELOPMENT_BUILD)
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
		assertEquals(List.of("1 username-layout", "2 username-table"), history(database));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void keepsTheAccountsOf221AndMovesTheirUsernames(TestDatabase database) {
		database.reset();
		EarlierInstallation.create(database, RELEASE);
		long before = System.currentTimeMillis();

		install(database);

		long after = System.currentTimeMillis();
		assertEquals(Stream.of(
				ALICE + " 1000 2000",
				BOB + " 1100 null",
				CAROL + " null null",
				DAVE + " 1200 2200",
				UPGRADER + " 1000 2000",
				LOWERCASE_DAVE + " 1300 2300"
		).sorted().toList(), rows(database, "SELECT unique_id, created_at, last_seen_at FROM identica_accounts").stream().sorted().toList());
		assertEquals(4, count(database, "identica_provider_links"));
		assertEquals(4, count(database, "identica_provider_profiles"));
		assertEquals(1, count(database, "identica_account_reservations"));

		// Each username keeps its source and the time it was last seen to change; an account without a
		// source follows its provider. Dave is older than dave, so Dave holds the username, and dave
		// goes on without a key.
		List<String> usernames = rows(database, "SELECT unique_id, username, username_key, source, provider_id, changed_at FROM identica_account_usernames ORDER BY unique_id");
		assertEquals(Stream.of(
				ALICE + " Alice alice provider null 2000",
				BOB + " Bob bob manual null 1100",
				DAVE + " Dave dave provider null 2200",
				UPGRADER + " Upgrader upgrader provider null 2000",
				LOWERCASE_DAVE + " dave null manual null 2300"
		).sorted().toList(), usernames.stream().filter(row -> !row.startsWith(CAROL)).sorted().toList());
		String[] carol = usernames.stream().filter(row -> row.startsWith(CAROL)).findFirst().orElseThrow().split(" ");
		assertEquals(CAROL + " Carol carol system null", String.join(" ", List.of(carol).subList(0, 5)));
		long changedAt = Long.parseLong(carol[5]);
		assertTrue(changedAt >= before && changedAt <= after, "Carol's username was never seen, so it dates from the upgrade");

		// The history of the deleted account stays behind.
		assertEquals(List.of(
				ALICE + " example Alicia Alice provider 3000",
				BOB + " null Bobby Bob manual 3100",
				LOWERCASE_DAVE + " example Dave dave manual 3200"
		), rows(database, "SELECT unique_id, provider_id, old_username, new_username, source, changed_at FROM identica_account_username_history ORDER BY changed_at"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void changesNothingWhenA221DatabaseIsInstalledAgain(TestDatabase database) {
		database.reset();
		EarlierInstallation.create(database, RELEASE);
		install(database);
		List<Object> before = everything(database);

		install(database);

		assertEquals(before, everything(database));
	}

	/**
	 * DDL commits on its own on MySQL, MariaDB and H2, so a move that stopped halfway leaves the new
	 * tables with some rows next to the old ones; running it again finishes it without copying a row
	 * twice.
	 */
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void finishesAMoveThatStoppedHalfway(TestDatabase database) {
		database.reset();
		EarlierInstallation.create(database, RELEASE);
		EarlierInstallation.create(database, DEVELOPMENT_BUILD);
		database.connect(connection -> {
			try (Statement statement = connection.createStatement()) {
				statement.execute("INSERT INTO identica_account_username_state (unique_id, source, updated_at) VALUES ('" + BOB + "', 'manual', 1100)");
				statement.execute("""
						INSERT INTO identica_account_username_history (unique_id, provider_id, old_username, new_username, source, changed_at)
						SELECT unique_id, provider_id, old_username, new_username, source, changed_at
						  FROM identica_username_history
						 WHERE changed_at <= 3100
						""");
			}
			if (!connection.getAutoCommit()) connection.commit();
			return null;
		});

		install(database);

		assertEquals(List.of("manual", "manual", "system"), rows(database, "SELECT source FROM identica_account_usernames WHERE source <> 'provider' ORDER BY source"));
		assertEquals(3, count(database, "identica_account_username_history"));
	}

	/**
	 * Of the accounts that share a username without regard to case, the oldest holds it, and one of
	 * unknown age comes after every other. Usernames are lowered as Java lowers them, also outside
	 * ASCII, where SQLite's {@code LOWER} leaves letters as they are.
	 */
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void givesASharedUsernameToItsOldestAccount(TestDatabase database) {
		database.reset();
		EarlierInstallation.create(database, DEVELOPMENT_BUILD);
		insert(database,
				account(1, "Ärger", "2000"),
				account(2, "äRGER", "1000"),
				account(3, "Zed", "NULL"),
				account(4, "zed", "3000"),
				account(5, "ZED", "3000"),
				account(6, "Solo", "NULL"));

		install(database);

		assertEquals(List.of(
				uniqueId(1) + " null",
				uniqueId(2) + " ärger",
				uniqueId(3) + " null",
				uniqueId(4) + " zed",
				uniqueId(5) + " null",
				uniqueId(6) + " solo"
		), rows(database, "SELECT unique_id, username_key FROM identica_account_usernames ORDER BY unique_id"));
	}

	/**
	 * DDL commits on its own on MySQL, MariaDB and H2, so moving the usernames may stop after their
	 * table was filled, while the old column and the username state still exist; running it again
	 * writes every username anew from them and then drops them.
	 */
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void finishesAMoveOfTheUsernamesThatStoppedHalfway(TestDatabase database) {
		SchemaSnapshot expected = newInstallation(database);
		database.reset();
		EarlierInstallation.create(database, RELEASE);
		install(database);
		database.connect(connection -> {
			Dialectica.on(connection).apply(
					new AddColumn("identica_accounts", ColumnDefinition.builder("username", ColumnType.varchar(64)).build()),
					new CreateTable(TableDefinition.builder(STATE)
							.column(ColumnDefinition.builder("unique_id", ColumnType.uuid()).notNull().build())
							.column(ColumnDefinition.builder("source", ColumnType.varchar(32)).notNull().build())
							.column(ColumnDefinition.builder("updated_at", ColumnType.bigint()).notNull().build())
							.primaryKey("unique_id")
							.build())
			);
			try (Statement statement = connection.createStatement()) {
				statement.execute("UPDATE identica_accounts SET username = (SELECT u.username FROM identica_account_usernames u WHERE u.unique_id = identica_accounts.unique_id)");
				statement.execute("INSERT INTO " + STATE + " (unique_id, source, updated_at) VALUES ('" + LOWERCASE_DAVE + "', 'manual', 2300)");
				statement.execute("UPDATE identica_account_usernames SET username_key = NULL, source = 'system' WHERE unique_id = '" + DAVE + "'");
				statement.execute("DELETE FROM identica_account_usernames WHERE unique_id = '" + LOWERCASE_DAVE + "'");
				statement.execute("DELETE FROM strata_history WHERE stream = 'identica/account' AND version = 2");
			}
			if (!connection.getAutoCommit()) connection.commit();
			return null;
		});

		install(database);

		assertEquals(expected, database.connect(SchemaSnapshot::everyTable));
		assertEquals(List.of("1 username-layout", "2 username-table"), history(database));
		assertEquals(Stream.of(DAVE + " dave provider", LOWERCASE_DAVE + " null manual").sorted().toList(), rows(database,
				"SELECT unique_id, username_key, source FROM identica_account_usernames WHERE username IN ('Dave', 'dave')").stream().sorted().toList());
	}

	@Test
	void treatsADatabaseWithoutAccountsAsNew() throws Exception {
		assertEquals(OptionalInt.empty(), baseline());
	}

	@Test
	void startsEveryExistingAccountTableAtZero() throws Exception {
		assertEquals(OptionalInt.of(0), baseline(
				"CREATE TABLE identica_accounts (unique_id UUID PRIMARY KEY, username VARCHAR(64) NOT NULL, username_source VARCHAR(32) NOT NULL)"));
		assertEquals(OptionalInt.of(0), baseline(
				"CREATE TABLE identica_accounts (unique_id UUID PRIMARY KEY, username VARCHAR(64) NOT NULL)",
				"CREATE TABLE identica_account_username_state (unique_id UUID PRIMARY KEY)"));
	}

	private List<Object> everything(TestDatabase database) {
		List<Object> state = new ArrayList<>();
		state.add(database.connect(SchemaSnapshot::everyTable));
		state.add(history(database));
		for (String table : tables())
			state.add(rows(database, "SELECT * FROM " + table + " ORDER BY 1, 2"));

		return state;
	}

	private static String uniqueId(int account) {
		return "00000000-0000-0000-0000-00000000000" + account;
	}

	private static String account(int account, String username, String createdAt) {
		return "INSERT INTO identica_accounts (unique_id, username, created_at, last_seen_at) VALUES ('"
				+ uniqueId(account) + "', '" + username + "', " + createdAt + ", NULL)";
	}

	private static void insert(TestDatabase database, String... statements) {
		database.connect(connection -> {
			try (Statement statement = connection.createStatement()) {
				for (String sql : statements) statement.execute(sql);
			}
			if (!connection.getAutoCommit()) connection.commit();
			return null;
		});
	}

	private static long count(TestDatabase database, String table) {
		return database.connect(connection -> {
			try (Statement statement = connection.createStatement();
				 ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
				rows.next();
				return rows.getLong(1);
			}
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

	private static OptionalInt baseline(String... statements) throws Exception {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:account_baseline_" + UUID.randomUUID());
		try (Connection connection = dataSource.getConnection()) {
			try (Statement statement = connection.createStatement()) {
				for (String sql : statements) statement.execute(sql);
			}

			return new AccountSchema().baseline(new MetadataContext(connection), 1);
		}
	}

	/**
	 * Answers the baseline's questions from the connection's metadata, as Strata's database
	 * context does.
	 */
	private record MetadataContext(Connection connection) implements DatabaseContext {
		@Override
		public @NotNull Connection getConnection() {
			return connection;
		}

		@Override
		public @NotNull String getDialect() throws SQLException {
			return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
		}

		@Override
		public void execute(@NotNull String sql) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int update(@NotNull String sql, Object... parameters) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean tableExists(@NotNull String table) throws SQLException {
			DatabaseMetaData metaData = connection.getMetaData();
			try (ResultSet tables = metaData.getTables(null, null, table.toUpperCase(Locale.ROOT), null)) {
				return tables.next();
			}
		}

		@Override
		public boolean columnExists(@NotNull String table, @NotNull String column) throws SQLException {
			DatabaseMetaData metaData = connection.getMetaData();
			try (ResultSet columns = metaData.getColumns(null, null, table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT))) {
				return columns.next();
			}
		}
	}
}
