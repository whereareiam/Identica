package me.whereareiam.identica.adapter.database;

import me.whereareiam.attache.LibraryManager;
import me.whereareiam.identica.database.schema.AccountTable;
import me.whereareiam.identica.model.config.persistence.H2Persistence;
import me.whereareiam.identica.model.config.persistence.SqlitePersistence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

	@DisplayName("Opens the default H2 file inside a data directory relative to the working directory")
	@Test
	void opensTheDefaultH2FileInARelativeDataDirectory() throws IOException {
		// Platforms hand the plugin its data directory relative to the working directory, such as plugins/identica.
		Path relativeDataPath = Files.createTempDirectory(Path.of("build"), "h2-data-");
		DefaultDatabaseService database = new DefaultDatabaseService(H2Persistence::new, relativeDataPath, mock(LibraryManager.class));

		try {
			database.initialize();

			Long accounts = database.getJdbi().withHandle(handle -> handle.createQuery("SELECT COUNT(*) FROM " + AccountTable.NAME)
					.mapTo(Long.class)
					.one());
			assertEquals(0L, accounts);
			assertTrue(Files.exists(relativeDataPath.resolve("identica.mv.db")), "The database file must be in the data directory");
		} finally {
			database.shutdown();
			delete(relativeDataPath);
		}
	}

	private static void delete(Path directory) throws IOException {
		try (Stream<Path> files = Files.walk(directory)) {
			for (Path file : files.sorted(Comparator.reverseOrder()).toList())
				Files.delete(file);
		}
	}
}
