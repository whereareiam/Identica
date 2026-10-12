package me.whereareiam.identica.feature.config.migration;

import me.whereareiam.identica.common.config.testing.DataDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.common.config.migration.ConfigMigrations;
import me.whereareiam.identica.common.config.provider.*;
import me.whereareiam.identica.common.config.testing.DataDirectory;
import me.whereareiam.identica.feature.recognition.config.migration.RecognitionConfigMigrations;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionSettingsProvider;
import me.whereareiam.identica.feature.restriction.config.migration.RestrictionConfigMigrations;
import me.whereareiam.identica.feature.restriction.config.provider.RestrictionSettingsProvider;
import me.whereareiam.identica.feature.restriction.join.config.migration.JoinRestrictionConfigMigrations;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionCommandsProvider;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionMessagesProvider;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionSettingsProvider;
import me.whereareiam.identica.feature.sentinel.config.migration.SentinelConfigMigrations;
import me.whereareiam.identica.feature.sentinel.config.provider.SentinelMessagesProvider;
import me.whereareiam.identica.feature.sentinel.config.provider.SentinelSettingsProvider;
import me.whereareiam.identica.feature.verification.config.migration.VerificationConfigMigrations;
import me.whereareiam.identica.feature.verification.config.provider.VerificationCommandsProvider;
import me.whereareiam.identica.feature.verification.config.provider.VerificationMessagesProvider;
import me.whereareiam.identica.feature.verification.config.provider.VerificationSettingsProvider;
import me.whereareiam.identica.common.config.testing.EarlierConfiguration;
import me.whereareiam.strata.adapter.configura.StrataFeature;
import me.whereareiam.strata.model.AppliedMigration;
import me.whereareiam.strata.model.MigrationReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Upgrades the configuration directories that 2.2.1 and the 3.0.0 development builds left, captured from those
 * versions, with the configuration migrations of the core and of every compiled feature in one run, as Identica
 * starts, and checks that they end in the current layout with every value an operator could have set.
 */
@DisplayName("Earlier Configuration Upgrade")
class EarlierConfigurationUpgradeTest {
	/** The layout of the newest development builds, which is the one this build reads. */
	private static final String CURRENT = "development-build-898aac4b";

	/** Places an entry of a list may take that the current defaults leave empty. */
	private static final List<Pattern> OPTIONAL = List.of(
			Pattern.compile("^providers/providers:/providers/\\*/(features|session)/.*"),
			Pattern.compile("^identity/username/conflicts:/rules/username/cases/\\*/.*"),
			Pattern.compile("^routing:/scenarios/.*")
	);

	/** Values that have no counterpart in the current layout, and that the migrations drop. */
	private static final List<Pattern> DROPPED = List.of(
			Pattern.compile(".*:/_version$"),
			Pattern.compile("^settings:/(connection/)?sessions/activeTtl$"),
			Pattern.compile(".*/scenarios/\\w+/advanceLockTtl$"),
			Pattern.compile("^messages:/(connection|scenarios)/\\w+/advanceBusy/.*"),
			Pattern.compile("^messages:/(connection|scenarios)/(registration|migration)/errors/identity/replicationMissing$"),
			Pattern.compile("^messages:/(connection|engine)/prepare/providerRestricted$"),
			Pattern.compile(".*/conflicts?(:/rules)?/\\w+/default/providers$")
	);

	/**
	 * The June builds that wrote some settings, messages and commands both into the document of their feature and
	 * into the core document that had held them before.
	 */
	private static final List<String> TWO_HOMES = List.of("development-build-01c65c0e", "development-build-dae9c581");

	/** The configuration migrations of every compiled feature. */
	private static final List<DataDirectory.Owner> FEATURES = List.of(
			RecognitionConfigMigrations::on,
			RestrictionConfigMigrations::on,
			JoinRestrictionConfigMigrations::on,
			SentinelConfigMigrations::on,
			VerificationConfigMigrations::on
	);

