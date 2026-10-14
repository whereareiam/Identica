package me.whereareiam.identica.testing.cluster;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.IdenticaCluster;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.fixture.Authenticator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH_B;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_A;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_B;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An authenticator code is accepted once on the whole network, with or without Redis: a code used through one
 * proxy is refused through the other, and of a code typed through both at once only one is accepted.
 */
class SharedAuthenticatorCodeTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@IdenticaCluster(configure = Authenticator.ShortPeriod.class)
	void refusesThroughOneProxyACodeUsedThroughTheOther(ScenarioContext anvil) {
		refusesACodeUsedThroughTheOtherProxy(anvil);
	}

	@Test
	@IdenticaCluster(replication = false, configure = Authenticator.ShortPeriod.class)
	void refusesThroughOneProxyACodeUsedThroughTheOtherWhenTheProxiesOnlyShareTheDatabase(ScenarioContext anvil) {
		refusesACodeUsedThroughTheOtherProxy(anvil);
	}

	/**
	 * Redis carries Alice's login from proxy A to proxy B: B knows she already typed her password and asks for
	 * the code right away.
	 */
	@Test
	@IdenticaCluster(ownStepServers = true, configure = {Authenticator.ShortPeriod.class, AllowMultiple.class})
	void acceptsACodeTypedThroughBothProxiesAtOnceOnlyOnce(ScenarioContext anvil) {
		acceptsACodeTypedThroughBothProxiesOnce(anvil, joined -> joined.on(AUTH_B)
				.sees(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt()));
	}

	@Test
	@IdenticaCluster(replication = false, ownStepServers = true, configure = {Authenticator.ShortPeriod.class, AllowMultiple.class})
	void acceptsACodeTypedThroughBothProxiesAtOnceOnlyOnceWhenTheProxiesOnlyShareTheDatabase(ScenarioContext anvil) {
		acceptsACodeTypedThroughBothProxiesOnce(anvil, joined -> verificationPrompt(joined, AUTH_B));
	}

	private static void refusesACodeUsedThroughTheOtherProxy(ScenarioContext anvil) {
		Enrolled alice = enrolled(anvil);

		verificationPrompt(Journey.offline(anvil, "Alice", PROXY_B).join())
				.command("2fa confirm " + alice.app().nextCode())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody())
				.leave();

		verificationPrompt(alice.journey().rejoin())
				.command("2fa confirm " + alice.app().usedCode())
				.sees(m -> m.verification().getCommands().getConfirm().getInvalidCode())
				.remainsOn(AUTH)
				.command("2fa confirm " + alice.app().nextCode())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	/**
	 * Alice waits at the authenticator prompt of both proxies and types the same code into both. Sessions may be
	 * held through both proxies, so only the code decides who gets in. Each proxy has its own step server: on a
	 * shared one her second connection would push the first one off it.
	 *
	 * @param toTheCodePromptOfB brings the connection that proxy B just accepted to its authenticator prompt
	 */
	private static void acceptsACodeTypedThroughBothProxiesOnce(ScenarioContext anvil, UnaryOperator<Journey> toTheCodePromptOfB) {
		Enrolled alice = enrolled(anvil);
		Journey throughA = verificationPrompt(alice.journey().rejoin(), AUTH);
		Journey throughB = toTheCodePromptOfB.apply(Journey.offline(anvil, "Alice", PROXY_B).join());

		String code = alice.app().nextCode();
		throughA.command("2fa confirm " + code);
		throughB.command("2fa confirm " + code);

		boolean acceptedThroughA = accepts(throughA);
		boolean acceptedThroughB = accepts(throughB);
		assertTrue(acceptedThroughA ^ acceptedThroughB,
				"exactly one proxy must accept the code (A " + acceptedThroughA + ", B " + acceptedThroughB + ")");
		if (acceptedThroughA) {
			throughA.on(LOBBY);
			throughB.remainsOn(AUTH_B);
			return;
		}

		throughB.on(LOBBY);
		throughA.remainsOn(AUTH);
	}

	/**
	 * Returns whether the proxy let Alice in with the code she typed, rather than answering that it is invalid.
	 */
	private static boolean accepts(Journey journey) {
		return journey.seesAnyOf(
				m -> m.verification().getCommands().getConfirm().getInvalidCode(),
				m -> m.credential().getCompletion().getAuthentication().getBody()
		) == 1;
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
		return verificationPrompt(joined, AUTH);
	}

	/**
	 * Expects the password prompt on the step server of the proxy that just accepted the connection and types the
	 * password, which brings Alice to the authenticator prompt.
	 */
	private static Journey verificationPrompt(Journey joined, String stepServer) {
		return joined.on(stepServer)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.sees(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt());
	}

	private record Enrolled(Journey journey, Authenticator app) {
	}

	private static final class AllowMultiple implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.settings().getSessions().setConcurrencyPolicy(SessionConcurrencyPolicy.ALLOW_MULTIPLE);
		}
	}
}
