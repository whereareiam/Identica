package me.whereareiam.identica.testing.join;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.JourneyPolicy;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.testing.journey.Prompt;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * An offline player with a Credential account joins a network whose only provider is Credential.
 */
class CredentialReturningJoinTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void logsInWithThePasswordInInteractiveMode(ScenarioContext anvil) {
		logsIn(anvil);
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL)
	void logsInWithThePasswordInSeamlessMode(ScenarioContext anvil) {
		logsIn(anvil);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void refusesAWrongPasswordAndAcceptsTheRightOne(ScenarioContext anvil) {
		Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(Prompt.LOGIN)
				.login("Wrong-456")
				.sees(Prompt.INVALID_PASSWORD)
				.remainsOn(AUTH)
				.login(PASSWORD)
				.on(LOBBY)
				.sees(Prompt.AUTHENTICATED_WITH_PASSWORD);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void asksForThePasswordAgainAfterAReconnect(ScenarioContext anvil) {
		Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(Prompt.LOGIN)
				.login(PASSWORD)
				.on(LOBBY)
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(Prompt.LOGIN);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void logsInToAnAccountAnAdministratorRegisteredInInteractiveMode(ScenarioContext anvil) {
		logsInToAnAdministratorRegisteredAccount(anvil);
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL)
	void logsInToAnAccountAnAdministratorRegisteredInSeamlessMode(ScenarioContext anvil) {
		logsInToAnAdministratorRegisteredAccount(anvil);
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, policy = JourneyPolicy.STRICT, providers = Provider.CREDENTIAL)
	void refusesARegisteredPlayerWhoWouldHaveToTypeInStrictSeamlessMode(ScenarioContext anvil) {
		Accounts.credentialByAdmin(anvil, "Alice", PASSWORD);

		Journey.offline(anvil, "Alice").attempt()
				.kickedWith(Prompt.INTERACTION_REQUIRED);
	}

	private void logsInToAnAdministratorRegisteredAccount(ScenarioContext anvil) {
		Accounts.credentialByAdmin(anvil, "Alice", PASSWORD);

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(Prompt.LOGIN)
				.login(PASSWORD)
				.on(LOBBY)
				.sees(Prompt.AUTHENTICATED_WITH_PASSWORD);
	}

	private void logsIn(ScenarioContext anvil) {
		Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(Prompt.LOGIN)
				.login(PASSWORD)
				.on(LOBBY)
				.sees(Prompt.AUTHENTICATED_WITH_PASSWORD);
	}
}
