package me.whereareiam.identica.testing.cluster;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelPolicy;
import me.whereareiam.identica.testing.environment.IdenticaCluster;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.fixture.Authenticator;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_A;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_B;

/**
 * A player logs in through proxy A while another client with her username connects through proxy B. The proxies
 * replicate, so B knows the username is being logged in: a client from another address is refused until her login
 * has ended, and her own client from the same address continues where she is.
 */
class UsernameHoldTest {
	private static final String PASSWORD = "Secret-123";
	private static final String ELSEWHERE = "127.0.0.2";
	private static final int HELD_ATTEMPTS = 2;

	@Test
	@IdenticaCluster
	void refusesAnotherAddressWhileThePlayerTypesHerPassword(ScenarioContext anvil) {
		Journey owner = Accounts.credential(anvil, "Alice", PASSWORD, PROXY_A).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());

		elsewhereThroughProxyB(anvil, "Alice").kickedWith(m -> m.identica().getEngine().getUsernameHeld());

		owner.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@IdenticaCluster(configure = Authenticator.ShortPeriod.class)
	void refusesAnotherAddressWhileThePlayerTypesHerAuthenticatorCode(ScenarioContext anvil) {
		Enrolled alice = enrolled(anvil);
		Journey owner = verificationPrompt(alice.journey().rejoin());

		elsewhereThroughProxyB(anvil, "Alice").kickedWith(m -> m.identica().getEngine().getUsernameHeld());

		owner.command("2fa confirm " + alice.app().nextCode())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@IdenticaCluster
	void refusesAnotherAddressWhileThePlayerConfirmsHerNewPassword(ScenarioContext anvil) {
		Journey owner = registrationPrompt(Journey.offline(anvil, "Bob", PROXY_A).join())
				.command("pass " + PASSWORD)
				.sees(m -> m.credential().getScenario().getRegistration().getConfirmPrompt());

		elsewhereThroughProxyB(anvil, "Bob").kickedWith(m -> m.identica().getEngine().getUsernameHeld());

		owner.command("passconfirm " + PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	@Test
	@IdenticaCluster(configure = Authenticator.ShortPeriod.class)
	void continuesThroughTheOtherProxyFromTheSameAddress(ScenarioContext anvil) {
		Enrolled alice = enrolled(anvil);
		verificationPrompt(alice.journey().rejoin()).leave();

		Journey.offline(anvil, "Alice", PROXY_B).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt())
				.command("2fa confirm " + alice.app().nextCode())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	/**
	 * Two clients from one address log in at once, one through each proxy. The first to log in wins, and the other
	 * one is disconnected.
	 */
	@Test
	@IdenticaCluster
	void disconnectsTheOtherClientOnceOneLogsIn(ScenarioContext anvil) {
		Journey first = Accounts.credential(anvil, "Alice", PASSWORD, PROXY_A).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
		Journey second = Journey.offline(anvil, "Alice", PROXY_B).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());

		second.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());

		first.kickedWith(m -> m.identica().getEngine().getConcurrentLoginKick());
		second.remainsOn(LOBBY);
	}

	@Test
	@IdenticaCluster
	void letsAnotherAddressInOnceThePlayerLoggedIn(ScenarioContext anvil) {
		Accounts.credential(anvil, "Alice", PASSWORD, PROXY_A).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.leave();

		Journey.of(anvil, Client.offline("Alice").at(PROXY_B).from(ELSEWHERE)).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
	}

	@Test
	@IdenticaCluster(configure = FewContestedAttempts.class)
	void locksOutAnAddressThatKeepsTryingHeldUsernames(ScenarioContext anvil) {
		Accounts.credential(anvil, "Alice", PASSWORD, PROXY_A).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());

		Journey impostor = elsewhereThroughProxyB(anvil, "Alice")
				.kickedWith(m -> m.identica().getEngine().getUsernameHeld());
		for (int attempt = 1; attempt < HELD_ATTEMPTS; attempt++)
			impostor.attemptAgain().kickedWith(m -> m.identica().getEngine().getUsernameHeld());

		Journey.of(anvil, Client.offline("Carol").at(PROXY_B).from(ELSEWHERE)).attempt()
				.kickedWith(m -> m.sentinel().getContestedConnection().getDenied());
	}

	private static Journey elsewhereThroughProxyB(ScenarioContext anvil, String username) {
		return Journey.of(anvil, Client.offline(username).at(PROXY_B).from(ELSEWHERE)).attempt();
	}

	/**
	 * Expects a connection that was just accepted to be asked for a new password, choosing Credential first when the
	 * network asks which provider to register with.
	 */
	private static Journey registrationPrompt(Journey joined) {
		joined.on(AUTH);
		int asked = joined.seesAnyOf(
				m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody(),
				m -> m.credential().getScenario().getRegistration().getPrompt());
		if (asked == 0)
			joined.enroll(Provider.CREDENTIAL).sees(m -> m.credential().getScenario().getRegistration().getPrompt());

		return joined;
	}

	/**
	 * Registers Alice through proxy A, logs her in and enrolls an authenticator app; she leaves afterwards.
	 */
	private static Enrolled enrolled(ScenarioContext anvil) {
		Journey alice = Accounts.credential(anvil, "Alice", PASSWORD, PROXY_A).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);
		Authenticator app = Authenticator.of(alice.command("2fa enroll totp")
				.sees(m -> m.verification().getMethods().getTotp().getPending())
				.reads("secret"));
		alice.command("2fa enroll confirm " + app.code())
				.sees(m -> m.verification().getCommands().getConfirm().getRecoveryCodes().getBody())
				.command("2fa enroll confirm saved")
				.sees(m -> m.verification().getCommands().getConfirm().getEnabled())
				.leave();
		return new Enrolled(alice, app);
	}

	/**
	 * Expects the password prompt of a connection that was just accepted and types the password, which brings
	 * Alice to the authenticator prompt.
	 */
	private static Journey verificationPrompt(Journey joined) {
		return joined.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.sees(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt());
	}

	private record Enrolled(Journey journey, Authenticator app) {
	}

	private static final class FewContestedAttempts implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			SentinelPolicy policy = proxy.sentinel().getSentinels().getContestedConnection();
			policy.setMaxAttempts(HELD_ATTEMPTS);
			policy.getLockout().setDuration(Duration.ofMinutes(5));
		}
	}
}
