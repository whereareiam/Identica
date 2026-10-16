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

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_A;

/**
 * A restriction lives in Redis on a replicated network, so a proxy that restarts keeps enforcing it.
 */
class JoinRestrictionRestartTest {
	@Test
	@IdenticaCluster(configure = JoinRestriction.class)
	void keepsRefusingNewPlayersAfterARestart(ScenarioContext anvil) {
		Administrator.at(anvil, PROXY_A).restrictsJoins(Provider.CREDENTIAL);

		anvil.processes().restart(PROXY_A);

		Journey.offline(anvil, "Bob", PROXY_A).attempt()
				.refusedAtLoginWith(m -> m.joinRestriction().getDenied());
	}

	static final class JoinRestriction implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.joinRestriction().setEnabled(true);
			proxy.joinRestriction().setAllow(List.of("linked"));
		}
	}
}
