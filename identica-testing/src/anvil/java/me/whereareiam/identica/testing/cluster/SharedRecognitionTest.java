package me.whereareiam.identica.testing.cluster;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
import me.whereareiam.identica.feature.recognition.type.RecognitionSignal;
import me.whereareiam.identica.testing.environment.IdenticaCluster;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_A;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_B;

/**
 * A player with a Credential account moves between the proxies of a replicated cluster with recognition turned on,
 * recognised by username and address. Each proxy recognises the player from the address of their latest login,
 * wherever that login happened.
 */
class SharedRecognitionTest {
	private static final String PASSWORD = "Secret-123";
	private static final String HOME = "127.0.0.2";
	private static final String ELSEWHERE = "127.0.0.3";

	@Test
	@IdenticaCluster(configure = TrustedLoopback.class)
	void recognisesAPlayerOnTheOtherProxy(ScenarioContext anvil) {
		Accounts.credential(anvil, Client.offline("Alice").from(HOME).at(PROXY_A), PASSWORD);

		Journey.of(anvil, Client.offline("Alice").from(HOME).at(PROXY_B)).join()
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRecognition().getBody());
	}

	/**
	 * Proxy B recognised Alice from her home address once. After she logs in from elsewhere through proxy A, proxy B
	 * no longer recognises her home address, because her latest login came from elsewhere.
	 */
	@Test
	@IdenticaCluster(configure = TrustedLoopback.class)
	void followsTheLatestLoginMadeOnTheOtherProxy(ScenarioContext anvil) {
		Accounts.credential(anvil, Client.offline("Alice").from(HOME).at(PROXY_A), PASSWORD);
		Journey homeOnB = Journey.of(anvil, Client.offline("Alice").from(HOME).at(PROXY_B)).join()
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRecognition().getBody())
				.leave();

		Journey.of(anvil, Client.offline("Alice").from(ELSEWHERE).at(PROXY_A)).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.leave();

		homeOnB.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
	}

	static final class TrustedLoopback implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			RecognitionSettings recognition = proxy.recognition();
			recognition.setEnabled(true);
			recognition.getEligibility().getUntrustedIps().setEnabled(false);
			// The test proxies listen on different ports, which a cluster behind one hostname does not.
			recognition.setDefaultSignals(List.of(RecognitionSignal.USERNAME, RecognitionSignal.IP));
		}
	}
}
