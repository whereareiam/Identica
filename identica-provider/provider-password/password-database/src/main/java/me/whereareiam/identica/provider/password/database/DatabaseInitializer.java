package me.whereareiam.identica.provider.password.database;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.SchemaManager;
import org.jdbi.v3.core.Jdbi;

@Singleton
public class DatabaseInitializer {
	@Inject
	public DatabaseInitializer(Jdbi jdbi) {
		SchemaManager schemaManager = Dialectica.schema(jdbi)
				.scanPackages("me.whereareiam.identica.provider.password.database.entity")
				.setFailOnError(false);
		schemaManager.initialize();
	}
}
