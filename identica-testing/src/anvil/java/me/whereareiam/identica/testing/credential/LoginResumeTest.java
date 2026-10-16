package me.whereareiam.identica.testing.credential;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * A player leaves while registering and comes back. With {@code engine.scenarios.*.allowResume}, as Identica
 * generates it, the registration continues where it stopped.
 */
@Tag("account")
class LoginResumeTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL)
	void continuesARegistrationAfterARejoin(ScenarioContext anvil) {
		halfRegistered(anvil)
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getConfirmPrompt())
				.command("passconfirm " + PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	private static Journey halfRegistered(ScenarioContext anvil) {
		return Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.command("pass " + PASSWORD)
				.sees(m -> m.credential().getScenario().getRegistration().getConfirmPrompt())
				.leave();
	}
}
