package me.whereareiam.identica.testing.cluster;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.IdenticaCluster;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_A;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_B;

/**
 * An account that is online through one proxy logs in through the other. An account has one session, and
 * Identica replaces it by default.
 */
class ConcurrentLoginTest {
	private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(6);
	private static final String PASSWORD = "Secret-123";

	@Test
	@IdenticaCluster
	void aLoginThroughTheOtherProxyDisconnectsTheEarlierConnection(ScenarioContext anvil) {
		Journey earlier = online(anvil);

		Journey later = loginThroughProxyB(anvil);

		earlier.kickedWith(m -> m.identica().getEngine().getConcurrentLoginKick());
		later.on(LOBBY).remainsOn(LOBBY);
		Administrator.at(anvil, PROXY_A).findsSessionOf("Alice", PROXY_B).listsSessionsHeldBy(PROXY_B);
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

	/**
	 * A proxy that crashes cannot close its sessions. They stay until their heartbeat timeout passes, so the
	 * account is still refused right after the crash and free once the timeout has passed.
	 */
	@Test
	@IdenticaCluster(configure = {RejectNew.class, ShortHeartbeat.class})
	void aCrashedProxyReleasesTheAccountOnceItsSessionsExpire(ScenarioContext anvil) {
		online(anvil);

		anvil.processes().kill(PROXY_A);

		loginThroughProxyB(anvil)
				.kickedWith(m -> m.identica().getEngine().getConcurrentLoginRefused())
				.waits(HEARTBEAT_TIMEOUT.plusSeconds(2))
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.remainsOn(LOBBY);
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

	private static final class ShortHeartbeat implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.replication().setHeartbeatTimeout(HEARTBEAT_TIMEOUT);
		}
	}

	private static final class RejectNew implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.settings().getSessions().setConcurrencyPolicy(SessionConcurrencyPolicy.REJECT_NEW);
		}
	}
}
