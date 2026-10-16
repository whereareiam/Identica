package me.whereareiam.identica.adapter.database.connection;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.persistence.H2Persistence;
import me.whereareiam.identica.model.config.persistence.Persistence;
import me.whereareiam.identica.model.config.persistence.SqlitePersistence;
import me.whereareiam.identica.model.config.persistence.external.ExternalPersistence;
import me.whereareiam.identica.type.DatabaseType;
import org.jdbi.v3.core.ConnectionException;

import javax.sql.DataSource;
import java.net.ConnectException;
import java.nio.file.Path;
import java.sql.SQLException;

/**
 * Factory for creating HikariCP DataSource instances.
 */
public final class DataSourceFactory {
	/**
	 * Creates a HikariCP DataSource with the given configuration.
	 *
	 * @param persistence the database configuration
	 * @param dataPath    runner path for relative database files
	 * @return the initialized DataSource
	 */
	public static DataSource create(Persistence persistence, Path dataPath) {
		Persistence.Hikari hikariConfig = persistence.getHikari();

		// Build JDBC URL based on database type
		String jdbcUrl = JdbcUrlFactory.create(persistence, dataPath);
		Logger.info("Connecting to database at %s", maskJdbcUrl(jdbcUrl));

		try {
			// Configure HikariCP DataSource
			HikariConfig hikariConfigObj = new HikariConfig();
			hikariConfigObj.setJdbcUrl(jdbcUrl);
			hikariConfigObj.setDriverClassName(getDriverClassName(persistence.getType()));
			if (persistence instanceof ExternalPersistence external) {
				hikariConfigObj.setUsername(external.getUsername());
				hikariConfigObj.setPassword(external.getPassword());
			}
			// SQLite checks foreign keys, and cascades deletes along them, only on connections that turn them on.
			if (persistence.getType() == DatabaseType.SQLITE)
				hikariConfigObj.setConnectionInitSql("PRAGMA foreign_keys = ON");

			// Apply HikariCP pool settings from config
			hikariConfigObj.setPoolName(hikariConfig.getPoolName());
			hikariConfigObj.setMaximumPoolSize(hikariConfig.getMaximumPoolSize());
			hikariConfigObj.setMinimumIdle(hikariConfig.getMinimumIdle());
			hikariConfigObj.setConnectionTimeout(hikariConfig.getConnectionTimeout());
			hikariConfigObj.setIdleTimeout(hikariConfig.getIdleTimeout());
			hikariConfigObj.setMaxLifetime(hikariConfig.getMaxLifetime());

			// Disable fail-fast to allow plugin to start even if database is unavailable
			hikariConfigObj.setInitializationFailTimeout(-1);

			return new HikariDataSource(hikariConfigObj);
		} catch (Exception e) {
			handleInitializationError(e, persistence);
			throw new RuntimeException("Failed to initialize HikariCP", e);
		}
	}

	private static void handleInitializationError(Exception e, Persistence persistence) {
		Throwable cause = e.getCause();
		String errorMessage = e.getMessage();

		// Check if it's a connection error
		boolean isConnectionError = e instanceof HikariPool.PoolInitializationException;

		if (!isConnectionError && cause != null) {
			if (cause instanceof ConnectException ||
					cause instanceof SQLException ||
					cause instanceof ConnectionException ||
					cause.getClass().getName().startsWith("org.postgresql.") ||
					cause.getClass().getName().startsWith("org.mariadb.") ||
					cause.getClass().getName().startsWith("org.h2.")) {
				isConnectionError = true;
			}
		}

		if (isConnectionError) {
			if (persistence instanceof SqlitePersistence sqlite) {
				Logger.severe("Failed to open SQLite database file. Please ensure:");
				Logger.severe("  - File path is correct (%s)", sqlite.getFile());
				Logger.severe("  - Parent directory is writable");
			}

			if (persistence instanceof H2Persistence h2) {
				Logger.severe("Failed to open H2 database file. Please ensure:");
				Logger.severe("  - File path is correct (%s)", h2.getFile());
				Logger.severe("  - Parent directory is writable");
			}

			if (persistence instanceof ExternalPersistence external) {
				Logger.severe("Failed to connect to database server. Please ensure:");
				Logger.severe("  - Database server is running");
				Logger.severe("  - Host and port are correct (%s:%d)", external.getHost(), external.getPort());
				Logger.severe("  - Database server is accepting connections");
			} else {
				Logger.severe("Failed to connect to database server.");
			}

			Logger.severe("Database features will be disabled until connection is established.");
			return;
		}

		Logger.severe("Failed to initialize database connection: %s", errorMessage);
		if (cause != null && !errorMessage.equals(cause.getMessage()))
			Logger.severe("Caused by: %s", cause.getMessage());
	}

	private static String maskJdbcUrl(String jdbcUrl) {
		return jdbcUrl.replaceAll("password=[^;&]+", "password=***");
	}

	private static String getDriverClassName(DatabaseType type) {
		return switch (type) {
			case POSTGRES -> "org.postgresql.Driver";
			case MYSQL -> "org.mariadb.jdbc.Driver";
			case SQLITE -> "org.sqlite.JDBC";
			case H2 -> "org.h2.Driver";
		};
	}
}
