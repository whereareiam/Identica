package me.whereareiam.identica.testing.credential;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * A network turns {@code engine.scenarios.*.allowResume} off. Players still register and log in on one connection,
 * and a reconnect the login asks for still continues it; only a player who leaves and comes back starts over.
 */
@Tag("account")
class LoginWithoutResumeTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, configure = NoResume.class)
	void registersAndLogsInOnOneConnection(ScenarioContext anvil) {
		Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, configure = NoResume.class)
	void startsARegistrationAgainAfterARejoin(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.command("pass " + PASSWORD)
				.sees(m -> m.credential().getScenario().getRegistration().getConfirmPrompt())
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.doesNotSee(m -> m.credential().getScenario().getRegistration().getConfirmPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	/**
	 * Choosing Premium asks the player to rejoin for the premium login, and that rejoin continues the registration.
	 */
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, configure = NoResume.class)
	void registersWithPremiumAfterTheRequestedRejoin(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.premium(anvil, account).join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.enroll(Provider.PREMIUM)
				.kickedWith(m -> m.premium().getVerification().getRejoin())
				.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody());
	}

	static final class NoResume implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.engine().getScenarios().getAuthentication().setAllowResume(false);
			proxy.engine().getScenarios().getRegistration().setAllowResume(false);
			proxy.engine().getScenarios().getMigration().setAllowResume(false);
		}
	}
}
