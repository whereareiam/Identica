package me.whereareiam.identica.testing.security;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * Once a username belongs to a Premium account, an offline client with that username is asked for a premium login on
 * every join, whatever it was told on an earlier join.
 */
@Tag("account")
class PremiumOwnedUsernameTest {
	private static final String OTHER_ADDRESS = "127.0.0.2";

	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void asksAnOfflineClientForAPremiumLoginOnEveryJoin(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");
		Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.leave();

		Journey intruder = Journey.of(anvil, Client.offline("Steve").from(OTHER_ADDRESS)).attempt();
		intruder.authenticationRequired();

		intruder.attemptAgain().authenticationRequired();
	}

	/**
	 * An offline client is refused for a premium username before anyone registered it; the owner then registers with
	 * Premium. The client's next join is refused as well, without the Credential registration it fell back to before
	 * the username had an account, and the owner still signs in with Premium.
	 */
	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void asksForAPremiumLoginAfterTheOwnerRegisteredARefusedUsername(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		Journey intruder = Journey.of(anvil, Client.offline("Steve").from(OTHER_ADDRESS)).attempt();
		intruder.authenticationRequired();

		Journey owner = Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.leave();

		intruder.attemptAgain().authenticationRequired();
		intruder.doesNotSee(m -> m.credential().getScenario().getRegistration().getPrompt());

		owner.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getAuthentication().getBody());
	}
}
