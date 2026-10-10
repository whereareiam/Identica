package me.whereareiam.identica.testing.join;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * An offline player joins a network with both providers, where Premium outranks Credential. Whether the player's
 * username belongs to a premium account decides what happens.
 */
class MixedProviderJoinTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void registersWithCredentialWhenTheUsernameIsNotPremium(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
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
	@Identica(mode = JourneyMode.INTERACTIVE)
	void offersBothProvidersForAPremiumUsernameInInteractiveMode(ScenarioContext anvil, YggdrasilMock yggdrasil) {
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
	@Identica(mode = JourneyMode.INTERACTIVE)
	void offersOnlyCredentialWhenTheUsernameIsNotPremium(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.with(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getDescriptions().get("credential"))
				.without(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getDescriptions().get("premium"));
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE)
	void sendsAnOfflinePlayerWhoChoosesPremiumBackToTheProviderChoice(ScenarioContext anvil, YggdrasilMock yggdrasil) {
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
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.with(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getDescriptions().get("credential"));
	}

	/**
	 * Documents today's behavior: when the profile lookup fails, a premium username is treated like any other,
	 * so an offline player can register it with Credential.
	 */
	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void treatsAPremiumUsernameAsOrdinaryWhileTheLookupIsUnavailable(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		yggdrasil.register("Alice");
		yggdrasil.available(false);

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());
	}
}
