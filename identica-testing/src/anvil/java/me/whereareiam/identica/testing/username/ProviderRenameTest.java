package me.whereareiam.identica.testing.username;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * A premium owner renames the account at Mojang. The account follows the new name on the next join; when a
 * Credential account holds it, the default rules rename the premium account instead, because nobody vouches for
 * the Credential player's choice but the holder chose it first. When another premium account still holds the name
 * from before its own rename, that name is stale: the current owner gets it.
 */
class ProviderRenameTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void followsARenameIntoAFreeUsername(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Alice");
		Journey.premium(anvil, account).join()
				.on(LOBBY)
				.playsAs("Alice")
				.leave();

		SessionIdentity renamed = yggdrasil.rename(account.getUniqueId(), "Alicia");

		Journey.premium(anvil, renamed).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getAuthentication().getBody())
				.playsAs("Alicia");
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, entrypoints = true)
	void renamesTheOwnerWhenARenameMeetsACredentialAccount(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Alice");
		premium(anvil, account).join()
				.on(LOBBY)
				.playsAs("Alice")
				.leave();
		Accounts.credential(anvil, Client.offline("Bob").through(Provider.CREDENTIAL.getEntrypoint()), PASSWORD);

		SessionIdentity renamed = yggdrasil.rename(account.getUniqueId(), "Bob");

		premium(anvil, renamed).named("Bob_premium").join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getAuthentication().getBody())
				.playsAs("Bob_premium");
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void givesAStaleUsernameToItsCurrentOwner(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity first = yggdrasil.register("Steve");
		Journey.premium(anvil, first).join()
				.on(LOBBY)
				.playsAs("Steve")
				.leave();
		SessionIdentity renamed = yggdrasil.rename(first.getUniqueId(), "Bob");
		SessionIdentity second = yggdrasil.register("Steve");

		// The new owner plays on another machine than the earlier one.
		Journey.of(anvil, Client.premium(second).from("127.0.0.2")).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.playsAs("Steve")
				.leave();

		Journey.premium(anvil, renamed).named("Bob").join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getAuthentication().getBody())
				.playsAs("Bob");
	}

	private static Journey premium(ScenarioContext anvil, SessionIdentity account) {
		return Journey.of(anvil, Client.premium(account).through(Provider.PREMIUM.getEntrypoint()));
	}
}
