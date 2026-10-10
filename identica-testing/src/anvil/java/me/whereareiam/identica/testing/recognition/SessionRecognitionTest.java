package me.whereareiam.identica.testing.recognition;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * A player with a Credential account returns to a network with recognition turned on. Every test player connects
 * from the loopback address, which recognition distrusts by default, so the networks that recognise players trust
 * every address; the default signals, username, address and virtual host, stay the same between a player's joins.
 */
class SessionRecognitionTest {
	private static final String PASSWORD = "Secret-123";
	private static final Duration VALIDITY = Duration.ofSeconds(10);

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Recognised.class)
	void letsAReturningPlayerInWithoutThePassword(ScenarioContext anvil) {
		Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(LOBBY)
				.doesNotSee(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.sees(m -> m.credential().getCompletion().getRecognition().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = {Recognised.class, ShortValidity.class})
	void asksForThePasswordAgainOnceTheValidityHasPassed(ScenarioContext anvil) {
		Accounts.credential(anvil, "Alice", PASSWORD).waits(VALIDITY)
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = RecognitionOnly.class)
	void asksForThePasswordFromAnAddressRecognitionDistrustsByDefault(ScenarioContext anvil) {
		Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
	}

	/**
	 * Turns recognition on and leaves every other setting at its default, including the distrusted addresses.
	 */
	private static final class RecognitionOnly implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.recognition().setEnabled(true);
		}
	}

	/**
	 * Turns recognition on for a network whose players all connect from the loopback address.
	 */
	private static final class Recognised implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			RecognitionSettings recognition = proxy.recognition();
			recognition.setEnabled(true);
			recognition.getEligibility().getUntrustedIps().setEnabled(false);
		}
	}

	private static final class ShortValidity implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.recognition().setValidity(VALIDITY);
		}
	}
}
