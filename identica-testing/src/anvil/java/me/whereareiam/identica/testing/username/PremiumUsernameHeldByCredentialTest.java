package me.whereareiam.identica.testing.username;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * A Credential account holds a username that only later becomes the name of a premium account. On a network with
 * both providers, where Premium outranks Credential, the account that holds the username keeps it: Premium only asks
 * for a premium login when the account of a known username prefers Premium.
 */
@Tag("identity")
class PremiumUsernameHeldByCredentialTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void keepsLoggingTheCredentialAccountInWithItsPassword(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		// The offline client is named after the proxy, so the premium owner's client may use the username.
		Journey holder = Accounts.credential(anvil, "Steve", PASSWORD, PROXY);
		yggdrasil.register("Steve");

		holder.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void asksThePremiumOwnerForThePasswordOfTheAccountHoldingTheUsername(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		Accounts.credential(anvil, "Steve", PASSWORD, PROXY);
		SessionIdentity owner = yggdrasil.register("Steve");

		Journey.premium(anvil, owner).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.remainsOn(AUTH);
	}
}
