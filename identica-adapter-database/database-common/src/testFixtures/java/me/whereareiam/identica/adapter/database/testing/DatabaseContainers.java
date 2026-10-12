package me.whereareiam.identica.adapter.database.testing;

import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;

/**
 * Database servers tests start in containers, each given {@link #STARTUP_TIMEOUT} to become ready.
 */
public final class DatabaseContainers {
	/**
	 * How long a database server may take to accept connections. A first start initialises the data directory,
	 * and MySQL restarts once after that; on a loaded machine this has taken well over two minutes, longer than
	 * the default timeouts of Testcontainers.
	 */
	public static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(5);

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
		return new MariaDBContainer(image).withStartupTimeoutSeconds((int) STARTUP_TIMEOUT.toSeconds());
	}

	/**
	 * Returns a MySQL server of the given image that signs in with the native password plugin, the one the
	 * MariaDB driver Identica reaches MySQL through uses.
	 */
	public static MySQLContainer mysql(String image) {
		return new MySQLContainer(image)
				.withCommand("--default-authentication-plugin=mysql_native_password")
				.withStartupTimeoutSeconds((int) STARTUP_TIMEOUT.toSeconds());
	}
}
