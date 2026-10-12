package me.whereareiam.identica.adapter.database.connection;

/**
 * Thrown when the configured database server is older than Identica's SQL needs. Its message tells
 * an administrator which server was found and which one is required.
 */
public final class UnsupportedDatabaseServerException extends RuntimeException {
	/**
	 * @param message the explanation shown to administrators
	 */
	public UnsupportedDatabaseServerException(String message) {
		super(message);
	}
}
