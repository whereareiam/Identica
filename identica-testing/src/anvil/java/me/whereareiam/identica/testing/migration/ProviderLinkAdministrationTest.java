package me.whereareiam.identica.testing.migration;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * The owner of a premium account registered with a Credential password and moved the account to Premium, so it is
 * linked to both providers with Premium as the primary one. An administrator lists the links, changes the primary
 * one and drops the other.
 */
@Tag("operations")
class ProviderLinkAdministrationTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE)
	void listsBothLinksWithTheMovedToProviderAsPrimary(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		movedToPremium(anvil, yggdrasil);

		Administrator.at(anvil, PROXY)
				.listsLinksOf("Steve", Provider.PREMIUM, Provider.CREDENTIAL)
				.findsAlreadyPrimary("Steve", Provider.PREMIUM);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE)
	void droppingTheOtherLinkKeepsThePrimaryLogin(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		Journey steve = movedToPremium(anvil, yggdrasil).leave();

		Administrator.at(anvil, PROXY)
				.cannotDropPrimaryLink("Steve", Provider.PREMIUM)
				.dropsLink("Steve", Provider.CREDENTIAL)
				.findsNoLink("Steve", Provider.CREDENTIAL)
				.listsLinksOf("Steve", Provider.PREMIUM);

		steve.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE)
	void makingCredentialPrimaryAndDroppingPremiumAsksForThePassword(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		Journey steve = movedToPremium(anvil, yggdrasil).leave();

		Administrator.at(anvil, PROXY)
				.makesPrimary("Steve", Provider.CREDENTIAL)
				.listsLinksOf("Steve", Provider.CREDENTIAL, Provider.PREMIUM)
				.dropsLink("Steve", Provider.PREMIUM)
				.listsLinksOf("Steve", Provider.CREDENTIAL);

		steve.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	private static Journey movedToPremium(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		return Journey.premium(anvil, account).join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.enroll(Provider.CREDENTIAL)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.command("premium")
				.sees(m -> m.premium().getCommands().getPremium().getConfirm())
				.command("premium confirm")
				.kickedWith(m -> m.premium().getCommands().getPremium().getConfirmed())
				.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getMigration().getBody());
	}
}
