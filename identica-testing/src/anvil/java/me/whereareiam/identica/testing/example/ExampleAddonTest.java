package me.whereareiam.identica.testing.example;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
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
 * The example addon of the developer documentation, a separate Velocity plugin built on Identica's API, holds back
 * chat and commands until a player has authenticated. It learns that from the pending scenario service, takes the
 * commands it lets through from the definitions marked available while pending, such as {@code /identica enroll}
 * and Credential's {@code /pass}, and reads them again when Identica reloads.
 */
@Tag("operations")
class ExampleAddonTest {
	private static final String PASSWORD = "Secret-123";
	private static final String CHAT_HELD_BACK = "Log in before you chat.";
	private static final String COMMAND_HELD_BACK = "Log in before you use this command.";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, addon = true)
	void holdsBackChatUntilThePlayerHasRegistered(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.enroll(Provider.CREDENTIAL)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.chat("hello before registering")
				.doesNotSeeText("hello before registering")
				.seesText(CHAT_HELD_BACK)
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.chat("hello after registering")
				.seesText("hello after registering")
				.doesNotSeeText(CHAT_HELD_BACK);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, addon = true)
	void holdsBackOtherCommandsButLetsTheRegistrationCommandsThrough(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.enroll(Provider.CREDENTIAL)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.command("server " + LOBBY)
				.seesText(COMMAND_HELD_BACK)
				.remainsOn(AUTH)
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, addon = true)
	void startsOnceTheProvidersAreLoaded(ScenarioContext anvil) {
		// Credential's commands only exist once Identica has loaded its providers
		Administrator.at(anvil, PROXY)
				.seesOnConsole("Example addon allows", "login", "passconfirm", "identica enroll");
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, addon = true)
	void readsTheCommandsAndMessagesAgainWhenIdenticaReloads(ScenarioContext anvil) {
		Administrator.at(anvil, PROXY)
				.reloads()
				.seesOnConsole("Example addon allows", "login", "identica enroll", "message documents");
	}
}