	/** The streams of those migrations with the document each keeps its version in. */
	private static final Map<String, String> FEATURE_VERSIONS = Map.of(
			RecognitionConfigMigrations.ID, "features/recognition/settings",
			RestrictionConfigMigrations.ID, "features/restriction/settings",
			JoinRestrictionConfigMigrations.ID, "features/restriction/join/settings",
			SentinelConfigMigrations.ID, "features/sentinel/settings",
			VerificationConfigMigrations.ID, "features/verification/settings"
	);

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

	/** The layouts that kept every value in one place; two June builds kept some of them in two. */
	static Stream<Arguments> singleHomeLayouts() {
		return layouts().filter(arguments -> !TWO_HOMES.contains((String) arguments.get()[0]));
	}

	static Stream<Arguments> twoHomeLayouts() {
		return TWO_HOMES.stream().map(layout -> Arguments.of(layout, Format.YAML));
	}

	static Stream<Arguments> customised() {
		return Stream.of(
				Arguments.of(EarlierConfiguration.RELEASE_221_CUSTOMISED, Format.YAML),
				Arguments.of(EarlierConfiguration.RELEASE_221_CUSTOMISED_JSON, Format.JSON)
		);
	}

	static Stream<Arguments> defaults() {
		return Stream.of(
				Arguments.of(EarlierConfiguration.RELEASE_221, Format.YAML),
				Arguments.of(EarlierConfiguration.RELEASE_221_JSON, Format.JSON)
		);
	}

	@DisplayName("Every migration the directory has not seen runs, in order")
	@ParameterizedTest(name = "{0}")
	@MethodSource("layouts")
	void runsTheMigrationsTheDirectoryHasNotSeen(String layout, Format format) {
		DataDirectory directory = copy(layout, format);
		int core = directory.tree("settings").path(StrataFeature.VERSION_KEY).asInt();

		MigrationReport report = directory.migrate(FEATURES);

		assertEquals(IntStream.rangeClosed(core + 1, 7).boxed().toList(), versions(report, ConfigMigrations.ID));
		assertEquals(7, directory.tree("settings").path(StrataFeature.VERSION_KEY).asInt());
		FEATURE_VERSIONS.forEach((stream, document) ->
				assertEquals(2, directory.tree(document).path(StrataFeature.VERSION_KEY).asInt(), stream));
	}

	@DisplayName("Nothing is left where the current layout has no place for it")
	@ParameterizedTest(name = "{0}")
	@MethodSource("layouts")
	void leavesNothingOutsideTheCurrentLayout(String layout, Format format) {
		Set<String> current = normalised(copy(CURRENT, Format.YAML, dataPath.resolve("current")).values().keySet());
		Set<String> versions = FEATURE_VERSIONS.values().stream()
				.map(document -> document + ":/" + StrataFeature.VERSION_KEY)
				.collect(Collectors.toSet());
		DataDirectory directory = copy(layout, format);

		directory.migrate(FEATURES);

		List<String> outside = normalised(directory.values().keySet()).stream()
				.filter(path -> !current.contains(path))
				.filter(path -> OPTIONAL.stream().noneMatch(pattern -> pattern.matcher(path).matches()))
				// A document the migrations emptied, which Identica fills with its defaults when it reads it.
				.filter(path -> !path.endsWith(":"))
				.filter(path -> !versions.contains(path))
				.toList();
		assertEquals(List.of(), outside);
		assertFalse(java.nio.file.Files.exists(directory.getPath().resolve("providers/capabilities")), "providers/capabilities is left");
	}

	@DisplayName("Every text and list a document held is still in one, unless it has no counterpart")
	@ParameterizedTest(name = "{0}")
	@MethodSource("singleHomeLayouts")
	void keepsEveryTextAndList(String layout, Format format) {
		DataDirectory directory = copy(layout, format);
		Map<JsonNode, Long> before = distinctive(kept(directory));

		directory.migrate(FEATURES);

		Map<JsonNode, Long> after = distinctive(directory.values());
		List<String> lost = before.entrySet().stream()
				.filter(entry -> after.getOrDefault(entry.getKey(), 0L) < entry.getValue())
				.map(entry -> entry.getKey() + " x" + entry.getValue())
				.toList();
		assertEquals(List.of(), lost);
	}

