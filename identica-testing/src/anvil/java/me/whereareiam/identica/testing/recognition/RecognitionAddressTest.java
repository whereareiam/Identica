package me.whereareiam.identica.testing.recognition;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * A player with a Credential account returns to a network with recognition turned on, from the address and
 * through the hostname it registered with or from another one. Recognition compares its default signals: the
 * username, the address and the hostname the client announced.
 */
@Tag("account")
class RecognitionAddressTest {
	private static final String PASSWORD = "Secret-123";
	private static final String HOME = "127.0.0.2";
	private static final String ELSEWHERE = "127.0.0.3";
	private static final String UNTRUSTED = "127.0.0.4";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = TrustedLoopback.class)
	void recognisesAPlayerReturningFromTheSameAddress(ScenarioContext anvil) {
		Accounts.credential(anvil, Client.offline("Alice").from(HOME), PASSWORD).rejoin()
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRecognition().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = TrustedLoopback.class)
	void asksForThePasswordFromAnotherAddress(ScenarioContext anvil) {
		Accounts.credential(anvil, Client.offline("Alice").from(HOME), PASSWORD);

		Journey.of(anvil, Client.offline("Alice").from(ELSEWHERE)).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = TrustedLoopback.class)
	void asksForThePasswordThroughAnotherHostname(ScenarioContext anvil) {
		Accounts.credential(anvil, Client.offline("Alice").from(HOME), PASSWORD);

		Journey.of(anvil, Client.offline("Alice").from(HOME).through("play.identica.test")).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = OneUntrustedAddress.class)
	void neverRecognisesAPlayerFromAnUntrustedAddress(ScenarioContext anvil) {
		Accounts.credential(anvil, Client.offline("Alice").from(UNTRUSTED), PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = OneUntrustedAddress.class)
	void recognisesAPlayerFromAnAddressOffTheUntrustedList(ScenarioContext anvil) {
		Accounts.credential(anvil, Client.offline("Alice").from(HOME), PASSWORD).rejoin()
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRecognition().getBody());
	}

	/**
	 * Turns recognition on and trusts every address, since every test client connects from a loopback address,
	 * which the default untrusted list contains.
	 */
	private static final class TrustedLoopback implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			RecognitionSettings recognition = proxy.recognition();
			recognition.setEnabled(true);
			recognition.getEligibility().getUntrustedIps().setEnabled(false);
		}
	}

	/**
	 * Turns recognition on and distrusts one loopback address only.
	 */
	private static final class OneUntrustedAddress implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			RecognitionSettings recognition = proxy.recognition();
			recognition.setEnabled(true);
			recognition.getEligibility().getUntrustedIps().setEnabled(true);
			recognition.getEligibility().getUntrustedIps().setEntries(List.of(UNTRUSTED));
		}
	}
}
