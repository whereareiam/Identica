package me.whereareiam.identica.testing.upgrade;

import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.adapter.database.testing.schema.EarlierInstallation;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.DatabaseType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
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

/**
 * A proxy starts on the SQLite database an installation of Identica 2.2.1 left, upgrades it, and its players
 * keep their accounts. The configuration files are those of this version, since their upgrade is not part of
 * this journey. Upgrade journeys run through {@code anvilUpgradeTest}, not with the other journeys.
 */
@Tag("upgrade")
class Release221UpgradeTest {
	/** The password the 2.2.1 database holds a bcrypt hash of for Upgrader. */
	private static final String PASSWORD = "Upgrade-221";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Release221Database.class)
	void logsInWithThePasswordOf221AndTreatsACaseVariantAsAnotherPlayer(ScenarioContext anvil) {
		// The usernames move to their own tables, and the migration history 2.x kept for the credential tables goes.
		ProcessConsole console = anvil.processes().proxy(PROXY).console();
		console.await("Database schema identica/account: applied 1 username-layout", 0, Duration.ofSeconds(5));
		console.await("Database schema identica/provider/credential: applied 2 legacy-migration-history", 0, Duration.ofSeconds(5));

		Journey.offline(anvil, "Upgrader").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody())
				.leave();

		// As on a database this version created: another case is another offline player, who has no account yet.
		Journey.offline(anvil, "upgrader").join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody());
	}

	/**
	 * Places the SQLite database of a 2.2.1 installation, built from the layouts schema tests upgrade, in the
	 * proxy's data directory.
	 */
	private static final class Release221Database implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			try {
				Path directory = Files.createTempDirectory("identica-2.2.1-");
				directory.toFile().deleteOnExit();
				Path database = directory.resolve("identica.db");
				database.toFile().deleteOnExit();
				try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
					for (String owner : List.of("account", "verification", "credential"))
						EarlierInstallation.create(connection, DatabaseType.SQLITE, "identica-2.2.1/" + owner);
				}

				proxy.seed("identica.db", database);
			} catch (IOException failure) {
				throw new UncheckedIOException(failure);
			} catch (SQLException failure) {
				throw new IllegalStateException("Could not build the 2.2.1 database", failure);
			}
		}
	}
}