	@DisplayName("A June build's setting kept in two places is taken from the feature's document")
	@ParameterizedTest(name = "{0}")
	@MethodSource("twoHomeLayouts")
	void featureDocumentWinsOverAnOlderPlace(String layout, Format format) {
		DataDirectory directory = copy(layout, format);
		directory.write("settings", directory.files().get("settings.yml").replace("maxAttempts: 10", "maxAttempts: 4"));
		JsonNode sentinel = directory.at("features/sentinel/settings", "/sentinels/resumeSpam");
		JsonNode verification = directory.at("features/verification/commands", "/commands/admin-verification-reset");
		JsonNode username = directory.at("providers/capabilities/authoritative-username/messages", "/pipeline");

		directory.migrate(FEATURES);

		assertEquals(sentinel, directory.at("features/sentinel/settings", "/sentinels/resumeSpam"));
		assertEquals(verification, directory.at("features/verification/commands", "/commands/admin-verification-reset"));
		assertEquals(username, directory.at("identity/username/messages", "/pipeline"));
		assertTrue(directory.at("settings", "/sentinels").isMissingNode());
		assertTrue(directory.at("commands", "/commands/admin-verification-reset").isMissingNode());
		assertFalse(directory.exists("providers/capabilities/authoritative-username/messages"));
	}

	@DisplayName("Running every migration again changes nothing")
	@ParameterizedTest(name = "{0}")
	@MethodSource("layouts")
	void runningAgainChangesNothing(String layout, Format format) {
		DataDirectory directory = copy(layout, format);
		directory.migrate(FEATURES);
		Map<String, String> migrated = directory.files();

		List<String> versionDocuments = new ArrayList<>(List.of("settings"));
		versionDocuments.addAll(FEATURE_VERSIONS.values());
		for (String document : versionDocuments) {
			ObjectNode tree = directory.tree(document);
			tree.put(StrataFeature.VERSION_KEY, 1);
			directory.write(document, new String(directory.getConfigura().writeNodeBytes(tree), StandardCharsets.UTF_8));
		}
		MigrationReport again = directory.migrate(FEATURES);

		assertEquals(IntStream.rangeClosed(2, 7).boxed().toList(), versions(again, ConfigMigrations.ID));
		for (String stream : FEATURE_VERSIONS.keySet())
			assertEquals(List.of(2), versions(again, stream), stream);
		assertEquals(migrated, directory.files());
	}

	@DisplayName("A switched-off feature still collects its values")
	@ParameterizedTest(name = "{0}")
	@MethodSource("customised")
	void switchedOffFeatureCollectsItsValues(String layout, Format format) {
		DataDirectory directory = copy(layout, format);
		for (String feature : List.of("features/sentinel/settings", "features/recognition/settings"))
			directory.write(feature, format == Format.YAML ? "enabled: false\n" : "{\"enabled\": false}");

		directory.migrate(FEATURES);

		assertFalse(directory.at("features/sentinel/settings", "/enabled").asBoolean(true));
		assertEquals(5, directory.at("features/sentinel/settings", "/sentinels/resumeSpam/maxAttempts").asInt());
		assertFalse(directory.at("features/recognition/settings", "/enabled").asBoolean(true));
		assertEquals("6h", directory.at("features/recognition/settings", "/validity").asText());
		assertTrue(directory.at("settings", "/connection").isMissingNode());
	}

	@DisplayName("Identica loads every core and feature document of the upgraded directory")
	@ParameterizedTest(name = "{0}")
	@MethodSource("layouts")
	void loadsEveryCoreDocument(String layout, Format format) {
		DataDirectory directory = copy(layout, format);
		directory.migrate(FEATURES);

		assertDoesNotThrow(() -> load(directory));
	}

