package me.whereareiam.identica.testing.routing;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY_HOST;

/**
 * Players register on networks whose proxy forces {@link me.whereareiam.identica.testing.environment.IdenticaNetwork#LOBBY_HOST}
 * to {@code lobby} and whose routing has no completion server of its own. They register on {@code auth}.
 */
class ForcedHostJoinTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, complete = "", configure = FollowForcedHosts.class)
	void reachesTheForcedHostServerOnceDone(ScenarioContext anvil) {
		Journey.offlineThrough(anvil, "Alice", LOBBY_HOST).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, complete = "", configure = FollowForcedHosts.class)
	void staysOnTheStepServerThroughAHostnameWithoutAForcedHost(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.remainsOn(AUTH);
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, complete = "")
	void staysOnTheStepServerWhenRoutingDoesNotFollowForcedHosts(ScenarioContext anvil) {
		Journey.offlineThrough(anvil, "Alice", LOBBY_HOST).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.remainsOn(AUTH);
	}

	static final class FollowForcedHosts implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.routing().getDefaults().getComplete().setForcedHosts(true);
		}
	}
}
