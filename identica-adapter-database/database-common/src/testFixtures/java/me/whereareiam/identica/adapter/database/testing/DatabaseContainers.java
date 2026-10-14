package me.whereareiam.identica.adapter.database.testing;

import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.Map;

/**
 * Database servers tests start in containers, each given {@link #STARTUP_TIMEOUT} to become ready.
 * <p>
 * A test server keeps nothing worth flushing to disk, so none of them waits for the disk: Testcontainers starts
 * PostgreSQL with {@code fsync=off} itself, and MariaDB and MySQL keep their data in memory and do not flush
 * their logs on commit. Every statement of MySQL's data dictionary otherwise waits for several flushes, which
 * made a schema installation take seconds and a first start minutes on a busy machine.
 */
public final class DatabaseContainers {
	/**
	 * How long a database server may take to accept connections. A first start initialises the data directory,
	 * and MySQL restarts once after that; on a loaded machine this has taken well over two minutes, longer than
	 * the default timeouts of Testcontainers.
	 */
	public static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(5);

	private static final Map<String, String> DATA_IN_MEMORY = Map.of("/var/lib/mysql", "rw");
	private static final String NO_FLUSHES = "--innodb-flush-log-at-trx-commit=0 --innodb-doublewrite=0 --sync-binlog=0 --skip-log-bin";

	/**
	 * Returns a PostgreSQL server of the given image.
	 */
	public static PostgreSQLContainer postgres(String image) {
		// PostgreSQL waits for its log message, so the timeout belongs to the wait strategy.
		return new PostgreSQLContainer(image).withStartupTimeout(STARTUP_TIMEOUT);
	}

	/**
	 * Returns a MariaDB server of the given image.
	 */
	public static MariaDBContainer mariadb(String image) {
		// MariaDB and MySQL wait for a JDBC connection, which ignores the wait strategy's timeout.
		return new MariaDBContainer(image)
				.withTmpFs(DATA_IN_MEMORY)
				.withCommand(NO_FLUSHES)
				.withStartupTimeoutSeconds((int) STARTUP_TIMEOUT.toSeconds());
	}

	/**
	 * Returns a MySQL server of the given image that signs in with the native password plugin, the one the
	 * MariaDB driver Identica reaches MySQL through uses.
	 */
	public static MySQLContainer mysql(String image) {
		return new MySQLContainer(image)
				.withTmpFs(DATA_IN_MEMORY)
				.withCommand(NO_FLUSHES + " --default-authentication-plugin=mysql_native_password")
				.withStartupTimeoutSeconds((int) STARTUP_TIMEOUT.toSeconds());
	}
}
