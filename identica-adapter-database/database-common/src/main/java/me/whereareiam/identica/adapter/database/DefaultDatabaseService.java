package me.whereareiam.identica.adapter.database;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.zaxxer.hikari.HikariDataSource;
import lombok.AccessLevel;
import lombok.Getter;
import me.whereareiam.attache.LibraryManager;
import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.adapter.jdbi.DialecticaPlugin;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.config.LoggerConfig;
import me.whereareiam.identica.adapter.database.connection.DataSourceFactory;
import me.whereareiam.identica.adapter.database.connection.DatabaseServerRequirement;
import me.whereareiam.identica.adapter.database.connection.UnsupportedDatabaseServerException;
import me.whereareiam.identica.database.DatabaseService;
import me.whereareiam.identica.adapter.database.schema.AccountSchema;
import me.whereareiam.identica.adapter.database.schema.DatabaseSchemaInstaller;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.persistence.Persistence;
import me.whereareiam.identica.type.DatabaseType;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Identica's database, opened in {@link StartupPhase#PERSISTENCE}: loads the driver of the configured database,
 * opens the connection the {@code persistence} document configures, refuses a server older than {@link DatabaseServerRequirement} allows, installs the core
 * schema, and provides Jdbi, Dialectica's access to the rows of entities, and the {@link DatabaseSchemaInstaller}
 * that installs the other owners' schemas on it. The Jdbi speaks the database's Dialectica dialect, so entities and
 * the UUIDs and booleans of hand-written queries are stored the way the schema declares them. It closes the
 * connection when Identica stops.
 */
@Getter
@Singleton
public class DefaultDatabaseService implements DatabaseService, RuntimeLifecycle {
	@Getter(AccessLevel.NONE)
	private final Provider<Persistence> persistence;
	@Getter(AccessLevel.NONE)
	private final Path dataPath;
	@Getter(AccessLevel.NONE)
	private final LibraryManager libraryManager;
	private DataSource dataSource;
	private Jdbi jdbi;
	private DatabaseSchemaInstaller schemaInstaller;

	@Inject
	public DefaultDatabaseService(
			Provider<Persistence> persistence,
			@Named("dataPath") Path dataPath,
			LibraryManager libraryManager
	) {
		this.persistence = persistence;
		this.dataPath = dataPath;
		this.libraryManager = libraryManager;
	}

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.PERSISTENCE;
	}

	/**
	 * Opens the database and installs the core schema. A failure stops Identica's startup.
	 */
	@Override
	public void initialize() {
		if (jdbi != null) throw new IllegalStateException("DatabaseService has already been initialized");

		Persistence persistence = this.persistence.get();
		try {
			libraryManager.loadGroups(driverGroup(persistence.getType()));
			this.dataSource = DataSourceFactory.create(persistence, dataPath);
			DatabaseServerRequirement.verify(persistence.getType(), dataSource);

			this.jdbi = Jdbi.create(dataSource);
			jdbi.installPlugin(new SqlObjectPlugin());
			jdbi.installPlugin(new DialecticaPlugin());

			LoggerConfig.configure(jdbi);

			this.schemaInstaller = new DatabaseSchemaInstaller(jdbi);
			schemaInstaller.install(new AccountSchema());
		} catch (UnsupportedDatabaseServerException e) {
			Logger.severe("%s", e.getMessage());
			if (dataSource instanceof HikariDataSource hikariDataSource) hikariDataSource.close();
			dataSource = null;
			throw e;
		} catch (Exception e) {
			Logger.severe(e, "Failed to initialize database");
			throw new RuntimeException("Failed to initialize database", e);
		}
	}

	/**
	 * Closes the connection pool.
	 */
	@Override
	public void shutdown() {
		if (!isInitialized()) return;

		try {
			Logger.info("Shutting down database connection...");

			if (dataSource instanceof HikariDataSource hikariDataSource) {
				hikariDataSource.close();
				Logger.info("Database connection pool closed");
			}

			dataSource = null;
			jdbi = null;
			schemaInstaller = null;
		} catch (Exception e) {
			Logger.warn("Error occurred while shutting down database connection: %s", e.getMessage());
		}
	}

	@Override
	public boolean isInitialized() {
		return jdbi != null;
	}

	/**
	 * Names the Attache group that holds a database's driver, as {@code database-common} declares it.
	 */
	private static String driverGroup(DatabaseType type) {
		return type.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public <T> @NotNull EntityAccess<T> access(@NotNull Class<T> entityType) {
		if (!isInitialized())
			throw new IllegalStateException("The database is not open yet; it opens in the persistence phase of the startup");

		return EntityAccess.of(jdbi, Dialectica.entity(entityType));
	}
}
