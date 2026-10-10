package me.whereareiam.identica.testing.join;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Mojang;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The owner of a premium account joins. The account exists in the local Mojang service, which also verifies the
 * login, so these tests need no real account.
 */
class PremiumAccountJoinTest {
	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void joinsTheLobbyWithoutAnyPromptAndKeepsItsIdentityWhenReturning(ScenarioContext anvil) {
		SessionIdentity account = Mojang.service().register("Steve");

		Journey player = Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody());
		UUID registered = player.identity().getObservedUniqueId();
		assertEquals(account.getUsername(), player.identity().getObservedUsername());

		player.leave().rejoin().on(LOBBY);
		assertEquals(registered, player.identity().getObservedUniqueId());
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.PREMIUM)
	void joinsAndReturnsWhenPremiumIsTheOnlyProvider(ScenarioContext anvil) {
		SessionIdentity account = Mojang.service().register("Steve");

		Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.leave()
				.rejoin()
				.on(LOBBY);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE)
	void returnsToTheLobbyInInteractiveModeAfterRegisteringWithPremium(ScenarioContext anvil) {
		SessionIdentity account = Mojang.service().register("Steve");

		Journey player = Journey.premium(anvil, account).join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.with(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getDescriptions().get("premium"))
				.enroll(Provider.PREMIUM)
				.kickedWith(m -> m.premium().getVerification().getRejoin());

		player.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.leave()
				.rejoin()
				.on(LOBBY);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE)
	void choosesPremiumThenRejoinsToVerifyTheAccountInInteractiveMode(ScenarioContext anvil) {
		SessionIdentity account = Mojang.service().register("Steve");

		Journey player = Journey.premium(anvil, account).join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.with(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getDescriptions().get("premium"))
				.with(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getDescriptions().get("credential"))
				.enroll(Provider.PREMIUM)
				.kickedWith(m -> m.premium().getVerification().getRejoin());

		player.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody());
	}
}
