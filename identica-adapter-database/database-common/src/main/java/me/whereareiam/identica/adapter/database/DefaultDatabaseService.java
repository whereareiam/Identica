package me.whereareiam.identica.adapter.database;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import me.whereareiam.dialectica.adapter.jdbi.DialecticaPlugin;
import me.whereareiam.identica.adapter.database.config.LoggerConfig;
import me.whereareiam.identica.adapter.database.connection.DataSourceFactory;
import me.whereareiam.identica.adapter.database.connection.DatabaseServerRequirement;
import me.whereareiam.identica.adapter.database.connection.UnsupportedDatabaseServerException;
import me.whereareiam.identica.database.DatabaseService;
import me.whereareiam.identica.adapter.database.schema.AccountSchema;
import me.whereareiam.identica.adapter.database.schema.DatabaseSchemaInstaller;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.persistence.Persistence;
import me.whereareiam.identica.type.event.EventOrder;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import javax.sql.DataSource;
import java.nio.file.Path;

/**
 * Default implementation of DatabaseService.
 * Opens the database, refuses a server older than {@link DatabaseServerRequirement} allows, installs
 * the core schema, and provides access to Jdbi and the {@link DatabaseSchemaInstaller} that installs
 * the other owners' schemas on it. The Jdbi speaks the database's Dialectica dialect, so entities and
 * the UUIDs and booleans of hand-written queries are stored the way the schema declares them.
 */
@Getter
@Singleton
public class DefaultDatabaseService implements DatabaseService, EventListener {
	private final Persistence persistence;
	private DataSource dataSource;
	private Jdbi jdbi;
	private DatabaseSchemaInstaller schemaInstaller;

	@Inject
	public DefaultDatabaseService(
			Persistence persistence,
			EventManager eventManager,
			@Named("dataPath") Path dataPath
	) {
		this.persistence = persistence;

		eventManager.register(this);
		initialize(dataPath);
	}

	public void initialize(Path dataPath) {
		if (jdbi != null) throw new IllegalStateException("DatabaseService has already been initialized");

		try {
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

	@IdenticEvent(EventOrder.HIGH)
	public void onShutdown(IdenticaShutdownEvent event) {
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
}
