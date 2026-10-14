package me.whereareiam.identica.testing.security;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelPolicy;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * Players exceed a sentinel's limit from one loopback address and others join from another address or with
 * another account. The brute-force and resume-spam sentinels both count an address and an account together, so
 * exceeding a limit locks out only that account from that address.
 */
@Tag("account")
class SentinelScopeTest {
	private static final String PASSWORD = "Secret-123";
	private static final String WRONG = "Wrong-456";
	private static final String FIRST = "127.0.0.2";
	private static final String SECOND = "127.0.0.3";
	private static final int BRUTE_FORCE_ATTEMPTS = 3;
	private static final int RESUME_ATTEMPTS = 5;
	private static final Duration LOCKOUT = Duration.ofMinutes(5);

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = ThreeAttempts.class)
	void bruteForceLocksOutTheAccountOnlyFromTheAddressThatGuessed(ScenarioContext anvil) {
		Accounts.credentialByAdmin(anvil, "Alice", PASSWORD);
		Journey guesser = Journey.of(anvil, Client.offline("Alice").from(FIRST)).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
		for (int attempt = 1; attempt < BRUTE_FORCE_ATTEMPTS; attempt++)
			guesser.login(WRONG).sees(m -> m.credential().getScenario().getAuthentication().getStatus().getInvalid());
		guesser.login(WRONG)
				.kickedWith(m -> m.credential().getScenario().getAuthentication().getBruteforce().getExceeded());

		Journey.of(anvil, Client.offline("Alice").from(SECOND)).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.leave();

		guesser.attemptAgain()
				.kickedWith(m -> m.credential().getScenario().getAuthentication().getBruteforce().getExceeded());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = FewResumes.class)
	void resumeSpamLocksOutOnlyTheFloodingAccountFromItsAddress(ScenarioContext anvil) {
		Accounts.credentialByAdmin(anvil, "Alice", PASSWORD);
		Accounts.credentialByAdmin(anvil, "Bob", PASSWORD);
		Journey flooder = Journey.of(anvil, Client.offline("Alice").from(FIRST)).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
		for (int attempt = 0; attempt < RESUME_ATTEMPTS; attempt++)
			flooder.login(WRONG);
		flooder.kickedWith(m -> m.sentinel().getResumeSpam().getDenied());

		logsIn(Journey.of(anvil, Client.offline("Bob").from(FIRST)));
		logsIn(Journey.of(anvil, Client.offline("Alice").from(SECOND)));

		flooder.attemptAgain().kickedWith(m -> m.sentinel().getResumeSpam().getDenied());
	}

	private static void logsIn(Journey player) {
		player.join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.leave();
	}

	private static final class ThreeAttempts implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			CredentialSettings.Scenario.Authentication.Bruteforce bruteforce = proxy.credential().getScenario().getAuthentication().getBruteforce();
			bruteforce.setMaxAttempts(BRUTE_FORCE_ATTEMPTS);
			bruteforce.getLockout().setDuration(LOCKOUT);
		}
	}

	private static final class FewResumes implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			SentinelPolicy resumeSpam = proxy.sentinel().getSentinels().getResumeSpam();
			resumeSpam.setEnabled(true);
			resumeSpam.setMaxAttempts(RESUME_ATTEMPTS);
			resumeSpam.getLockout().setDuration(LOCKOUT);
		}
	}
}
