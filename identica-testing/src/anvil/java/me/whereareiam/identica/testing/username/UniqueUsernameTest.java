package me.whereareiam.identica.testing.username;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * Usernames are unique without regard to case, as Minecraft's are. A player who chooses a username, as a Credential
 * player does, cannot register one that another account holds in any case. A provider that vouches for its usernames,
 * as Premium does, is never refused: its player owns the name and gets an account that shares it, which
 * administrators find among the duplicate usernames.
 */
class UniqueUsernameTest {
	private static final String PASSWORD = "Secret-123";
	private static final String PASSPHRASE = "OpenSesame";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = {Provider.CREDENTIAL, Provider.EXAMPLE})
	void refusesACredentialRegistrationOfAUsernameAnotherAccountHoldsInAnotherCase(ScenarioContext anvil) {
		Journey.offline(anvil, "Steve").join()
				.on(AUTH)
				.enroll(Provider.EXAMPLE)
				.sees(m -> m.example().getPrompt())
				.command("passphrase " + PASSPHRASE)
				.on(LOBBY)
				.leave();

		Journey.offline(anvil, "steve").join()
				.on(AUTH)
				.enroll(Provider.CREDENTIAL)
				.kickedWith(m -> m.username().getPipeline().getPolicy().getTaken());

		Administrator.at(anvil, PROXY).findsNoDuplicateUsernames();
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, entrypoints = true)
	void givesAPremiumOwnerAnAccountWhenACredentialAccountHoldsTheUsernameInAnotherCase(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		Journey holder = Accounts.credential(anvil, Client.offline("steve").through(Provider.CREDENTIAL.getEntrypoint()), PASSWORD);
		SessionIdentity account = yggdrasil.register("Steve");

		Journey owner = Journey.of(anvil, Client.premium(account).through(Provider.PREMIUM.getEntrypoint())).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.leave();

		Administrator.at(anvil, PROXY).findsDuplicateUsername("steve");

		// Each account of the shared username logs in through its own provider.
		holder.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody())
				.leave();
		owner.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getAuthentication().getBody());
	}
}
