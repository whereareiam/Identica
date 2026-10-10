package me.whereareiam.identica.testing.cluster;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.IdenticaCluster;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_A;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_B;

/**
 * An account that is online through one proxy logs in through the other. Identica replaces existing sessions by
 * default.
 */
class ConcurrentLoginTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@IdenticaCluster
	void aLoginThroughTheOtherProxyDisconnectsTheEarlierConnection(ScenarioContext anvil) {
		Journey earlier = online(anvil);

		Journey later = loginThroughProxyB(anvil);

		earlier.kickedWith(m -> m.identica().getEngine().getConcurrentLoginKick());
		later.on(LOBBY).remainsOn(LOBBY);
	}

	@Test
	@IdenticaCluster(configure = RejectNew.class)
	void aLoginThroughTheOtherProxyIsRefusedWhileTheAccountIsOnline(ScenarioContext anvil) {
		Journey earlier = online(anvil);

		loginThroughProxyB(anvil)
				.kickedWith(m -> m.identica().getEngine().getConcurrentLoginRefused());

		earlier.remainsOn(LOBBY);
	}

	@Test
	@IdenticaCluster(configure = RejectNew.class)
	void aStoppedProxyDoesNotKeepTheAccountLocked(ScenarioContext anvil) {
		online(anvil);

		anvil.processes().stop(PROXY_A);

		loginThroughProxyB(anvil).on(LOBBY).remainsOn(LOBBY);
	}

	@Test
	@IdenticaCluster(configure = AllowMultiple.class)
	void leavingOneProxyKeepsTheSessionOfTheConnectionOnTheOther(ScenarioContext anvil) {
		Journey earlier = online(anvil);
		Journey later = loginThroughProxyB(anvil).on(LOBBY);

		earlier.leave();

		later.remainsOn(LOBBY);
		Administrator.at(anvil, PROXY_A).findsSessionOf("Alice");
	}

	/**
	 * The later connection stays on the auth server, because a backend keeps only one player of a name: on the
	 * lobby it would end the earlier connection there, whatever Identica allows.
	 */
	@Test
	@IdenticaCluster(configure = {AllowMultiple.class, ProxyBLeavesPlayersWhereTheyAre.class})
	void allowingMultipleKeepsASessionForEveryConnection(ScenarioContext anvil) {
		Journey earlier = online(anvil);
		Journey later = loginThroughProxyB(anvil)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody())
				.remainsOn(AUTH);
		earlier.remainsOn(LOBBY);

		Administrator.at(anvil, PROXY_A).findsSessionsOf("Alice", PROXY_A, PROXY_B).listsSessionsHeldBy(PROXY_A, PROXY_B);
		Administrator.at(anvil, PROXY_B).findsSessionsOf("Alice", PROXY_A, PROXY_B).listsSessionsHeldBy(PROXY_A, PROXY_B);

		earlier.leave();

		Administrator.at(anvil, PROXY_A).findsSessionsOf("Alice", PROXY_B).listsSessionsHeldBy(PROXY_B);
		later.remainsOn(AUTH);
	}

	private static Journey online(ScenarioContext anvil) {
		return Accounts.credential(anvil, "Alice", PASSWORD, PROXY_A).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);
	}

	private static Journey loginThroughProxyB(ScenarioContext anvil) {
		return Journey.offline(anvil, "Alice", PROXY_B).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD);
	}

	private static final class RejectNew implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.settings().getSessions().setConcurrencyPolicy(SessionConcurrencyPolicy.REJECT_NEW);
		}
	}

	private static final class ProxyBLeavesPlayersWhereTheyAre implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			if (proxy.name().equals(PROXY_B))
				proxy.routing().getDefaults().getComplete().setTarget("");
		}
	}

	private static final class AllowMultiple implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.settings().getSessions().setConcurrencyPolicy(SessionConcurrencyPolicy.ALLOW_MULTIPLE);
		}
	}
}
