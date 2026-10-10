package me.whereareiam.identica.testing.join;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.JourneyPolicy;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

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
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login("Wrong-456")
				.sees(m -> m.credential().getScenario().getAuthentication().getStatus().getInvalid())
				.remainsOn(AUTH)
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void asksForThePasswordAgainAfterAReconnect(ScenarioContext anvil) {
		Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = RejectNewSessions.class)
	void closesTheSessionOnLeavingSoAReconnectLogsInWhileNewSessionsAreRejected(ScenarioContext anvil) {
		Journey alice = Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);
		Administrator.at(anvil, PROXY).findsSessionOf("Alice");

		alice.leave();
		Administrator.at(anvil, PROXY).findsNoSessionOf("Alice");

		alice.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
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
				.kickedWith(m -> m.identica().getEngine().getJourney().getStep().getInteractionRequired());
	}

	private void logsInToAnAdministratorRegisteredAccount(ScenarioContext anvil) {
		Accounts.credentialByAdmin(anvil, "Alice", PASSWORD);

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	private void logsIn(ScenarioContext anvil) {
		Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	/**
	 * Refuses a session while the account already has one, so a reconnect only logs in when leaving closed the
	 * earlier session.
	 */
	private static final class RejectNewSessions implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.settings().getSessions().setConcurrencyPolicy(SessionConcurrencyPolicy.REJECT_NEW);
		}
	}
}
