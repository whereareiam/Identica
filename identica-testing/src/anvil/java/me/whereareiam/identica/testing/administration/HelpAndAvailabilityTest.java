package me.whereareiam.identica.testing.administration;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * The commands every player may use: the command help, which lists only what the reader may run, and whether a
 * username is taken.
 */
@Tag("operations")
class HelpAndAvailabilityTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void helpListsOnlyTheCommandsTheReaderMayRun(ScenarioContext anvil) {
		loggedIn(anvil, "Alice")
				.command("identica help")
				.sees(m -> m.identica().getCommands().getHelp().getFormat())
				.with(m -> "/identica availability username")
				.without(m -> "/identica admin");

		Administrator.at(anvil, PROXY).readsHelpWith("/identica admin");
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void tellsWhetherAUsernameIsTaken(ScenarioContext anvil) {
		Accounts.credential(anvil, "Bob", PASSWORD);

		loggedIn(anvil, "Alice")
				.command("identica availability username bob")
				.sees(m -> m.identica().getCommands().getAvailability().getUsername().getTaken())
				.waitsForCooldown()
				.command("identica availability username Carol")
				.sees(m -> m.identica().getCommands().getAvailability().getUsername().getFree());
	}

	private static Journey loggedIn(ScenarioContext anvil, String username) {
		return Accounts.credential(anvil, username, PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);
	}
}
