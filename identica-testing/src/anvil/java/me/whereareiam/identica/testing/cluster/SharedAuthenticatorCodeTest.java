package me.whereareiam.identica.testing.cluster;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.IdenticaCluster;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.fixture.Authenticator;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_A;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_B;

/**
 * An authenticator code is accepted once on the whole network: a code used through one proxy is refused
 * through the other.
 */
class SharedAuthenticatorCodeTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@IdenticaCluster
	void refusesThroughOneProxyACodeUsedThroughTheOther(ScenarioContext anvil) {
		refusesACodeUsedThroughTheOtherProxy(anvil);
	}

	@Test
	@IdenticaCluster(replication = false)
	void refusesACodeUsedThroughTheOtherProxyWhenTheProxiesOnlyShareTheDatabase(ScenarioContext anvil) {
		refusesACodeUsedThroughTheOtherProxy(anvil);
	}

	private static void refusesACodeUsedThroughTheOtherProxy(ScenarioContext anvil) {
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

		Journey.offline(anvil, "Alice", PROXY_B).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.sees(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt())
				.command("2fa confirm " + app.code())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody())
				.leave();

		alice.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.sees(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt())
				.command("2fa confirm " + app.usedCode())
				.sees(m -> m.verification().getCommands().getConfirm().getInvalidCode())
				.remainsOn(AUTH)
				.command("2fa confirm " + app.code())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}
}