	@DisplayName("A customised 2.2.1 directory keeps its settings in their new places")
	@ParameterizedTest(name = "{0}")
	@MethodSource("customised")
	void customisedSettingsReachTheirNewPlaces(String layout, Format format) {
		DataDirectory directory = copy(layout, format);
		directory.migrate(FEATURES);

		Map<String, String> expected = new LinkedHashMap<>();
		expected.put("settings:/level", "3");
		expected.put("settings:/identity/reservationTtl", "\"20m\"");
		expected.put("settings:/identity/uniqueIdMode", "\"OFFLINE\"");
		expected.put("settings:/sessions/concurrencyPolicy", "\"REJECT_NEW\"");
		expected.put("settings:/listeners/events/com.velocitypowered.api.event.player.ServerPostConnectEvent/priority", "\"LOW\"");
		expected.put("engine:/behavior/handshakeInstructionTtl", "\"7m\"");
		expected.put("engine:/behavior/bridgeTtl", "\"9m\"");
		expected.put("engine:/scenarios/authentication/journeyMode", "\"INTERACTIVE\"");
		expected.put("engine:/scenarios/registration/autoSelectSingleProvider", "true");
		expected.put("engine:/scenarios/migration/journeyPolicy", "\"STRICT\"");
		expected.put("routing:/defaults/complete/target", "\"lobby\"");
		expected.put("routing:/defaults/step/attempts/maxAttempts", "3");
		expected.put("providers/providers:/behavior/attemptTtl", "\"8m\"");
		expected.put("features/recognition/settings:/enabled", "true");
		expected.put("features/recognition/settings:/defaultSignals", "[\"USERNAME\",\"IP\"]");
		expected.put("features/recognition/settings:/eligibility/untrustedIps/entries", "[\"10.0.0.0/8\"]");
		expected.put("features/recognition/settings:/replication/snapshotNamespace", "\"network:session-recognition:snapshot\"");
		expected.put("features/sentinel/settings:/sentinels/resumeSpam/maxAttempts", "5");
		expected.put("features/sentinel/settings:/sentinels/resumeSpam/warning/thresholdPercentage", "50");
		expected.put("features/sentinel/settings:/replication/state", "\"network:sentinels\"");
		expected.put("features/verification/settings:/totp/digits", "8");
		expected.put("features/verification/settings:/enrollmentTtl", "\"15m\"");
		expected.put("replication:/cache/sessions/records", "\"network:sessions:session\"");
		expected.put("persistence:/hikari/poolName", "\"Upgraded\"");
		expected.put("features/verification/commands:/commands/verification/aliases", "[\"2fa\",\"twofactor\"]");
		expected.put("identity/username/conflicts:/rules/username/cases/0/when/providers", "[\"premium\",\"credential\"]");
		expected.put("identity/username/conflicts:/rules/username/cases/0/force", "true");
		expected.put("identity/username/conflicts:/rules/username/cases/1/resolvers/0/parameters/target", "\"existing\"");
		expected.put("identity/username/conflicts:/rules/username/default/when", "{}");
		expected.put("identity/username/conflicts:/rules/username/default/resolvers/0/parameters/format/pattern", "\"{username}+\"");

		Map<String, String> actual = new LinkedHashMap<>();
		expected.keySet().forEach(path -> actual.put(path, value(directory, path).toString()));
		assertEquals(expected, actual);
	}

