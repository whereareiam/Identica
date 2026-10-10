package me.whereareiam.identica.testing.join;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.JourneyPolicy;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.testing.journey.Prompt;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * A new offline player joins a network whose only provider is Credential.
 */
class CredentialFirstJoinTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void choosesCredentialAndRegistersInInteractiveMode(ScenarioContext anvil) {
		registersAfterChoosingTheProvider(anvil);
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL)
	void selectsTheOnlyProviderItselfInSeamlessMode(ScenarioContext anvil) {
		registersWithoutChoosingTheProvider(anvil);
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, policy = JourneyPolicy.STRICT, providers = Provider.CREDENTIAL)
	void refusesAPlayerWhoWouldHaveToTypeInStrictSeamlessMode(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").attempt()
				.kickedWith(Prompt.INTERACTION_REQUIRED);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, autoSelectSingleProvider = true)
	void skipsTheProviderChoiceWhenTheSingleProviderIsSelectedAutomatically(ScenarioContext anvil) {
		registersWithoutChoosingTheProvider(anvil);
	}

	private void registersWithoutChoosingTheProvider(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(Prompt.REGISTRATION_PASSWORD)
				.register(PASSWORD)
				.on(LOBBY)
				.sees(Prompt.REGISTERED_WITH_CREDENTIAL);
	}

	private void registersAfterChoosingTheProvider(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(Prompt.PROVIDER_CHOICE, Prompt.CREDENTIAL_OFFERED)
				.enroll(Provider.CREDENTIAL)
				.sees(Prompt.REGISTRATION_PASSWORD)
				.register(PASSWORD)
				.on(LOBBY)
				.sees(Prompt.REGISTERED_WITH_CREDENTIAL);
	}
}
