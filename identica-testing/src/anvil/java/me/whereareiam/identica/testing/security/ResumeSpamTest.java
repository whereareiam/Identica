package me.whereareiam.identica.testing.security;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelPolicy;
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
 * A player floods the authentication step with input on a network whose resume-spam sentinel is turned on and
 * allows a handful of attempts before a short lockout.
 */
class ResumeSpamTest {
	private static final String PASSWORD = "Secret-123";
	private static final String WRONG = "Wrong-456";
	private static final int ATTEMPTS = 5;
	private static final Duration LOCKOUT = Duration.ofSeconds(15);

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = FewResumes.class)
	void disconnectsAPlayerWhoKeepsSendingInput(ScenarioContext anvil) {
		flooded(anvil);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = FewResumes.class)
	void refusesAPlayerWhoReturnsDuringTheLockout(ScenarioContext anvil) {
		flooded(anvil).attemptAgain()
				.kickedWith(m -> m.sentinel().getResumeSpam().getDenied());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = FewResumes.class)
	void letsThePlayerLogInOnceTheLockoutHasPassed(ScenarioContext anvil) {
		flooded(anvil).waits(LOCKOUT)
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	/**
	 * Joining and every command count as attempts, so sending as many commands as the sentinel allows attempts
	 * always exceeds it.
	 */
	private static Journey flooded(ScenarioContext anvil) {
		Accounts.credentialByAdmin(anvil, "Alice", PASSWORD);

		Journey alice = Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
		for (int attempt = 0; attempt < ATTEMPTS; attempt++)
			alice.login(WRONG);

		return alice.kickedWith(m -> m.sentinel().getResumeSpam().getDenied());
	}

	private static final class FewResumes implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			SentinelPolicy resumeSpam = proxy.sentinel().getSentinels().getResumeSpam();
			resumeSpam.setEnabled(true);
			resumeSpam.setMaxAttempts(ATTEMPTS);
			resumeSpam.getLockout().setDuration(LOCKOUT);
		}
	}
}
