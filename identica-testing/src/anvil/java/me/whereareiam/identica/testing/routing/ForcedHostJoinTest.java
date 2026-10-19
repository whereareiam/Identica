package me.whereareiam.identica.testing.routing;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY_HOST;

/**
 * Players join networks whose proxy forces {@link me.whereareiam.identica.testing.environment.IdenticaNetwork#LOBBY_HOST}
 * to {@code lobby} and whose routing has no completion server of its own. Credential players register on
 * {@code auth}; a returning premium player needs no step there.
 */
@Tag("join")
class ForcedHostJoinTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, complete = "", configure = FollowForcedHosts.class)
	void reachesTheForcedHostServerOnceDone(ScenarioContext anvil) {
		Journey.of(anvil, Client.offline("Alice").through(LOBBY_HOST)).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, complete = "", configure = FollowForcedHosts.class)
	void returnsWithPremiumStraightToTheForcedHostServer(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.of(anvil, Client.premium(account).through(LOBBY_HOST)).join()
				.on(LOBBY)
				.leave()
				.rejoin()
				.on(LOBBY)
				.bypasses(AUTH);
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
		Journey.of(anvil, Client.offline("Alice").through(LOBBY_HOST)).join()
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
