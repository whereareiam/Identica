package me.whereareiam.identica.common.config.provider;

import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.common.config.IdenticaModule;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.config.persistence.H2Persistence;
import me.whereareiam.identica.model.config.persistence.Persistence;
import me.whereareiam.identica.model.config.persistence.SqlitePersistence;
import me.whereareiam.identica.model.config.persistence.external.MysqlPersistence;
import me.whereareiam.identica.model.config.persistence.external.PostgresPersistence;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import me.whereareiam.strata.adapter.configura.StrataFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Loads core documents through their providers with the Configura instance the plugin builds.
 */
@DisplayName("Configuration Documents")
class ConfigDocumentsTest {
	@TempDir
	Path dataPath;
	private Configura previous;

	@BeforeEach
	void useIdenticaConfigura() {
		previous = Config.configured();
		Config.setConfigured(Config.builder()
				.format(Format.YAML)
				.module(new IdenticaModule())
				.feature(new StrataFeature())
				.build());
	}

	@AfterEach
	void restoreConfigura() {
		Config.setConfigured(previous);
	}

	@DisplayName("A user's document keeps its values and is completed from the defaults")
	@Test
	void userDocumentIsCompletedFromDefaults() throws IOException {
		write("settings", """
				level: 0
				sessions:
				  heartbeatTimeout: "1m"
				""");

		Settings settings = new SettingsProvider(dataPath, registry()).get();

		assertEquals(0, settings.getLevel());
		assertEquals(Duration.ofMinutes(1), settings.getSessions().getHeartbeatTimeout());
		assertEquals(SessionConcurrencyPolicy.REPLACE_EXISTING, settings.getSessions().getConcurrencyPolicy());
		assertEquals(Duration.ofMinutes(15), settings.getIdentity().getReservationTtl());
		String written = read("settings");
		assertTrue(written.startsWith("level: 0\nsessions:\n  heartbeatTimeout: \"1m\"\n  concurrencyPolicy: \"REPLACE_EXISTING\"\n"), written);
		assertTrue(written.contains("reservationTtl: \"15m\""), written);
	}

	@DisplayName("A provider entry keeps what the user wrote and gets its defaults; others are not added")
	@Test
	void providerEntriesAreMergedById() throws IOException {
		Files.createDirectories(dataPath.resolve("providers"));
		Files.writeString(dataPath.resolve("providers/providers.yml"), """
				providers:
				  - id: "credential"
				    priority: 7
				  - id: "ldap"
				    customKey: "kept"
				""");

		Providers providers = new ProvidersProvider(dataPath.resolve("providers"), registry()).get();

		assertEquals(List.of("credential", "ldap"), providers.getProviders().stream().map(Providers.ProviderEntry::getId).toList());
		Providers.ProviderEntry credential = providers.getProviders().get(0);
		assertEquals(7, credential.getPriority());
		assertTrue(credential.isEnabled());
		assertEquals(List.of("credential.arcadeya.com"), credential.getEntrypoints());
		assertNull(credential.getSession());
		String written = Files.readString(dataPath.resolve("providers/providers.yml"));
		assertTrue(written.contains("customKey: \"kept\""), written);
		assertFalse(written.contains("session:"), written);
	}

	@DisplayName("An empty list a user writes is kept instead of the default list")
	@Test
	void explicitEmptyListIsKept() throws IOException {
		write("messages", """
				commands:
				  help:
				    format: []
				""");

		Messages messages = new MessagesProvider(dataPath, registry()).get();

		assertEquals(List.of(), messages.getCommands().getHelp().getFormat());
		assertTrue(read("messages").contains("format: []"));
	}

	@DisplayName("Persistence binds the model its type names and completes that model")
	@Test
	void persistenceBindsTheModelItsTypeNames() throws IOException {
		assertInstanceOf(SqlitePersistence.class, new PersistenceProvider(dataPath, registry()).get());
		assertTrue(read("persistence").startsWith("type: \"SQLITE\"\n"), read("persistence"));

		Map<String, Class<? extends Persistence>> types = Map.of(
				"SQLITE", SqlitePersistence.class,
				"H2", H2Persistence.class,
				"MYSQL", MysqlPersistence.class,
				"POSTGRES", PostgresPersistence.class
		);
		for (Map.Entry<String, Class<? extends Persistence>> type : types.entrySet()) {
			write("persistence", "type: \"" + type.getKey() + "\"\n");

			assertInstanceOf(type.getValue(), new PersistenceProvider(dataPath, registry()).get());
			assertTrue(read("persistence").startsWith("type: \"" + type.getKey() + "\"\nhikari:"), read("persistence"));
		}

		write("persistence", "type: \"POSTGRES\"\nhost: \"db.example.com\"\n");
		PostgresPersistence postgres = (PostgresPersistence) new PersistenceProvider(dataPath, registry()).get();
		assertEquals("db.example.com", postgres.getHost());
		assertEquals(5432, postgres.getPort());
	}

	@DisplayName("Keys no model declares are dropped, except the version")
	@Test
	void undeclaredKeysAreDropped() throws IOException {
		write("settings", "legacy: true\n_version: 3\nlevel: 1\n");

		Settings settings = new SettingsProvider(dataPath, registry()).get();

		assertEquals(1, settings.getLevel());
		String written = read("settings");
		assertTrue(written.startsWith("_version: 3\nlevel: 1\n"), written);
		assertFalse(written.contains("legacy"), written);
	}

	@DisplayName("Reload reads what the user changed in the meantime")
	@Test
	void reloadReadsChangedDocument() throws IOException {
		EngineProvider provider = new EngineProvider(dataPath, registry());
		Engine engine = provider.get();
		assertEquals(Duration.ofMinutes(10), engine.getBehavior().getBridgeTtl());

		write("engine", read("engine").replace("bridgeTtl: \"10m\"", "bridgeTtl: \"1m\""));
		assertSame(engine, provider.get());

		provider.reload();

		assertEquals(Duration.ofMinutes(1), provider.get().getBehavior().getBridgeTtl());
		assertEquals(Duration.ofMinutes(10), provider.get().getBehavior().getHandshakeInstructionTtl());
	}

	@DisplayName("The core command document carries behavior next to its commands")
	@Test
	void coreCommandsCarryBehavior() throws IOException {
		assertNotNull(new CommandsProvider(dataPath, registry()).get().getBehavior().getHelp());

		String written = read("commands");
		assertTrue(written.startsWith("commands:\n"), written);
		assertTrue(written.contains("\nbehavior:\n"), written);
	}

	private String read(String document) throws IOException {
		return Files.readString(dataPath.resolve(document + ".yml"));
	}

	private void write(String document, String content) throws IOException {
		Files.writeString(dataPath.resolve(document + ".yml"), content);
	}

	@SuppressWarnings("unchecked")
	private static Registry<Reloadable> registry() {
		return mock(Registry.class);
	}
}
