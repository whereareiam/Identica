package me.whereareiam.identica.testing.dialogs;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Addon;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.MinecraftVersion;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * A player whose Minecraft is older than dialogs, which arrived in 1.21.6, joins a network that runs the dialogs
 * addon: the addon sends their client nothing it cannot show, and they register and log in through Identica's chat
 * prompts.
 */
@Tag("account")
class OlderClientTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(
			mode = JourneyMode.INTERACTIVE,
			providers = Provider.CREDENTIAL,
			autoSelectSingleProvider = true,
			addons = Addon.DIALOGS,
			minecraft = MinecraftVersion.V1_21_1
	)
	void registersAndLogsInThroughChatPrompts(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}
}
