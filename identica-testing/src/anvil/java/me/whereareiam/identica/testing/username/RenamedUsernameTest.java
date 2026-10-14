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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * An account that lost its username in a conflict takes it back on its next join once nobody holds it. A username
 * an administrator gives an account by hand stays: neither a conflict nor a rename at Mojang changes it.
 */
@Tag("identity")
class RenamedUsernameTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, entrypoints = true)
	void givesARenamedAccountItsUsernameBackOnceItIsFree(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		Journey holder = Accounts.credential(anvil, Client.offline("Steve").through(Provider.CREDENTIAL.getEntrypoint()), PASSWORD);
		SessionIdentity account = yggdrasil.register("Steve");
		Journey.of(anvil, Client.premium(account).through(Provider.PREMIUM.getEntrypoint())).join()
				.on(LOBBY)
				.leave();

		Administrator.at(anvil, PROXY).deletes("Steve");

		holder.rejoin()
				.on(AUTH)
				.login(PASSWORD)
				.on(LOBBY)
				.playsAs("Steve");
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void keepsAUsernameAnAdministratorGave(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");
		Journey owner = Journey.premium(anvil, account).join()
				.on(LOBBY)
				.leave();

		Administrator.at(anvil, PROXY).renamesUsername("Steve", "Captain");
		SessionIdentity renamed = yggdrasil.rename(account.getUniqueId(), "Stephen");

		Journey.premium(anvil, renamed).named("Captain").join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getAuthentication().getBody())
				.playsAs("Captain");
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void refusesToRenameAnAccountToAHeldUsername(ScenarioContext anvil) {
		Accounts.credential(anvil, "Alice", PASSWORD);
		Journey bob = Accounts.credential(anvil, "Bob", PASSWORD);

		Administrator.at(anvil, PROXY).cannotRenameUsernameTo("Bob", "ALICE");

		bob.rejoin()
				.on(AUTH)
				.login(PASSWORD)
				.on(LOBBY)
				.playsAs("Bob");
	}
}
