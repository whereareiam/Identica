package me.whereareiam.identica.adapter.database.testing.schema;

import me.whereareiam.identica.adapter.database.testing.DatabaseContainers;
import me.whereareiam.identica.model.config.persistence.H2Persistence;
import me.whereareiam.identica.model.config.persistence.Persistence;
import me.whereareiam.identica.model.config.persistence.SqlitePersistence;
import me.whereareiam.identica.model.config.persistence.external.ExternalPersistence;
import me.whereareiam.identica.model.config.persistence.external.MysqlPersistence;
import me.whereareiam.identica.model.config.persistence.external.PostgresPersistence;
import me.whereareiam.identica.type.DatabaseType;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The databases schema tests run against: H2 and SQLite always, and PostgreSQL, MariaDB and MySQL
 * in containers when Docker is available. A container starts on first use and serves every test of
 * the run.
 */
public final class TestDatabases {
	private static final AtomicInteger H2_NAMES = new AtomicInteger();

	private static final Lazy<PostgreSQLContainer> POSTGRES = new Lazy<>(() -> DatabaseContainers.postgres("postgres:18.1"));
	private static final Lazy<MariaDBContainer> MARIADB = new Lazy<>(() -> DatabaseContainers.mariadb("mariadb:12"));
	private static final Lazy<MySQLContainer> MYSQL = new Lazy<>(() -> DatabaseContainers.mysql("mysql:8.0"));

	private TestDatabases() {
	}

	/**
	 * Returns every database available to this run.
	 */
	public static Stream<TestDatabase> all() {
		List<TestDatabase> databases = new ArrayList<>(List.of(new H2(), new Sqlite()));
		if (DockerClientFactory.instance().isDockerAvailable())
			databases.addAll(List.of(new Postgres(), new Mariadb(), new Mysql()));

		return databases.stream();
	}

	private static final class H2 extends TestDatabase {
		@Override
		public String name() {
			return "h2";
		}

		@Override
		public DatabaseType type() {
			return DatabaseType.H2;
		}

		@Override
		protected Persistence persistence(Path dataPath) {
			H2Persistence persistence = new H2Persistence();
			persistence.setFile("mem:identica_schema_" + H2_NAMES.incrementAndGet());
			persistence.setOptions("DB_CLOSE_DELAY=-1");
			return persistence;
		}
	}

	private static final class Sqlite extends TestDatabase {
		@Override
		public String name() {
			return "sqlite";
		}

		@Override
		public DatabaseType type() {
			return DatabaseType.SQLITE;
		}

		@Override
		protected Persistence persistence(Path dataPath) {
			SqlitePersistence persistence = new SqlitePersistence();
			persistence.setFile("identica.db");
			return persistence;
		}
	}

	private static final class Postgres extends TestDatabase {
		@Override
		public String name() {
			return "postgresql";
		}

		@Override
		public DatabaseType type() {
			return DatabaseType.POSTGRES;
		}

		@Override
		protected Persistence persistence(Path dataPath) {
			return external(new PostgresPersistence(), POSTGRES.get(), PostgreSQLContainer.POSTGRESQL_PORT);
		}

		@Override
		protected void clear(Connection connection) throws SQLException {
			try (Statement statement = connection.createStatement()) {
				statement.execute("DROP SCHEMA public CASCADE");
				statement.execute("CREATE SCHEMA public");
			}
		}
	}

	private static class Mariadb extends TestDatabase {
		@Override
		public String name() {
			return "mariadb";
		}

		@Override
		public DatabaseType type() {
			return DatabaseType.MYSQL;
		}

		@Override
		protected Persistence persistence(Path dataPath) {
			return external(new MysqlPersistence(), MARIADB.get(), 3306);
		}

		@Override
		protected void clear(Connection connection) throws SQLException {
			List<String> tables = new ArrayList<>();
			try (ResultSet rows = connection.getMetaData().getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
				while (rows.next()) tables.add(rows.getString("TABLE_NAME"));
			}
			try (Statement statement = connection.createStatement()) {
				statement.execute("SET FOREIGN_KEY_CHECKS = 0");
				for (String table : tables) statement.execute("DROP TABLE `" + table + "`");
				statement.execute("SET FOREIGN_KEY_CHECKS = 1");
			}
		}
	}

	private static final class Mysql extends Mariadb {
		@Override
		public String name() {
			return "mysql";
		}

		@Override
		protected Persistence persistence(Path dataPath) {
			return external(new MysqlPersistence(), MYSQL.get(), 3306);
		}
	}

	private static Persistence external(ExternalPersistence persistence, JdbcDatabaseContainer<?> container, int port) {
		persistence.setHost(container.getHost());
		persistence.setPort(container.getMappedPort(port));
		persistence.setDatabase(container.getDatabaseName());
		persistence.setUsername(container.getUsername());
		persistence.setPassword(container.getPassword());
		return persistence;
	}

	/**
	 * A container started on first use.
	 */
	private static final class Lazy<C extends JdbcDatabaseContainer<?>> {
		private final Supplier<C> factory;
		private C container;

		private Lazy(Supplier<C> factory) {
			this.factory = factory;
		}

		synchronized C get() {
			if (container == null) {
				container = factory.get();
				container.start();
			}

			return container;
		}
	}
}
