package me.whereareiam.identica.testing.join;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.JourneyMode;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.MojangProfiles;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.testing.journey.Prompt;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
				.sees(Prompt.REGISTRATION_PASSWORD)
				.register(PASSWORD)
				.on(LOBBY)
				.sees(Prompt.REGISTERED_WITH_CREDENTIAL);
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void refusesThePremiumUsernameOnceThenFallsBackToCredential(ScenarioContext anvil) {
		MojangProfiles.premium("Alice");

		Journey alice = Journey.offline(anvil, "Alice").attempt();
		alice.refused();

		alice.rejoin()
				.on(AUTH)
				.sees(Prompt.REGISTRATION_PASSWORD)
				.register(PASSWORD)
				.on(LOBBY)
				.sees(Prompt.REGISTERED_WITH_CREDENTIAL);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE)
	void offersBothProvidersForAPremiumUsernameInInteractiveMode(ScenarioContext anvil) {
		MojangProfiles.premium("Alice");

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(Prompt.PROVIDER_CHOICE, Prompt.PREMIUM_OFFERED, Prompt.CREDENTIAL_OFFERED)
				.enroll(Provider.CREDENTIAL)
				.sees(Prompt.REGISTRATION_PASSWORD)
				.register(PASSWORD)
				.on(LOBBY)
				.sees(Prompt.REGISTERED_WITH_CREDENTIAL);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE)
	void offersOnlyCredentialWhenTheUsernameIsNotPremium(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(Prompt.PROVIDER_CHOICE, Prompt.CREDENTIAL_OFFERED)
				.without(Prompt.PREMIUM_OFFERED);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE)
	void sendsAnOfflinePlayerWhoChoosesPremiumBackToTheProviderChoice(ScenarioContext anvil) {
		MojangProfiles.premium("Alice");

		Journey alice = Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(Prompt.PROVIDER_CHOICE, Prompt.PREMIUM_OFFERED)
				.enroll(Provider.PREMIUM);
		assertTrue(alice.kicked().contains(Prompt.PREMIUM_REJOIN_TO_VERIFY.getText()));

		alice.attemptAgain().refused();

		alice.rejoin()
				.on(AUTH)
				.sees(Prompt.PROVIDER_CHOICE, Prompt.CREDENTIAL_OFFERED);
	}

	/**
	 * Documents today's behavior: when the profile lookup fails, a premium username is treated like any other,
	 * so an offline player can register it with Credential.
	 */
	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void treatsAPremiumUsernameAsOrdinaryWhileTheLookupIsUnavailable(ScenarioContext anvil) {
		MojangProfiles.premium("Alice");
		MojangProfiles.unavailable();

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(Prompt.REGISTRATION_PASSWORD);
	}
}
