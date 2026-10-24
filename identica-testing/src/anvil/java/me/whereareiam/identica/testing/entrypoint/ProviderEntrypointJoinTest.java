package me.whereareiam.identica.testing.entrypoint;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyChoice;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * Players join networks whose providers answer on their own hostnames, {@code premium.identica.test} and
 * {@code credential.identica.test}. A hostname is one reason among others to pick a provider: it beats a verified
 * login without an account, the player's own choice and a matching name, and loses to a pending migration and to an
 * account the verified login already belongs to, so it never moves an account. It never lifts Premium's protection of
 * a premium name.
 */
@Tag("join")
class ProviderEntrypointJoinTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(choice = JourneyChoice.PROMPTED, entrypoints = true)
	void registersAnOfflinePlayerWithCredentialWithoutTheChoiceWhenAsked(ScenarioContext anvil) {
		Journey.of(anvil, Client.offline("Alice").through(Provider.CREDENTIAL.getEntrypoint())).join()
				.on(AUTH)
				.doesNotSee(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(choice = JourneyChoice.PROMPTED, entrypoints = true)
	void registersAPremiumOwnerWithPremiumWithoutTheChoiceWhenAsked(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.of(anvil, Client.premium(account).through(Provider.PREMIUM.getEntrypoint())).join()
				.on(LOBBY)
				.doesNotSee(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.sees(m -> m.premium().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(choice = JourneyChoice.PROMPTED, entrypoints = true)
	void givesAPremiumOwnerHisOwnAccountThroughThePremiumHostnameWhileACredentialAccountHoldsTheNameWhenAsked(
			ScenarioContext anvil,
			YggdrasilMock yggdrasil
	) {
		Accounts.credential(anvil, Client.offline("Steve").through(Provider.CREDENTIAL.getEntrypoint()), PASSWORD);
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.of(anvil, Client.premium(account).through(Provider.PREMIUM.getEntrypoint())).join()
				.on(LOBBY)
				.doesNotSee(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.sees(m -> m.premium().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(choice = JourneyChoice.PROMPTED, entrypoints = true)
	void asksAPremiumOwnerWithoutProofForThePasswordOfTheCredentialAccountHoldingTheNameWhenAsked(
			ScenarioContext anvil,
			YggdrasilMock yggdrasil
	) {
		Accounts.credential(anvil, Client.offline("Steve").through(Provider.CREDENTIAL.getEntrypoint()), PASSWORD);
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.premium(anvil, account).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
	}

	@Test
	@Identica(entrypoints = true)
	void registersAPremiumOwnerWithPremiumThroughItsHostname(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.of(anvil, Client.premium(account).through(Provider.PREMIUM.getEntrypoint())).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(entrypoints = true)
	void registersAnOfflinePlayerWithCredentialThroughItsHostname(ScenarioContext anvil) {
		Journey.of(anvil, Client.offline("Alice").through(Provider.CREDENTIAL.getEntrypoint())).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(entrypoints = true)
	void registersAPremiumOwnerWithCredentialAfterThePremiumLoginThroughTheCredentialHostname(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.of(anvil, Client.premium(account).through(Provider.CREDENTIAL.getEntrypoint())).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.leave();

		Journey.premium(anvil, account).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(entrypoints = true)
	void demandsThePremiumLoginForAPremiumNameThroughTheCredentialHostname(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		yggdrasil.register("Alice");

		Journey.of(anvil, Client.offline("Alice").through(Provider.CREDENTIAL.getEntrypoint())).attempt()
				.authenticationRequired();
	}

	@Test
	@Identica(entrypoints = true)
	void keepsAPremiumAccountOnPremiumThroughTheCredentialHostname(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");
		Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.leave();

		Journey.of(anvil, Client.premium(account).through(Provider.CREDENTIAL.getEntrypoint())).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(entrypoints = true)
	void demandsAPremiumLoginFromAnOfflinePlayerThroughThePremiumHostname(ScenarioContext anvil) {
		Journey.of(anvil, Client.offline("Alice").through(Provider.PREMIUM.getEntrypoint())).attempt()
				.authenticationRequired();
	}

	@Test
	@Identica(entrypoints = true)
	void givesAPremiumOwnerHisOwnAccountThroughThePremiumHostnameWhileACredentialAccountHoldsTheName(
			ScenarioContext anvil,
			YggdrasilMock yggdrasil
	) {
		Accounts.credential(anvil, Client.offline("Steve").through(Provider.CREDENTIAL.getEntrypoint()), PASSWORD);
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.of(anvil, Client.premium(account).through(Provider.PREMIUM.getEntrypoint())).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(entrypoints = true)
	void asksAPremiumOwnerWithoutProofForThePasswordOfTheCredentialAccountHoldingTheName(
			ScenarioContext anvil,
			YggdrasilMock yggdrasil
	) {
		Accounts.credential(anvil, Client.offline("Steve").through(Provider.CREDENTIAL.getEntrypoint()), PASSWORD);
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.premium(anvil, account).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
	}
}
