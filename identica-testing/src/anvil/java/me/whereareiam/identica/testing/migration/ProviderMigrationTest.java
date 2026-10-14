package me.whereareiam.identica.testing.migration;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * The owner of a premium account first registers with a Credential password, then moves the account to Premium
 * with the provider's own command. The account exists in the network's stand-in for Mojang, which verifies the
 * premium login. A player who cannot give that login keeps signing in with Credential.
 */
@Tag("operations")
class ProviderMigrationTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE)
	void movesACredentialAccountToPremium(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.premium(anvil, account).join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.enroll(Provider.CREDENTIAL)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.command("premium")
				.sees(m -> m.premium().getCommands().getPremium().getConfirm())
				.command("premium confirm")
				.kickedWith(m -> m.premium().getCommands().getPremium().getConfirmed())
				.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getMigration().getBody())
				.leave()
				.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE)
	void movesACredentialAccountToPremiumAndBackWithItsExistingPassword(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.premium(anvil, account).join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.enroll(Provider.CREDENTIAL)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.command("premium")
				.sees(m -> m.premium().getCommands().getPremium().getConfirm())
				.command("premium confirm")
				.kickedWith(m -> m.premium().getCommands().getPremium().getConfirmed())
				.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getMigration().getBody())
				.command("credential")
				.sees(m -> m.credential().getCommands().getCredential().getConfirm())
				.command("credential confirm")
				.kickedWith(m -> m.credential().getCommands().getCredential().getConfirmed())
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getMigration().getVerification().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getMigration().getBody())
				.doesNotSee(m -> m.identica().getScenarios().getMigration().getCancelled())
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	/**
	 * An offline player registered a premium username with Credential and asks to move to Premium. The rejoin asks for
	 * the premium login it cannot give; the join after it cancels the move and signs in with the password instead of
	 * asking for the premium login again.
	 */
	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void fallsBackToCredentialWhenThePremiumLoginOfAMoveFails(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		yggdrasil.register("Alice");
		Journey alice = Journey.offline(anvil, "Alice").attempt();
		alice.authenticationRequired();

		alice.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.command("premium")
				.sees(m -> m.premium().getCommands().getPremium().getConfirm())
				.command("premium confirm")
				.kickedWith(m -> m.premium().getCommands().getPremium().getConfirmed());

		alice.attemptAgain().authenticationRequired();

		alice.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.sees(m -> m.identica().getScenarios().getMigration().getCancelled())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}
}
