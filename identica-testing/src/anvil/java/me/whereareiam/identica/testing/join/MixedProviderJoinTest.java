package me.whereareiam.identica.testing.join;

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
 * An offline player joins a network with both providers, where Premium outranks Credential. Whether the player's
 * username belongs to a premium account decides what happens.
 */
@Tag("join")
class MixedProviderJoinTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica
	void registersWithCredentialWhenTheUsernameIsNotPremium(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica
	void refusesThePremiumUsernameOnceThenFallsBackToCredential(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		yggdrasil.register("Alice");

		Journey alice = Journey.offline(anvil, "Alice").attempt();
		alice.authenticationRequired();

		alice.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(choice = JourneyChoice.PROMPTED)
	void offersBothProvidersForAPremiumUsernameWhenAsked(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		yggdrasil.register("Alice");

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.with(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getDescriptions().get("premium"))
				.with(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getDescriptions().get("credential"))
				.enroll(Provider.CREDENTIAL)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(choice = JourneyChoice.PROMPTED)
	void registersWithCredentialWithoutAChoiceWhenTheUsernameIsNotPremium(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.doesNotSee(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());
	}

	@Test
	@Identica(choice = JourneyChoice.PROMPTED)
	void sendsAnOfflinePlayerWhoChoosesPremiumOnToCredential(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		yggdrasil.register("Alice");

		Journey alice = Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.with(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getDescriptions().get("premium"))
				.enroll(Provider.PREMIUM)
				.kickedWith(m -> m.premium().getVerification().getRejoin());

		alice.attemptAgain().authenticationRequired();

		alice.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());
	}

	/**
	 * Documents today's behavior: when the profile lookup fails, a premium username is treated like any other,
	 * so an offline player can register it with Credential.
	 */
	@Test
	@Identica
	void treatsAPremiumUsernameAsOrdinaryWhileTheLookupIsUnavailable(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		yggdrasil.register("Alice");
		yggdrasil.available(false);

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());
	}
}
