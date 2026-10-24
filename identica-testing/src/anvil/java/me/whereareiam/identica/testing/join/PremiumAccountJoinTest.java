package me.whereareiam.identica.testing.join;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyChoice;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The owner of a premium account joins. The account exists in the network's stand-in for Mojang, which also verifies the
 * login, so these tests need no real account.
 */
@Tag("join")
class PremiumAccountJoinTest {
	@Test
	@Identica
	void joinsTheLobbyWithoutAnyPromptAndKeepsItsIdentityWhenReturning(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		Journey player = Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody());
		UUID registered = player.profile().getUniqueId();
		assertEquals(account.getUsername(), player.profile().getUsername());

		player.leave().rejoin().on(LOBBY).bypasses(AUTH);
		assertEquals(registered, player.profile().getUniqueId());
	}

	@Test
	@Identica(providers = Provider.PREMIUM)
	void joinsAndReturnsWhenPremiumIsTheOnlyProvider(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.leave()
				.rejoin()
				.on(LOBBY)
				.bypasses(AUTH);
	}

	@Test
	@Identica(choice = JourneyChoice.PROMPTED)
	void returnsToTheLobbyInInteractiveModeAfterRegisteringWithPremium(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

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
				.on(LOBBY)
				.bypasses(AUTH);
	}

	@Test
	@Identica(choice = JourneyChoice.PROMPTED)
	void choosesPremiumThenRejoinsToVerifyTheAccountWhenAsked(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

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