	@DisplayName("A customised 2.2.1 directory keeps its messages in their new places")
	@ParameterizedTest(name = "{0}")
	@MethodSource("customised")
	void customisedMessagesReachTheirNewPlaces(String layout, Format format) {
		DataDirectory directory = copy(layout, format);
		ObjectNode messages = directory.tree("messages");
		ObjectNode connection = (ObjectNode) messages.get("connection");
		directory.migrate(FEATURES);

		assertEquals(connection.at("/authentication/routing/missingServer"), value(directory, "messages:/scenarios/authentication/routing/missingServer"));
		assertEquals(connection.at("/registration/routing/missingServer"), value(directory, "messages:/scenarios/registration/routing/missingServer"));
		assertEquals(connection.at("/migration/pipelineKick"), value(directory, "messages:/scenarios/migration/pipelineKick"));
		assertEquals(connection.at("/journey/step/enrollment/body"), value(directory, "messages:/engine/journey/step/enrollment/body"));
		assertEquals(connection.at("/resumeSentineled"), value(directory, "features/sentinel/messages:/resumeSpam/denied"));
		assertEquals(connection.at("/authentication/errors/identity/replicationMissing"), value(directory, "identity/username/messages:/pipeline/identity/synchronizationFailed"));
		assertEquals(connection.at("/authentication/conflictEntrypointRequired"), value(directory, "identity/username/messages:/pipeline/policy/entrypointRequired"));
		assertEquals(messages.at("/commands/verification/status/body"), value(directory, "features/verification/messages:/commands/status/body"));
		assertEquals(messages.at("/commands/verification/methods/totp/pending"), value(directory, "features/verification/messages:/methods/totp/pending"));
		assertEquals(messages.at("/commands/admin/verification/reset/completed"), value(directory, "features/verification/messages:/commands/admin/reset/completed"));
		assertEquals(messages.at("/commands/admin/sessions/info/body"), value(directory, "messages:/commands/admin/sessions/status/body"));
	}

	@DisplayName("A provider entry keeps what an operator changed and takes the current defaults for the rest")
	@ParameterizedTest(name = "{0}")
	@MethodSource("customised")
	void changedProviderSettingsAreKept(String layout, Format format) {
		DataDirectory directory = copy(layout, format);
		directory.migrate(FEATURES);

		ObjectNode credential = provider(directory, "credential");
		assertEquals(json("{\"recognition\":{\"allowOnUntrustedIps\":true,\"enabled\":false,\"signals\":[\"IP\"]},"
				+ "\"verification\":{\"unavailableSelectionPolicy\":\"CLEAR_SELECTION\","
				+ "\"methods\":[{\"id\":\"totp\",\"enabled\":true,\"priority\":90,\"unavailableSelectionPolicy\":\"KEEP_LOCKED\"}]}}"),
				credential.get("features"));
		assertEquals(json("{\"concurrencyPolicy\":\"ALLOW_MULTIPLE\"}"), credential.get("session"));

		ObjectNode premium = provider(directory, "premium");
		assertEquals(json("{\"verification\":{\"enabled\":false,\"required\":true}}"), premium.get("features"));
		assertNull(premium.get("session"));
	}

	@DisplayName("A provider entry 2.2.1 wrote with its defaults takes the current defaults")
	@ParameterizedTest(name = "{0}")
	@MethodSource("defaults")
	void defaultProviderSettingsTakeTheCurrentDefaults(String layout, Format format) {
		DataDirectory directory = copy(layout, format);
		directory.migrate(FEATURES);

		for (String id : List.of("credential", "premium")) {
			ObjectNode entry = provider(directory, id);
			assertEquals(List.of("id", "displayName", "enabled", "priority", "entrypoints"), fieldNames(entry), id);
		}
	}

	private DataDirectory copy(String layout, Format format) {
		return copy(layout, format, dataPath.resolve("data"));
	}

	private static DataDirectory copy(String layout, Format format, Path path) {
		EarlierConfiguration.copy(layout, path);
		return new DataDirectory(path, format);
	}

	private static ObjectNode provider(DataDirectory directory, String id) {
		for (JsonNode entry : directory.tree("providers/providers").get("providers")) {
			if (entry.path("id").asText().equals(id)) return (ObjectNode) entry;
		}

		throw new AssertionError("No provider entry " + id);
	}

