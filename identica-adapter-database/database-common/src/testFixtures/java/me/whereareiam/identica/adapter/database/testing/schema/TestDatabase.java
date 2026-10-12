package me.whereareiam.identica.adapter.database.testing.schema;

import com.zaxxer.hikari.HikariDataSource;
import me.whereareiam.attache.LibraryManager;
import me.whereareiam.identica.adapter.database.DefaultDatabaseService;
import me.whereareiam.identica.adapter.database.connection.DataSourceFactory;
import me.whereareiam.identica.model.config.persistence.Persistence;
import me.whereareiam.identica.type.DatabaseType;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import static org.mockito.Mockito.mock;

/**
 * A database that schema tests run against. {@link #reset()} empties it before a test; the test
 * then prepares it through {@link #connect(SqlWork)} and opens it as Identica does with
 * {@link #open()}.
 */
public abstract class TestDatabase {
	private Path dataPath;
	private Persistence persistence;

	/**
	 * Returns the database's name, as test reports show it.
	 */
	public abstract String name();

	/**
	 * Returns the type Identica configures for this database.
	 */
	public abstract DatabaseType type();

	/**
	 * Configures Identica for an empty database of this kind.
	 *
	 * @param dataPath the data directory of this test
	 */
	protected abstract Persistence persistence(Path dataPath);

	/**
	 * Removes every table a previous test left, for a database that outlives a test.
	 */
	protected void clear(Connection connection) throws SQLException {
	}

	/**
	 * Gives this test an empty database and a new data directory.
	 */
	public void reset() {
		try {
			dataPath = Files.createTempDirectory("identica-schema-");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		persistence = persistence(dataPath);
		connect(connection -> {
			clear(connection);
			return null;
		});
	}

	/**
	 * Runs work on a plain connection, outside Identica.
	 */
	public <T> T connect(SqlWork<T> work) {
		DataSource dataSource = DataSourceFactory.create(requirePersistence(), dataPath);
		try (Connection connection = dataSource.getConnection()) {
			return work.run(connection);
		} catch (SQLException e) {
			throw new IllegalStateException("Database work failed on " + name(), e);
		} finally {
			if (dataSource instanceof HikariDataSource hikari) hikari.close();
		}
	}

	/**
	 * Opens the database as Identica does, which installs the core schema.
	 */
	public DefaultDatabaseService open() {
		Persistence persistence = requirePersistence();
		DefaultDatabaseService database = new DefaultDatabaseService(() -> persistence, dataPath, mock(LibraryManager.class));
		database.initialize();
		return database;
	}

	/**
	 * Closes a database opened by {@link #open()}.
	 */
	public static void close(DefaultDatabaseService database) {
		database.shutdown();
	}

	private Persistence requirePersistence() {
		if (persistence == null) throw new IllegalStateException("Call reset() before using " + name());

		return persistence;
	}

	@Override
	public String toString() {
		return name();
	}

	/**
	 * Work on a JDBC connection.
	 */
	@FunctionalInterface
	public interface SqlWork<T> {
		T run(Connection connection) throws SQLException;
	}
}
