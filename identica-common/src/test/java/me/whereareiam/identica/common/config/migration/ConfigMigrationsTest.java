package me.whereareiam.identica.common.config.migration;

import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.common.config.IdenticaModule;
import me.whereareiam.identica.common.config.provider.CommandsProvider;
import me.whereareiam.identica.common.config.provider.EngineProvider;
import me.whereareiam.identica.common.config.provider.ReplicationProvider;
import me.whereareiam.identica.common.config.provider.SettingsProvider;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import me.whereareiam.strata.Strata;
import me.whereareiam.strata.adapter.configura.StrataFeature;
import me.whereareiam.strata.exception.MigrationVersionException;
import me.whereareiam.strata.model.AppliedMigration;
import me.whereareiam.strata.model.MigrationReport;
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
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Starts Identica's configuration the way the plugin does, migrations first and the documents
 * after them, on a new data directory and on one from before a version was kept.
 */
@DisplayName("Configuration Migrations")
class ConfigMigrationsTest {
	private static final String EXISTING_SETTINGS = "level: 3\nsessions:\n  activeTtl: \"1h\"\n  concurrencyPolicy: \"REJECT_NEW\"\n";
	private static final String EXISTING_ENGINE = "behavior:\n  bridgeTtl: \"1m\"\n";

	@TempDir
	Path dataPath;
	private Configura previous;

	@BeforeEach
	void rememberConfigura() {
		previous = Config.configured();
	}

	@AfterEach
	void restoreConfigura() {
		Config.setConfigured(previous);
	}

	@DisplayName("A new installation is recorded at the latest version without running a migration")
	@Test
	void newInstallationIsRecordedAtLatestVersion() throws IOException {
		MigrationReport report = start();

		assertTrue(report.isEmpty());
		assertTrue(read("settings").startsWith("_version: 9\nlevel: 2\n"), read("settings"));
		assertFalse(read("engine").contains("_version"));
	}

