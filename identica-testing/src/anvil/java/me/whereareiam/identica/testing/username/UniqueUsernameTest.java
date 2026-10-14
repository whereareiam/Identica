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

import java.util.UUID;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Usernames are unique without regard to case, as Minecraft's are. A player who chooses a username, as a Credential
 * player does, cannot register one that another account holds in any case, and neither can an administrator from
 * the console. A Credential player who joins with their own username in another case logs in to their account. A
 * provider that owns its usernames, as Premium does, is not refused: its player gets the name, and the account that
 * held it in another case is renamed, so no two accounts share it.
 */
@Tag("identity")
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
	@Identica(mode = JourneyMode.INTERACTIVE, providers = {Provider.CREDENTIAL, Provider.EXAMPLE})
	void refusesAConsoleRegistrationOfAUsernameAnAccountOfAnotherProviderHoldsInAnotherCase(ScenarioContext anvil) {
		Journey steve = Journey.offline(anvil, "Steve").join()
				.on(AUTH)
				.enroll(Provider.EXAMPLE)
				.sees(m -> m.example().getPrompt())
				.command("passphrase " + PASSPHRASE)
				.on(LOBBY);
		UUID account = steve.identity().getObserved().getUniqueId();
		steve.leave();

		Administrator.at(anvil, PROXY)
				.cannotRegisterCredential("steve", PASSWORD, "Steve", account)
				.findsNoDuplicateUsernames();

		// The account was given no password: it still logs in with its passphrase alone.
		steve.rejoin()
				.on(AUTH)
				.sees(m -> m.example().getPrompt())
				.command("passphrase " + PASSPHRASE)
				.on(LOBBY);
		assertEquals(account, steve.identity().getObserved().getUniqueId());
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL)
	void logsAPlayerJoiningInAnotherCaseInToTheirCredentialAccount(ScenarioContext anvil) {
		Journey registered = loggedIn(Accounts.credential(anvil, "Steve", PASSWORD).rejoin());
		UUID account = registered.identity().getObserved().getUniqueId();
		registered.leave();

		Journey player = loggedIn(Journey.offline(anvil, "steve").join());

		assertEquals(account, player.identity().getObserved().getUniqueId());
		Administrator.at(anvil, PROXY).findsNoDuplicateUsernames();
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, entrypoints = true)
	void givesAPremiumOwnerTheUsernameACredentialAccountHoldsInAnotherCase(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		Journey holder = Accounts.credential(anvil, Client.offline("steve").through(Provider.CREDENTIAL.getEntrypoint()), PASSWORD);
		SessionIdentity account = yggdrasil.register("Steve");

		Journey owner = Journey.of(anvil, Client.premium(account).through(Provider.PREMIUM.getEntrypoint())).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.playsAs("Steve")
				.leave();

		Administrator.at(anvil, PROXY).findsNoDuplicateUsernames();

		// Each account logs in through its own provider, the Credential one under its new name.
		holder.named("steve_credential").rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody())
				.playsAs("steve_credential")
				.leave();
		owner.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getAuthentication().getBody())
				.playsAs("Steve");
	}

	private static Journey loggedIn(Journey joined) {
		return joined.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);
	}
}
