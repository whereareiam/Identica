package me.whereareiam.identica.testing.credential;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * An administrator changes the algorithm the Credential provider hashes passwords with. Every stored password keeps
 * working, whether it was hashed before or after the change, and whether or not a login upgrades it.
 */
@Tag("account")
class PasswordHashingTest {
	private static final String SETTINGS = "providers/Credential/settings.yml";
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void passwordsKeepWorkingWhileTheAlgorithmChangesAndLoginsUpgradeThem(ScenarioContext anvil) {
		Journey alice = Accounts.credential(anvil, "Alice", PASSWORD);

		switchTo(anvil, "argon2", true);
		logsIn(alice);
		logsIn(alice);
		Journey bob = Accounts.credential(anvil, "Bob", PASSWORD);

		switchTo(anvil, "bcrypt", true);
		logsIn(alice);
		logsIn(bob);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void passwordsKeepWorkingWhileTheAlgorithmChangesWithoutUpgrades(ScenarioContext anvil) {
		Journey alice = Accounts.credential(anvil, "Alice", PASSWORD);

		switchTo(anvil, "argon2", false);
		logsIn(alice);
		Journey bob = Accounts.credential(anvil, "Bob", PASSWORD);

		switchTo(anvil, "bcrypt", false);
		logsIn(alice);
		logsIn(bob);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Argon2.class)
	void registersAndLogsInWithArgon2(ScenarioContext anvil) {
		logsIn(Accounts.credential(anvil, "Alice", PASSWORD));
	}

	private static void switchTo(ScenarioContext anvil, String algorithm, boolean autoupgrade) {
		ProxyConfiguration.editGenerated(anvil, SETTINGS, CredentialSettings.class, settings -> {
			settings.getCryptography().setAlgorithm(algorithm);
			settings.getCryptography().setAutoupgrade(autoupgrade);
		});
		Administrator.at(anvil, PROXY).reloads();
	}

	private static void logsIn(Journey player) {
		player.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody())
				.leave();
	}

	static final class Argon2 implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.credential().getCryptography().setAlgorithm("argon2");
		}
	}
}
