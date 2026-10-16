package me.whereareiam.identica.testing.administration;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * A logged-in player without any permission tries the administrator's commands against another player's account.
 * None of them runs: the other account keeps its password and name, and no account is registered. On Velocity the
 * proxy treats a command the player may not use as unknown and passes it to the backend, so the player reads the
 * backend's answer rather than Identica's.
 */
@Tag("operations")
class AdminPermissionTest {
	private static final String PASSWORD = "Secret-123";
	private static final List<String> ADMIN_COMMANDS = List.of(
			"identica admin reload",
			"identica admin delete Bob",
			"identica admin clear Bob",
			"identica admin session end Bob",
			"identica admin reservation delete Bob",
			"identica admin username rename Bob Robert",
			"identica admin 2fa reset Bob",
			"identica credential register Carol " + PASSWORD,
			"identica credential setpassword Bob Taken-456",
			"identica migration list Bob",
			"identica migration start Bob premium",
			"identica migration primary Bob credential",
			"identica migration drop Bob credential"
	);

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void refusesEveryAdministratorCommandAndLeavesTheOtherAccountAlone(ScenarioContext anvil) {
		Journey bob = Accounts.credential(anvil, "Bob", PASSWORD);
		Journey alice = Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);

		for (String command : ADMIN_COMMANDS)
			alice.command(command)
					.doesNotSee(m -> m.identica().getCommands().getAdmin().getDelete().getConfirm());
		alice.remainsOn(LOBBY);

		bob.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.playsAs("Bob");
		Journey.offline(anvil, "Carol").join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody());
	}
}
