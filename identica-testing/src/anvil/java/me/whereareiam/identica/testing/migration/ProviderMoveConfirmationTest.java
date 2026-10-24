package me.whereareiam.identica.testing.migration;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyChoice;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * The owner of a premium account asks to move the account to the other provider and then cancels it. A cancelled
 * move, or a cancel without a move, leaves the account where it was.
 */
@Tag("operations")
class ProviderMoveConfirmationTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(choice = JourneyChoice.PROMPTED)
	void cancellingAMoveToPremiumKeepsThePassword(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		registeredWithCredential(anvil, yggdrasil)
				.command("premium")
				.sees(m -> m.premium().getCommands().getPremium().getConfirm())
				.command("premium cancel")
				.sees(m -> m.premium().getCommands().getPremium().getCancelled())
				.command("premium confirm")
				.sees(m -> m.premium().getCommands().getPremium().getNoPending())
				.remainsOn(LOBBY)
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);
	}

	@Test
	@Identica(choice = JourneyChoice.PROMPTED)
	void cancellingWithoutAMoveFindsNothingPending(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		registeredWithCredential(anvil, yggdrasil)
				.command("premium cancel")
				.sees(m -> m.premium().getCommands().getPremium().getNoPending())
				.command("credential cancel")
				.sees(m -> m.credential().getCommands().getCredential().getNoPending())
				.remainsOn(LOBBY);
	}

	@Test
	@Identica(choice = JourneyChoice.PROMPTED)
	void cancellingAMoveBackToCredentialKeepsPremium(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		registeredWithCredential(anvil, yggdrasil)
				.command("premium")
				.sees(m -> m.premium().getCommands().getPremium().getConfirm())
				.command("premium confirm")
				.kickedWith(m -> m.premium().getCommands().getPremium().getConfirmed())
				.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getMigration().getBody())
				.command("credential")
				.sees(m -> m.credential().getCommands().getCredential().getConfirm())
				.command("credential cancel")
				.sees(m -> m.credential().getCommands().getCredential().getCancelled())
				.command("credential confirm")
				.sees(m -> m.credential().getCommands().getCredential().getNoPending())
				.remainsOn(LOBBY)
				.leave()
				.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getAuthentication().getBody());
	}

	private static Journey registeredWithCredential(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		return Journey.premium(anvil, account).join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.enroll(Provider.CREDENTIAL)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}
}
