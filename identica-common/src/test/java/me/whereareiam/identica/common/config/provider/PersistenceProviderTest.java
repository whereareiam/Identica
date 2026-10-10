package me.whereareiam.identica.common.config.provider;

import me.whereareiam.configura.exception.ConfigException;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.model.config.persistence.H2Persistence;
import me.whereareiam.identica.model.config.persistence.Persistence;
import me.whereareiam.identica.model.config.persistence.SqlitePersistence;
import me.whereareiam.identica.type.DatabaseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("Persistence Provider")
class PersistenceProviderTest {
	@DisplayName("An unknown persistence type stops loading with the accepted values and leaves the file untouched")
	@ParameterizedTest
	@ValueSource(strings = {"type: \"ORACLE\"\n", "type: sqlite\n"})
	void unknownPersistenceTypeFailsWithAcceptedValues(String content, @TempDir Path tempDir) throws Exception {
		Path persistencePath = tempDir.resolve("persistence.yml");
		Files.writeString(persistencePath, content);

		ConfigException failure = assertThrows(ConfigException.class, () -> provider(tempDir).get());

		String messages = messages(failure);
		assertTrue(messages.contains("'type'"), messages);
		for (String accepted : new String[]{"SQLITE", "H2", "MYSQL", "POSTGRES"})
			assertTrue(messages.contains(accepted), messages);
		assertEquals(content, Files.readString(persistencePath));
	}

	@DisplayName("A type that is not text stops loading instead of falling back to SQLite")
	@Test
	void nonTextPersistenceTypeStopsLoading(@TempDir Path tempDir) throws Exception {
		Path persistencePath = tempDir.resolve("persistence.yml");
		Files.writeString(persistencePath, "type: 5\n");

		assertThrows(ConfigException.class, () -> provider(tempDir).get());
		assertEquals("type: 5\n", Files.readString(persistencePath));
	}

	@DisplayName("Missing persistence file is generated as SQLite")
	@Test
	void missingPersistenceFileIsGeneratedAsSqlite(@TempDir Path tempDir) throws Exception {
		Persistence persistence = provider(tempDir).get();

		assertInstanceOf(SqlitePersistence.class, persistence);
		assertTrue(Files.readString(tempDir.resolve("persistence.yml")).contains("SQLITE"));
	}

	@DisplayName("Known persistence type selects its model")
	@Test
	void knownPersistenceTypeSelectsItsModel(@TempDir Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("persistence.yml"), "type: \"H2\"\n");

		Persistence persistence = provider(tempDir).get();

		assertInstanceOf(H2Persistence.class, persistence);
		assertEquals(DatabaseType.H2, persistence.getType());
	}

	@SuppressWarnings("unchecked")
	private static PersistenceProvider provider(Path dataPath) {
		return new PersistenceProvider(dataPath, mock(Registry.class));
	}

	/**
	 * Configura reports an unknown type as the cause of the failure to bind the file.
	 */
	private static String messages(Throwable failure) {
		StringBuilder messages = new StringBuilder();
		for (Throwable cause = failure; cause != null; cause = cause.getCause())
			messages.append(cause.getMessage()).append('\n');
		return messages.toString();
	}
}
