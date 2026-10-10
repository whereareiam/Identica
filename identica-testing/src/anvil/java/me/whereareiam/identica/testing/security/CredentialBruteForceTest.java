package me.whereareiam.identica.testing.security;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * A player with a Credential account types wrong passwords on a network whose brute-force sentinel allows three
 * attempts, warns from the second one and then locks the account out for a short while.
 */
class CredentialBruteForceTest {
	private static final String PASSWORD = "Secret-123";
	private static final String WRONG = "Wrong-456";
	private static final Duration LOCKOUT = Duration.ofSeconds(15);

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = ThreeAttempts.class)
	void warnsWithTheRemainingAttemptsBeforeTheLockout(ScenarioContext anvil) {
		Accounts.credentialByAdmin(anvil, "Alice", PASSWORD);

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(WRONG)
				.sees(m -> m.credential().getScenario().getAuthentication().getStatus().getInvalid())
				.without(m -> m.credential().getScenario().getAuthentication().getBruteforce().getRemaining())
				.login(WRONG)
				.sees(m -> m.credential().getScenario().getAuthentication().getStatus().getInvalid())
				.with(m -> m.credential().getScenario().getAuthentication().getBruteforce().getRemaining())
				.remainsOn(AUTH);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = ThreeAttempts.class)
	void refusesAPlayerWhoReturnsDuringTheLockout(ScenarioContext anvil) {
		lockedOut(anvil).attemptAgain()
				.kickedWith(m -> m.credential().getScenario().getAuthentication().getBruteforce().getExceeded());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = ThreeAttempts.class)
	void acceptsTheRightPasswordOnceTheLockoutHasPassed(ScenarioContext anvil) {
		lockedOut(anvil).waits(LOCKOUT)
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	/**
	 * Uses up every attempt. The proxy disconnects the player on the last one; the reason it gives is checked by
	 * {@code CredentialReturningJoinTest#locksTheAccountOutWithTheConfiguredMessageAfterTheLastAllowedWrongPassword}.
	 */
	private static Journey lockedOut(ScenarioContext anvil) {
		Accounts.credentialByAdmin(anvil, "Alice", PASSWORD);

		Journey alice = Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
		for (int attempt = 0; attempt < 3; attempt++)
			alice.login(WRONG);

		return alice.disconnected();
	}

	private static final class ThreeAttempts implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			CredentialSettings.Scenario.Authentication.Bruteforce bruteforce = proxy.credential().getScenario().getAuthentication().getBruteforce();
			bruteforce.setMaxAttempts(3);
			bruteforce.getLockout().setDuration(LOCKOUT);
			bruteforce.getWarning().setThresholdPercentage(50);
		}
	}
}
