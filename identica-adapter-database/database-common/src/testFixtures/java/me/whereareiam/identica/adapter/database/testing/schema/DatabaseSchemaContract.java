package me.whereareiam.identica.adapter.database.testing.schema;

import me.whereareiam.identica.adapter.database.DefaultDatabaseService;
import me.whereareiam.identica.database.schema.DatabaseSchema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What installing an owner's {@link DatabaseSchema} guarantees on every database: a new database
 * gets exactly the tables a development build of Identica 3.0.0 created, installing again changes
 * nothing, and a database that development build created is recognised and kept with its rows.
 * Each owner's schema test extends it.
 */
public abstract class DatabaseSchemaContract {
	private static final String HISTORY = "strata_history";

	/**
	 * Returns the schema under test.
	 */
	protected abstract DatabaseSchema schema();

	/**
	 * Returns the {@link EarlierInstallation} layouts of the last development build to create, in
	 * order: those of the owners this schema requires, then its own, such as
	 * {@code development-build/account}.
	 */
	protected abstract List<String> developmentBuild();

	/**
	 * Returns the version this schema's baseline gives the tables of the last development build.
	 */
	protected int developmentBuildVersion() {
		return 1;
	}

	/**
	 * Returns the tables of this schema.
	 */
	protected abstract List<String> tables();

	/**
	 * Returns statements that fill the tables of a development build with rows.
	 */
	protected abstract List<String> rows();

	/**
	 * Returns the schema a development build has once this schema's migrations ran on it.
	 */
	protected SchemaSnapshot upgraded(SchemaSnapshot developmentBuild) {
		return developmentBuild;
	}

	/**
	 * Returns the schema a new database gets, when it differs from what a development build created
	 * and this schema's migrations then changed.
	 */
	protected SchemaSnapshot created(TestDatabase database, SchemaSnapshot developmentBuild) {
		return upgraded(developmentBuild);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void createsTheTablesOfADevelopmentBuildOnANewDatabase(TestDatabase database) {
		SchemaSnapshot expected = created(database, developmentBuildSnapshot(database));
		Set<String> tables = new TreeSet<>(database.connect(SchemaSnapshot::tableNames));
		tables.add(HISTORY);
		database.reset();

		install(database);

		database.connect(connection -> {
			assertEquals(tables, SchemaSnapshot.tableNames(connection), "tables");
			assertEquals(expected, SchemaSnapshot.of(connection, tables()));
			assertEquals(List.of(latest() + " creation"), history(connection));
			return null;
		});
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void changesNothingWhenInstalledAgain(TestDatabase database) {
		database.reset();
		install(database);
		Object before = database.connect(connection -> List.of(SchemaSnapshot.of(connection, tables()), history(connection)));

		install(database);

		Object after = database.connect(connection -> List.of(SchemaSnapshot.of(connection, tables()), history(connection)));
		assertEquals(before, after);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void keepsTheTablesAndRowsOfADevelopmentBuild(TestDatabase database) {
		SchemaSnapshot developmentBuild = developmentBuildSnapshot(database);
		Map<String, Long> rows = database.connect(connection -> {
			try (Statement statement = connection.createStatement()) {
				for (String row : rows()) statement.execute(row);
			}
			if (!connection.getAutoCommit()) connection.commit();
			return counts(connection);
		});

		install(database);

		database.connect(connection -> {
			assertEquals(upgraded(developmentBuild), SchemaSnapshot.of(connection, tables()));
			assertEquals(rows, counts(connection));
			int baseline = developmentBuildVersion();
			List<String> expected = new ArrayList<>();
			if (baseline > 0) expected.add(baseline + " baseline");
			schema().migrations().stream()
					.filter(migration -> migration.getVersion() > baseline)
					.forEach(migration -> expected.add(migration.getVersion() + " " + migration.getName()));
			assertEquals(expected, history(connection));
			return null;
		});
	}

	private SchemaSnapshot developmentBuildSnapshot(TestDatabase database) {
		database.reset();
		developmentBuild().forEach(layout -> EarlierInstallation.create(database, layout));

		return database.connect(connection -> SchemaSnapshot.of(connection, tables()));
	}

	/**
	 * Returns every table of the database once a new installation got this schema and the ones it
	 * requires, as an upgraded database has to end up.
	 */
	protected final SchemaSnapshot newInstallation(TestDatabase database) {
		database.reset();
		install(database);

		return database.connect(SchemaSnapshot::everyTable);
	}

	/**
	 * Opens the database as Identica does, which installs the core schema, and installs this one.
	 */
	protected final void install(TestDatabase database) {
		DefaultDatabaseService service = database.open();
		try {
			service.getSchemaInstaller().install(schema());
		} finally {
			TestDatabase.close(service);
		}
	}

	/**
	 * Returns the version and name of each entry Strata recorded for this schema, in order.
	 */
	protected final List<String> history(TestDatabase database) {
		return database.connect(this::history);
	}

	private int latest() {
		return schema().migrations().stream().mapToInt(migration -> migration.getVersion()).max().orElse(0);
	}

	private List<String> history(Connection connection) throws SQLException {
		List<String> entries = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT version, name FROM " + HISTORY + " WHERE stream = ? ORDER BY version")) {
			statement.setString(1, schema().stream());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) entries.add(rows.getInt("version") + " " + rows.getString("name"));
			}
		}

		return entries;
	}

	private Map<String, Long> counts(Connection connection) throws SQLException {
		Map<String, Long> counts = new LinkedHashMap<>();
		try (Statement statement = connection.createStatement()) {
			for (String table : tables())
				try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
					rows.next();
					counts.put(table, rows.getLong(1));
				}
		}

		return counts;
	}
}
