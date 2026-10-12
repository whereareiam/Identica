package me.whereareiam.identica.testing.platform;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Addon;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Platform;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.ASK_HOST;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.HOLD_HOST;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * The example addon's checkpoints hold players on BungeeCord, and the console answers their steps. Players who join
 * through {@link me.whereareiam.identica.testing.environment.IdenticaNetwork#HOLD_HOST} wait on the loading screen, in
 * the configuration of the first server BungeeCord connects them to, and enter no server's world until the journey
 * has finished; replies to them wait until they have. A journey that completes on another server switches them there
 * once they have entered the first. Players who join through
 * {@link me.whereareiam.identica.testing.environment.IdenticaNetwork#ASK_HOST} wait on the step's server, in its
 * world.
 */
@Tag("platform")
class BungeeCordCheckpointTest {
	private static final String PASSWORD = "Secret-123";
	/** Longer than BungeeCord's read timeout, which closes a connection whose client sends nothing for 30 seconds. */
	private static final Duration PAST_THE_READ_TIMEOUT = Duration.ofSeconds(35);

	@Test
	@Identica(platform = Platform.BUNGEECORD, providers = Provider.CREDENTIAL, addons = Addon.EXAMPLE)
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
	@Identica(platform = Platform.BUNGEECORD, providers = Provider.CREDENTIAL, addons = Addon.EXAMPLE)
	void logsInWhileHeldAndReadsTheAnswersOnceJoined(ScenarioContext anvil) {
		Accounts.credentialByAdmin(anvil, "Alice", PASSWORD);
		Journey alice = Journey.of(anvil, Client.offline("Alice").through(HOLD_HOST)).joinsHeld();
		Administrator console = Administrator.at(anvil, PROXY);

		console.answersFor("Alice", "login Wrong-456");
		alice.isHeld();
		console.answersFor("Alice", "login " + PASSWORD);

		alice.on(LOBBY)
				.sees(m -> m.credential().getScenario().getAuthentication().getStatus().getInvalid())
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(platform = Platform.BUNGEECORD, providers = Provider.CREDENTIAL, addons = Addon.EXAMPLE)
	void keepsAHeldPlayerConnectedPastTheReadTimeout(ScenarioContext anvil) {
		Journey alice = Journey.of(anvil, Client.offline("Alice").through(HOLD_HOST)).joinsHeld()
				.staysHeld(PAST_THE_READ_TIMEOUT);
		Administrator console = Administrator.at(anvil, PROXY);

		console.answersFor("Alice", "pass " + PASSWORD);
		console.answersFor("Alice", "passconfirm " + PASSWORD);

		alice.on(LOBBY);
	}

	@Test
	@Identica(platform = Platform.BUNGEECORD, providers = Provider.CREDENTIAL, addons = Addon.EXAMPLE,
			complete = AUTH)
	void sendsAHeldPlayerOnToAnotherCompletionServer(ScenarioContext anvil) {
		Journey alice = Journey.of(anvil, Client.offline("Alice").through(HOLD_HOST)).joinsHeld();
		Administrator console = Administrator.at(anvil, PROXY);

		console.answersFor("Alice", "pass " + PASSWORD);
		console.answersFor("Alice", "passconfirm " + PASSWORD);

		alice.on(LOBBY)
				.on(AUTH)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(platform = Platform.BUNGEECORD, providers = Provider.CREDENTIAL, addons = Addon.EXAMPLE)
	void releasesAPlayerWhoLeftWhileHeld(ScenarioContext anvil) {
		Journey.of(anvil, Client.offline("Alice").through(HOLD_HOST)).joinsHeld().leave();

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY);
	}

	@Test
	@Identica(platform = Platform.BUNGEECORD, providers = Provider.CREDENTIAL, addons = Addon.EXAMPLE)
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
