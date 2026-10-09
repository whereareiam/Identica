package me.whereareiam.identica.testing.fixture;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.IdenticaNetwork;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.testing.journey.Prompt;

/**
 * Creates accounts that a test about returning players starts from.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Accounts {
	/**
	 * Registers a Credential account the way a player does, in whichever journey mode the network uses, and
	 * leaves the network. The returned journey continues with the same player.
	 */
	public static Journey credential(ScenarioContext anvil, String username, String password) {
		Journey player = Journey.offline(anvil, username).join().on(IdenticaNetwork.AUTH);
		if (player.seesAnyOf(Prompt.PROVIDER_CHOICE, Prompt.REGISTRATION_PASSWORD) == Prompt.PROVIDER_CHOICE)
			player.enroll(Provider.CREDENTIAL).sees(Prompt.REGISTRATION_PASSWORD);

		return player.register(password)
				.on(IdenticaNetwork.LOBBY)
				.sees(Prompt.REGISTERED_WITH_CREDENTIAL)
				.leave();
	}
}
