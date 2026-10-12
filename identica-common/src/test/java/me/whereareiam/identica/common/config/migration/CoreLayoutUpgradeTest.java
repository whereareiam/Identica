package me.whereareiam.identica.common.config.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.common.config.provider.*;
import me.whereareiam.identica.common.config.testing.DataDirectory;
import me.whereareiam.identica.common.config.testing.EarlierConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Upgrades the configuration directories of 2.2.1 and the 3.0.0 development builds with the core's migrations
 * alone, as a directory is before any feature has collected its sections from the core's documents.
 */
@DisplayName("Core Layout Upgrade")
class CoreLayoutUpgradeTest {
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

	static Stream<Arguments> layouts() {
		List<Arguments> layouts = new ArrayList<>(List.of(
				Arguments.of(EarlierConfiguration.RELEASE_221, Format.YAML),
				Arguments.of(EarlierConfiguration.RELEASE_221_JSON, Format.JSON),
				Arguments.of(EarlierConfiguration.RELEASE_221_CUSTOMISED, Format.YAML),
				Arguments.of(EarlierConfiguration.RELEASE_221_CUSTOMISED_JSON, Format.JSON)
		));
		EarlierConfiguration.DEVELOPMENT_BUILDS.forEach(build -> layouts.add(Arguments.of(build, Format.YAML)));

		return layouts.stream();
	}

	static Stream<Arguments> defaults() {
		return Stream.of(
				Arguments.of(EarlierConfiguration.RELEASE_221, Format.YAML),
				Arguments.of(EarlierConfiguration.RELEASE_221_JSON, Format.JSON)
		);
	}

	@DisplayName("The core leaves the sections the features collect where they are")
	@ParameterizedTest(name = "{0}")
	@MethodSource("defaults")
	void featureSectionsStayForTheFeatures(String layout, Format format) {
		DataDirectory directory = copy(layout, format);

		directory.migrate();

		assertTrue(directory.at("settings", "/connection/sentinels/resumeSpam").isObject());
		assertTrue(directory.at("settings", "/connection/sessions/recognition").isObject());
		assertTrue(directory.at("replication", "/cache/sentinels").isTextual());
		assertTrue(directory.at("messages", "/connection/resumeSentineled").isArray());
		assertTrue(directory.at("messages", "/commands/verification").isObject());
		assertTrue(directory.at("commands", "/commands/admin-verification-reset").isObject());
		assertTrue(directory.exists("verification"));
		assertTrue(directory.at("settings", "/connection/attemptTtl").isMissingNode());
	}

	@DisplayName("Identica loads every core document while a feature's section is still in it")
	@ParameterizedTest(name = "{0}")
	@MethodSource("layouts")
	void loadsWithFeatureSectionsLeft(String layout, Format format) {
		DataDirectory directory = copy(layout, format);
		directory.migrate();

		assertDoesNotThrow(() -> load(directory));
	}

	@DisplayName("The defaults the provider entries are compared with are those 2.2.1 wrote")
	@ParameterizedTest(name = "{0}")
	@MethodSource("defaults")
	void comparedDefaultsAreThoseOf221(String layout, Format format) {
		DataDirectory directory = copy(layout, format);

		for (JsonNode entry : directory.tree("providers/providers").get("providers")) {
			ObjectNode written = Config.json().mapper().createObjectNode();
			written.putObject("recognition")
					.<ObjectNode>set("allowOnUntrustedIps", entry.at("/overrides/allowRecognitionOnUntrustedIp"))
					.set("signals", entry.at("/overrides/recognition/signals"));
			written.set("verification", entry.get("verification"));

			String id = entry.path("id").asText();
			assertEquals(ProviderFeaturesMigration.RELEASE_221_DEFAULTS, written, id);
			assertEquals(List.of("allowRecognitionOnUntrustedIp", "recognition"), fieldNames((ObjectNode) entry.get("overrides")), id);
		}
	}

	private DataDirectory copy(String layout, Format format) {
		EarlierConfiguration.copy(layout, dataPath);
		return new DataDirectory(dataPath, format);
	}

	@SuppressWarnings("unchecked")
	private static void load(DataDirectory directory) {
		Config.setConfigured(directory.getConfigura());
		Registry<Reloadable> registry = mock(Registry.class);
		Path data = directory.getPath();
		Path providers = data.resolve("providers");
		Path username = data.resolve("identity/username");

		new SettingsProvider(data, registry).get();
		new EngineProvider(data, registry).get();
		new RoutingProvider(data, registry).get();
		new MessagesProvider(data, registry).get();
		new CommandsProvider(data, registry).get();
		new PersistenceProvider(data, registry).get();
		new ReplicationProvider(data, registry).get();
		new ProvidersProvider(providers, registry).get();
		new ConflictsProvider(providers, registry).get();
		new UsernameConflictsProvider(username, registry).get();
		new UsernameMessagesProvider(username, registry).get();
	}

	private static List<String> fieldNames(ObjectNode object) {
		List<String> names = new ArrayList<>();
		object.fieldNames().forEachRemaining(names::add);

		return names;
	}
}
