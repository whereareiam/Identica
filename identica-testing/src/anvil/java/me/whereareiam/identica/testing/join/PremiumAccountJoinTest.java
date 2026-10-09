package me.whereareiam.identica.testing.join;

import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.JourneyMode;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.MojangProfiles;
import me.whereareiam.identica.testing.fixture.PremiumAccount;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.testing.journey.Prompt;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A player signed in to a real premium account joins. These tests need the developer's stored account and skip
 * themselves without it.
 */
@Tag(PremiumAccount.TAG)
class PremiumAccountJoinTest {
	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void joinsTheLobbyWithoutAnyPromptAndKeepsItsIdentityWhenReturning(ScenarioContext anvil) {
		AuthenticationAccount account = premium(anvil);

		Journey player = Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(Prompt.REGISTERED_WITH_PREMIUM);
		UUID registered = player.identity().getObservedUniqueId();
		assertEquals(account.getUsername(), player.identity().getObservedUsername());

		player.leave().rejoin().on(LOBBY);
		assertEquals(registered, player.identity().getObservedUniqueId());
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.PREMIUM)
	void joinsAndReturnsWhenPremiumIsTheOnlyProvider(ScenarioContext anvil) {
		AuthenticationAccount account = premium(anvil);

		Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(Prompt.REGISTERED_WITH_PREMIUM)
				.leave()
				.rejoin()
				.on(LOBBY);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE)
	void returnsToTheLobbyInInteractiveModeAfterRegisteringWithPremium(ScenarioContext anvil) {
		AuthenticationAccount account = premium(anvil);

		Journey player = Journey.premium(anvil, account).join()
				.on(AUTH)
				.sees(Prompt.PROVIDER_CHOICE, Prompt.PREMIUM_OFFERED)
				.enroll(Provider.PREMIUM)
				.kickedWith(Prompt.PREMIUM_REJOIN_TO_VERIFY);

		player.rejoin()
				.on(LOBBY)
				.sees(Prompt.REGISTERED_WITH_PREMIUM)
				.leave()
				.rejoin()
				.on(LOBBY);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE)
	void choosesPremiumThenRejoinsToVerifyTheAccountInInteractiveMode(ScenarioContext anvil) {
		AuthenticationAccount account = premium(anvil);

		Journey player = Journey.premium(anvil, account).join()
				.on(AUTH)
				.sees(Prompt.PROVIDER_CHOICE, Prompt.PREMIUM_OFFERED, Prompt.CREDENTIAL_OFFERED)
				.enroll(Provider.PREMIUM)
				.kickedWith(Prompt.PREMIUM_REJOIN_TO_VERIFY);

		player.rejoin()
				.on(LOBBY)
				.sees(Prompt.REGISTERED_WITH_PREMIUM);
	}

	private static AuthenticationAccount premium(ScenarioContext anvil) {
		AuthenticationAccount account = PremiumAccount.require(anvil);
		MojangProfiles.premium(account.getUsername());
		return account;
	}
}
