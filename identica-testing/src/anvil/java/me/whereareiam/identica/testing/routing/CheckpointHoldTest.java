package me.whereareiam.identica.testing.routing;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.ASK_HOST;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.HOLD_HOST;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * The example addon's checkpoints hold players on Velocity, and the console answers their steps. Players who join
 * through {@link me.whereareiam.identica.testing.environment.IdenticaNetwork#HOLD_HOST} wait on the loading screen,
 * where no server sees them; once the journey has finished, the proxy sends them to the server the journey completes
 * on directly. Players who join through {@link me.whereareiam.identica.testing.environment.IdenticaNetwork#ASK_HOST}
 * wait on the step's server, in its world.
 */
@Tag("join")
class CheckpointHoldTest {
	private static final String PASSWORD = "Secret-123";
	/** Longer than Velocity's read timeout, which closes a connection whose client sends nothing for 30 seconds. */
	private static final Duration PAST_THE_READ_TIMEOUT = Duration.ofSeconds(35);

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, addon = true)
	void holdsAPlayerOnTheLoadingScreenUntilTheyHaveRegistered(ScenarioContext anvil) {
		Journey alice = Journey.of(anvil, Client.offline("Alice").through(HOLD_HOST)).joinsHeld();
		Administrator console = Administrator.at(anvil, PROXY);

		console.answersFor("Alice", "pass " + PASSWORD);
		alice.isHeld();
		console.answersFor("Alice", "passconfirm " + PASSWORD);

		alice.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.neverJoins(AUTH);
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, addon = true)
	void keepsAHeldPlayerConnectedPastTheReadTimeout(ScenarioContext anvil) {
		Journey alice = Journey.of(anvil, Client.offline("Alice").through(HOLD_HOST)).joinsHeld()
				.staysHeld(PAST_THE_READ_TIMEOUT);
		Administrator console = Administrator.at(anvil, PROXY);

		console.answersFor("Alice", "pass " + PASSWORD);
		console.answersFor("Alice", "passconfirm " + PASSWORD);

		alice.on(LOBBY);
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, addon = true)
	void releasesAPlayerWhoLeftWhileHeld(ScenarioContext anvil) {
		Journey.of(anvil, Client.offline("Alice").through(HOLD_HOST)).joinsHeld().leave();

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY);
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, addon = true)
	void asksAPlayerOnTheStepsServerWhereTheCheckpointHoldsThemThere(ScenarioContext anvil) {
		Journey alice = Journey.of(anvil, Client.offline("Alice").through(ASK_HOST)).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());
		Administrator console = Administrator.at(anvil, PROXY);

		console.answersFor("Alice", "pass " + PASSWORD);
		console.answersFor("Alice", "passconfirm " + PASSWORD);

		alice.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}
}
