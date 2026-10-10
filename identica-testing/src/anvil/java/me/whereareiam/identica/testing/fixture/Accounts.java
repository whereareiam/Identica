package me.whereareiam.identica.testing.fixture;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.IdenticaNetwork;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Journey;

import java.time.Duration;

/**
 * Creates accounts that a test about returning players starts from.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Accounts {
	/**
	 * Registers a Credential account from the proxy console, as an administrator does, and waits until the
	 * proxy confirms it. The player has never joined.
	 */
	public static void credentialByAdmin(ScenarioContext anvil, String username, String password) {
		ProcessConsole console = anvil.processes().proxy(IdenticaNetwork.PROXY).console();
		long before = console.checkpoint();
		console.sendCommand("identica credential register " + username + " " + password);
		console.await("Credential account registered.", before, Duration.ofSeconds(15));
	}

	/**
	 * Registers a Credential account the way a player does, in whichever journey mode the network uses, and
	 * leaves the network. The returned journey continues with the same player.
	 */
	public static Journey credential(ScenarioContext anvil, String username, String password) {
		return register(Journey.offline(anvil, username), password);
	}

	/**
	 * Registers a Credential account through one proxy of a cluster and leaves the network.
	 */
	public static Journey credential(ScenarioContext anvil, String username, String password, String proxy) {
		return register(Journey.offline(anvil, username, proxy), password);
	}

	private static Journey register(Journey client, String password) {
		Journey player = client.join().on(IdenticaNetwork.AUTH);
		int asked = player.seesAnyOf(
				m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody(),
				m -> m.credential().getScenario().getRegistration().getPrompt());
		if (asked == 0)
			player.enroll(Provider.CREDENTIAL).sees(m -> m.credential().getScenario().getRegistration().getPrompt());

		return player.register(password)
				.on(IdenticaNetwork.LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.leave();
	}
}
