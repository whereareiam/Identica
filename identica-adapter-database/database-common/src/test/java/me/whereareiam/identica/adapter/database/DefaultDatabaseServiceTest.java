package me.whereareiam.identica.adapter.database;

import me.whereareiam.attache.LibraryManager;
import me.whereareiam.identica.model.config.persistence.SqlitePersistence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Database service")
class DefaultDatabaseServiceTest {
	@TempDir
	Path dataPath;

	@DisplayName("Opening the database loads only the driver of the configured database")
	@Test
	void loadsTheDriverOfTheConfiguredDatabase() {
		SqlitePersistence persistence = new SqlitePersistence();
		persistence.setFile("identica.db");
		LibraryManager libraryManager = mock(LibraryManager.class);
		DefaultDatabaseService database = new DefaultDatabaseService(() -> persistence, dataPath, libraryManager);

		try {
			database.initialize();

			verify(libraryManager).loadGroups("sqlite");
		} finally {
			database.shutdown();
		}
	}
}
