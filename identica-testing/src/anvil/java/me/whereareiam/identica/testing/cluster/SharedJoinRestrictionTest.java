package me.whereareiam.identica.testing.cluster;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.IdenticaCluster;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_A;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_B;

/**
 * An administrator restricts joins through Credential on one proxy of a replicated cluster. The restriction holds for
 * the whole network until it is lifted on either proxy.
 */
class SharedJoinRestrictionTest {
	@Test
	@IdenticaCluster(configure = JoinRestriction.class)
	void refusesANewPlayerOnTheOtherProxy(ScenarioContext anvil) {
		Administrator.at(anvil, PROXY_A).restrictsJoins(Provider.CREDENTIAL);

		Journey.offline(anvil, "Bob", PROXY_B).attempt()
				.refusedAtLoginWith(m -> m.joinRestriction().getDenied());
	}

	@Test
	@IdenticaCluster(configure = JoinRestriction.class)
	void letsANewPlayerInOnceTheOtherProxyLiftsTheRestriction(ScenarioContext anvil) {
		Administrator.at(anvil, PROXY_A).restrictsJoins(Provider.CREDENTIAL);
		Administrator.at(anvil, PROXY_B).liftsJoinRestriction(Provider.CREDENTIAL);

		Journey.offline(anvil, "Bob", PROXY_A).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());
	}

	static final class JoinRestriction implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.joinRestriction().setEnabled(true);
			proxy.joinRestriction().setAllow(List.of("linked"));
		}
	}
}
