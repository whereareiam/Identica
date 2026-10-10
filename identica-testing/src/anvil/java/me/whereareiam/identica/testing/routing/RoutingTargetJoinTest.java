package me.whereareiam.identica.testing.routing;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * Players register and log in on networks whose routing sends them to another server, or leaves them where they
 * are, while a step waits and once they are done. Players join {@code lobby}.
 */
class RoutingTargetJoinTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, step = "", complete = "")
	void registersOnTheLobbyWithoutAnyRouting(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(LOBBY)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.remainsOn(LOBBY);
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, step = "", complete = "")
	void logsInOnTheLobbyWithoutAnyRouting(ScenarioContext anvil) {
		Accounts.credentialByAdmin(anvil, "Alice", PASSWORD);

		Journey.offline(anvil, "Alice").join()
				.on(LOBBY)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody())
				.remainsOn(LOBBY);
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, step = "", complete = "")
	void completesAPremiumRegistrationOnTheFirstServerWithoutAnyRouting(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.remainsOn(LOBBY);
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, complete = "")
	void staysOnTheStepServerWithoutACompletionTarget(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.remainsOn(AUTH);
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, step = "", complete = AUTH)
	void reachesTheCompletionTargetFromTheServerItRegisteredOn(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(LOBBY)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(AUTH)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, step = "")
	void completesOnTheServerItIsAlreadyOnWhenThatIsTheCompletionTarget(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(LOBBY)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.remainsOn(LOBBY);
	}
}
