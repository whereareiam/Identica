package me.whereareiam.identica.testing.upgrade;

import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.adapter.database.testing.schema.EarlierInstallation;
import me.whereareiam.identica.common.config.testing.EarlierConfiguration;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.DatabaseType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A proxy starts on what an installation of Identica 2.2.1 left, upgrades it, and its players keep their accounts:
 * the SQLite database, with the configuration files of this version, and then the database together with the
 * configuration directory of a 2.2.1 installation whose operator changed its settings. Upgrade journeys run through
 * {@code anvilUpgradeTest}, not with the other journeys.
 */
@Tag("upgrade")
class Release221UpgradeTest {
	/** The password the 2.2.1 database holds a bcrypt hash of for Upgrader. */
	private static final String PASSWORD = "Upgrade-221";

	/** The passphrase of the example provider, which the 2.2.1 database links Dave and dave to. */
	private static final String PASSPHRASE = "OpenSesame";

	/** The line the customised 2.2.1 directory adds to each of its messages. */
	private static final String CUSTOMISED = "[customised]";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Release221Database.class)
	void logsInWithThePasswordOf221AlsoInAnotherCase(ScenarioContext anvil) {
		// The usernames move to their own tables, and the migration history 2.x kept for the credential tables goes.
		ProcessConsole console = anvil.processes().proxy(PROXY).console();
		console.await("Database schema identica/account: applied 1 username-layout", 0, Duration.ofSeconds(5));
		console.await("Database schema identica/account: applied 2 username-table", 0, Duration.ofSeconds(5));
		console.await("Database schema identica/provider/credential: applied 2 legacy-migration-history", 0, Duration.ofSeconds(5));

		Journey.offline(anvil, "Upgrader").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody())
				.leave();

		// Usernames are compared without regard to case: in another case it is the same player and the same account.
		Journey.offline(anvil, "upgrader").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Release221Directory.class)
	void startsOnACustomised221DirectoryWithItsSettings(ScenarioContext anvil) {
		// The operator's settings reached their places: its timeouts and its texts, and its completion server.
		Engine engine = ProxyConfiguration.generated(anvil, "engine.yml", Engine.class);
		assertEquals(Duration.ofMinutes(7), engine.getBehavior().getHandshakeInstructionTtl());
		assertEquals(Duration.ofMinutes(9), engine.getBehavior().getBridgeTtl());
		VerificationSettings verification = ProxyConfiguration.generated(anvil, "features/verification/settings.yml", VerificationSettings.class);
		assertEquals(8, verification.getTotp().getDigits());
		Messages messages = ProxyConfiguration.generated(anvil, "messages.yml", Messages.class);
		assertEquals(CUSTOMISED, last(messages.getScenarios().getRegistration().getRouting().getMissingServer()));
		// One session per account: the provider's ALLOW_MULTIPLE of 2.2.1 lets the newest login take over instead.
		Providers providers = ProxyConfiguration.generated(anvil, "providers/providers.yml", Providers.class);
		assertEquals(SessionConcurrencyPolicy.REPLACE_EXISTING, providers.getProviders().stream()
				.filter(entry -> entry.getId().equals(Provider.CREDENTIAL.getId()))
				.findFirst()
				.orElseThrow()
				.getSession()
				.getConcurrencyPolicy());
		// Credential's messages of 2.2.1 still name a message this version no longer has; the rest of the file loads.
		CredentialMessages credential = ProxyConfiguration.generated(anvil, "providers/Credential/messages.yml", CredentialMessages.class);
		assertEquals(
				"{prefix}<white>Your current password is <red>invalid</red>.</white>",
				credential.getScenario().getMigration().getVerification().getStatus().getInvalid()
		);

		Journey.offline(anvil, "Upgrader").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody())
				.leave();

		// The registration scenario selects the only provider on its own, as the operator set in 2.2.1.
		Journey.offline(anvil, "Newcomer").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());
	}

	/**
	 * The 2.2.1 database holds Dave and dave, two accounts of one username in two cases. Dave, the older, holds the
	 * username; both go on logging in through their own provider links, and the console names them at every start.
	 */
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.EXAMPLE, configure = Release221Database.class)
	void keepsLoggingInBothAccountsOfAUsername221HeldInTwoCases(ScenarioContext anvil) {
		ProcessConsole console = anvil.processes().proxy(PROXY).console();
		console.await("Duplicate usernames: dave", 0, Duration.ofSeconds(5));

		for (String username : List.of("Dave", "dave"))
			Journey.offline(anvil, username).join()
					.on(AUTH)
					.sees(m -> m.example().getPrompt())
					.command("passphrase " + PASSPHRASE)
					.on(LOBBY)
					.leave();

		Administrator.at(anvil, PROXY).findsDuplicateUsername("dave");
	}

	private static String last(List<String> lines) {
		return lines.get(lines.size() - 1);
	}

	/**
	 * Builds the SQLite database of a 2.2.1 installation from the layouts schema tests upgrade.
	 */
	private static Path release221Database() {
		try {
			Path directory = Files.createTempDirectory("identica-2.2.1-");
			directory.toFile().deleteOnExit();
			Path database = directory.resolve("identica.db");
			database.toFile().deleteOnExit();
			try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
				for (String owner : List.of("account", "verification", "credential"))
					EarlierInstallation.create(connection, DatabaseType.SQLITE, "identica-2.2.1/" + owner);
			}

			return database;
		} catch (IOException failure) {
			throw new UncheckedIOException(failure);
		} catch (SQLException failure) {
			throw new IllegalStateException("Could not build the 2.2.1 database", failure);
		}
	}

	/**
	 * Places the SQLite database of a 2.2.1 installation in the proxy's data directory.
	 */
	private static final class Release221Database implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.seed("identica.db", release221Database());
		}
	}

	/**
	 * Places the database and the configuration directory of a customised 2.2.1 installation in the proxy's data
	 * directory, in place of the files this version's test network writes.
	 */
	private static final class Release221Directory implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.seed("identica.db", release221Database());
			try {
				Path directory = Files.createTempDirectory("identica-2.2.1-config-");
				directory.toFile().deleteOnExit();
				EarlierConfiguration.copy(EarlierConfiguration.RELEASE_221_CUSTOMISED, directory);
				for (String file : EarlierConfiguration.files(EarlierConfiguration.RELEASE_221_CUSTOMISED)) {
					proxy.omit(file);
					proxy.seed(file, directory.resolve(file));
				}
			} catch (IOException failure) {
				throw new UncheckedIOException(failure);
			}
			for (String file : List.of("engine.yml", "routing.yml"))
				proxy.omit(file);
		}
	}
}