	@DisplayName("An installation from before versions gets the version and keeps its documents")
	@Test
	void existingInstallationKeepsItsDocuments() throws IOException {
		write("settings", EXISTING_SETTINGS);
		write("engine", EXISTING_ENGINE);

		MigrationReport report = migrate();

		assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), versions(report));
		assertEquals("_version: 9\nlevel: 3\nsessions:\n  concurrencyPolicy: \"REJECT_NEW\"\n", read("settings"));
		assertEquals(EXISTING_ENGINE, read("engine"));
		assertEquals(EXISTING_SETTINGS, Files.readString(dataPath.resolve(".strata/backup/identica.config/v1/settings.yml")));
	}

	@DisplayName("The version stays first in the settings document when Identica completes it")
	@Test
	void versionIsCarriedThroughRewrite() throws IOException {
		write("settings", EXISTING_SETTINGS);

		start();

		String settings = read("settings");
		assertTrue(settings.startsWith("_version: 9\nlevel: 3\n"), settings);
		assertTrue(settings.contains("concurrencyPolicy: \"REJECT_NEW\""), settings);
		assertTrue(settings.contains("identity:"), settings);
	}

	@DisplayName("A second start runs nothing and leaves every file untouched")
	@Test
	void secondStartLeavesFilesUntouched() throws IOException {
		write("settings", EXISTING_SETTINGS);
		write("engine", EXISTING_ENGINE);
		start();
		Map<String, String> first = snapshot();

		MigrationReport report = start();

		assertTrue(report.isEmpty());
		assertEquals(first, snapshot());
	}

	@DisplayName("A version written by a newer build refuses the start and changes nothing")
	@Test
	void newerVersionRefusesStart() throws IOException {
		write("settings", "_version: 10\nlevel: 1\n");

		assertThrows(MigrationVersionException.class, this::migrate);

		assertEquals("_version: 10\nlevel: 1\n", read("settings"));
	}

	@DisplayName("A version in a document the migrations do not use survives being rewritten")
	@Test
	void versionOfAnotherDocumentSurvives() throws IOException {
		write("engine", "_version: 4\nlegacy: true\n");

		start();

		assertTrue(read("engine").startsWith("_version: 4\nbehavior:"), read("engine"));
		assertFalse(read("engine").contains("legacy"));
	}

	@DisplayName("The session lifetime of the old layout is dropped and the replication heartbeat timeout applies with its default")
	@Test
	void sessionLifetimeIsReplacedByTheHeartbeatDefault() throws IOException {
		write("settings", EXISTING_SETTINGS);

		start();

		String settings = read("settings");
		assertFalse(settings.contains("activeTtl"), settings);
		assertFalse(settings.contains("heartbeatTimeout"), settings);
		assertEquals(Duration.ofSeconds(30), new ReplicationProvider(dataPath, registry()).get().getHeartbeatTimeout());
	}

	@DisplayName("A 2.2.1 installation with the removed session policy ALLOW_MULTIPLE starts with REPLACE_EXISTING")
	@Test
	void removedSessionPolicyBecomesReplaceExisting() throws IOException {
		write("settings", "level: 3\nconnection:\n  sessions:\n    concurrencyPolicy: \"ALLOW_MULTIPLE\"\n");

		start();

		assertTrue(read("settings").contains("concurrencyPolicy: \"REPLACE_EXISTING\""), read("settings"));
		assertEquals(SessionConcurrencyPolicy.REPLACE_EXISTING,
				new SettingsProvider(dataPath, registry()).get().getSessions().getConcurrencyPolicy());
	}

	@DisplayName("The session namespaces keep the names an operator chose under their new keys")
	@Test
	void sessionNamespacesAreRenamed() throws IOException {
		write("settings", EXISTING_SETTINGS);
		write("replication", """
				enabled: true
				serverId: "proxy-a"
				cache:
				  reservations: "network:reservation"
				  sessions:
				    user: "network:sessions:user"
				    session: "network:sessions:session"
				    subject: "network:sessions:subject"
				    servers: "network:sessions:servers"
				""");

		migrate();

		assertEquals("""
				enabled: true
				serverId: "proxy-a"
				cache:
				  reservations: "network:reservation"
				  sessions:
				    records: "network:sessions:session"
				    accounts: "network:sessions:user"
				    subjects: "network:sessions:subject"
				""", read("replication"));
		Replication.Sessions sessions = new ReplicationProvider(dataPath, registry()).get().getCache().getSessions();
		assertEquals("network:sessions:session", sessions.getRecords());
		assertEquals("network:sessions:user", sessions.getAccounts());
		assertEquals("network:sessions:subject", sessions.getSubjects());
	}

	@DisplayName("A replication document without session namespaces is left as it is")
	@Test
	void replicationWithoutSessionNamespacesIsUntouched() throws IOException {
		write("settings", EXISTING_SETTINGS);
		write("replication", "enabled: false\n");

		migrate();

		assertEquals("enabled: false\n", read("replication"));
	}

	@DisplayName("The reservation commands of an existing installation take a username and keep the rest of their usage")
	@Test
	void reservationCommandsTakeAUsername() throws IOException {
		write("settings", EXISTING_SETTINGS);
		write("commands", """
				commands:
				  admin-reservation-set:
				    usage: "{command} {alias} <key> <uniqueId>"
				    arguments:
				      key: "Reservation key"
				      uniqueId: "UUID"
				  admin-reservation-info:
				    usage: "/reserved {alias} <key>"
				    arguments:
				      key: "Who"
				  admin-reservation-delete:
				    usage: "{command} {alias} <key>"
				  help:
				    usage: "{alias} [key]"
				""");

		migrate();

		assertEquals("""
				commands:
				  admin-reservation-set:
				    usage: "{command} {alias} <username> <uniqueId>"
				    arguments:
				      uniqueId: "UUID"
				      username: "Reservation key"
				  admin-reservation-info:
				    usage: "/reserved {alias} <username>"
				    arguments:
				      username: "Who"
				  admin-reservation-delete:
				    usage: "{command} {alias} <username>"
				  help:
				    usage: "{alias} [key]"
				""", read("commands"));
	}

	@DisplayName("The commands of an existing installation start after the reservation commands are migrated")
	@Test
	void migratedCommandsLoad() throws IOException {
		write("settings", EXISTING_SETTINGS);
		write("commands", """
				commands:
				  admin-reservation-set:
				    usage: "{command} {alias} <key> <uniqueId>"
				""");

		migrate();

		assertEquals("{command} {alias} <username> <uniqueId>", new CommandsProvider(dataPath, registry()).get()
				.getCommands().get("admin-reservation-set").getUsage());
	}

	/** Runs the migrations and then loads documents, in the order the plugin does. */
	private MigrationReport start() {
		MigrationReport report = migrate();

		Registry<Reloadable> reloadables = registry();
		new SettingsProvider(dataPath, reloadables).get();
		new EngineProvider(dataPath, reloadables).get();

		return report;
	}

	private MigrationReport migrate() {
		Configura configura = Config.builder()
				.format(Format.YAML)
				.module(new IdenticaModule())
				.feature(new StrataFeature())
				.build();
		MigrationReport report = new Strata(List.of(ConfigMigrations.on(configura, dataPath))).migrate();
		Config.setConfigured(configura);

		return report;
	}

	private static List<Integer> versions(MigrationReport report) {
		return report.getApplied().getOrDefault(ConfigMigrations.ID, List.of()).stream()
				.map(AppliedMigration::getVersion)
				.toList();
	}

	/** Every file below the data directory except Strata's lock, by its path. */
	private Map<String, String> snapshot() throws IOException {
		Map<String, String> files = new TreeMap<>();
		try (Stream<Path> paths = Files.walk(dataPath)) {
			for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
				String name = dataPath.relativize(path).toString();
				if (!name.equals(".strata/lock")) files.put(name, Files.readString(path));
			}
		}

		return files;
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
