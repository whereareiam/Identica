package me.whereareiam.identica.adapter.database.connection;

import me.whereareiam.identica.model.config.persistence.H2Persistence;
import me.whereareiam.identica.model.config.persistence.Persistence;
import me.whereareiam.identica.model.config.persistence.SqlitePersistence;
import me.whereareiam.identica.model.config.persistence.external.ExternalPersistence;

import java.nio.file.Path;

/**
 * Factory for creating JDBC URLs from persistence configuration.
 */
public final class JdbcUrlFactory {
	/**
	 * Creates a JDBC URL from persistence configuration.
	 *
	 * @param persistence the persistence configuration
	 * @param dataPath    base path for relative database files
	 * @return the JDBC URL string
	 */
	public static String create(Persistence persistence, Path dataPath) {
		return switch (persistence.getType()) {
			case SQLITE -> createSqliteUrl(persistence, dataPath);
			case H2 -> createH2Url(persistence, dataPath);
			case POSTGRES -> createExternalUrl(persistence, "jdbc:postgresql://%s/%s");
			case MYSQL -> createExternalUrl(persistence, "jdbc:mariadb://%s/%s");
		};
	}

	private static String createExternalUrl(Persistence persistence, String format) {
		if (!(persistence instanceof ExternalPersistence external))
			throw new IllegalArgumentException("Expected external persistence for " + persistence.getType());

		String host = external.getHost() + ":" + external.getPort();
		String database = external.getDatabase();

		return String.format(format, host, database);
	}

	private static String createSqliteUrl(Persistence persistence, Path dataPath) {
		if (!(persistence instanceof SqlitePersistence sqlite))
			throw new IllegalArgumentException("Expected sqlite persistence for " + persistence.getType());

		String file = sqlite.getFile();
		if (file == null || file.isBlank())
			file = "identica.db";

		return "jdbc:sqlite:" + dataPath.resolve(file);
	}

	/**
	 * A file inside the data directory is given to H2 as an absolute path, because H2 refuses a file path that is
	 * relative to the working directory and platforms hand plugins a relative data directory.
	 */
	private static String createH2Url(Persistence persistence, Path dataPath) {
		if (!(persistence instanceof H2Persistence h2))
			throw new IllegalArgumentException("Expected H2 persistence for " + persistence.getType());

		String file = h2.getFile();
		String baseUrl = file.startsWith("jdbc:h2:")
				? file
				: isDirectH2Target(file)
				? "jdbc:h2:" + file
				: "jdbc:h2:file:" + dataPath.resolve(file).toAbsolutePath();

		String options = h2.getOptions();
		if (options.isBlank()) return baseUrl;

		return baseUrl + (options.startsWith(";") ? "" : ";") + options;
	}

	private static boolean isDirectH2Target(String file) {
		return file.startsWith("mem:") ||
				file.startsWith("tcp:") ||
				file.startsWith("ssl:") ||
				file.startsWith("file:");
	}
}