	private static JsonNode value(DataDirectory directory, String path) {
		int separator = path.indexOf(':');
		return directory.at(path.substring(0, separator), path.substring(separator + 1));
	}

	private static ObjectNode json(String content) {
		try {
			return (ObjectNode) Config.json().mapper().readTree(content);
		} catch (Exception failure) {
			throw new IllegalStateException(failure);
		}
	}

	/** Paths with the entries of a list written as {@code *}, so that lists of any length compare. */
	private static Set<String> normalised(Collection<String> paths) {
		return paths.stream().map(path -> path.replaceAll("/\\d+(?=/|$)", "/*")).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns the values of a directory before its upgrade, without those the migrations drop on purpose: values
	 * with no counterpart, the usages {@link ReservationUsernameMigration} rewrites, and what 2.2.1 wrote into the
	 * entries of {@code credential} and {@code premium} by default, which the core's migrations drop.
	 */
	private Map<String, JsonNode> kept(DataDirectory directory) {
		Map<String, JsonNode> values = new TreeMap<>(directory.values());
		values.keySet().removeIf(path -> DROPPED.stream().anyMatch(pattern -> pattern.matcher(path).matches()));
		values.keySet().removeIf(path -> path.matches("^commands:/commands/admin-reservation-\\w+/usage$"));

		JsonNode entries = directory.at("providers/providers", "/providers");
		for (int index = 0; index < entries.size(); index++) {
			JsonNode entry = entries.get(index);
			if (!List.of("credential", "premium").contains(entry.path("id").asText())) continue;

			String prefix = "providers/providers:/providers/" + index + "/verification/";
			JsonNode verification = release221Verification();
			if (entry.at("/verification/methods").equals(verification.get("methods")))
				values.keySet().removeIf(path -> path.startsWith(prefix + "methods/"));
			if (entry.at("/verification/unavailableSelectionPolicy").equals(verification.get("unavailableSelectionPolicy")))
				values.remove(prefix + "unavailableSelectionPolicy");
		}

		return values;
	}

	/** Returns the verification settings 2.2.1 wrote into every provider entry it shipped. */
	private JsonNode release221Verification() {
		DataDirectory release = copy(EarlierConfiguration.RELEASE_221, Format.YAML, dataPath.resolve("release-2.2.1"));
		return release.tree("providers/providers").get("providers").get(0).get("verification");
	}

	/** Counts the texts and the non-empty lists, which identify a value where numbers and switches do not. */
	private static Map<JsonNode, Long> distinctive(Map<String, JsonNode> values) {
		return values.values().stream()
				.filter(value -> value.isTextual() && !value.asText().isEmpty() || value.isArray() && !value.isEmpty())
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
	}

	private static List<Integer> versions(MigrationReport report, String stream) {
		return report.getApplied().getOrDefault(stream, List.of()).stream()
				.map(AppliedMigration::getVersion)
				.toList();
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

		Path features = data.resolve("features");
		new RecognitionSettingsProvider(features.resolve("recognition"), registry).get();
		new RestrictionSettingsProvider(features.resolve("restriction"), registry).get();
		new JoinRestrictionSettingsProvider(features.resolve("restriction/join"), registry).get();
		new JoinRestrictionMessagesProvider(features.resolve("restriction/join"), registry).get();
		new JoinRestrictionCommandsProvider(features.resolve("restriction/join"), registry).get();
		new SentinelSettingsProvider(features.resolve("sentinel"), registry).get();
		new SentinelMessagesProvider(features.resolve("sentinel"), registry).get();
		new VerificationSettingsProvider(features.resolve("verification"), registry).get();
		new VerificationMessagesProvider(features.resolve("verification"), registry).get();
		new VerificationCommandsProvider(features.resolve("verification"), registry).get();
	}

	private static List<String> fieldNames(ObjectNode object) {
		List<String> names = new ArrayList<>();
		object.fieldNames().forEachRemaining(names::add);

		return names;
	}
}
