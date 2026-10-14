package me.whereareiam.identica.testing.example;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * The example provider of the developer documentation, loaded from Identica's providers directory like the
 * official ones: a player registers and logs in with the passphrase in its settings, {@code OpenSesame} by
 * default. Identica creates the account, links it to the provider and opens the session.
 */
@Tag("operations")
class ExampleProviderTest {
	private static final String PASSPHRASE = "OpenSesame";

	@Test
	@Identica(providers = Provider.EXAMPLE)
	void registersAPlayerWhoTypesThePassphraseAndLogsThemInWhenTheyReturn(ScenarioContext anvil) {
		Journey alice = Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.example().getPrompt())
				.command("passphrase WrongGuess")
				.sees(m -> m.example().getInvalid())
				.remainsOn(AUTH)
				.command("passphrase " + PASSPHRASE)
				.on(LOBBY);

		alice.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.example().getPrompt())
				.command("passphrase " + PASSPHRASE)
				.on(LOBBY);
	}

	@Test
	@Identica(providers = Provider.EXAMPLE, addon = true)
	void theExampleAddonLetsThePassphraseCommandThroughAndHoldsBackChat(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.example().getPrompt())
				.chat("hello before the passphrase")
				.doesNotSeeText("hello before the passphrase")
				.seesText("Log in before you chat.")
				.command("passphrase " + PASSPHRASE)
				.on(LOBBY)
				.chat("hello after the passphrase")
				.seesText("hello after the passphrase");
	}
}
