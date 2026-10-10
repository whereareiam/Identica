package me.whereareiam.identica.testing.administration;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * An administrator acts on an online player after the messages for it were emptied. Emptying a message
 * silences the text; the player is disconnected all the same.
 */
class SilentDisconnectTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = NoDisconnectMessages.class)
	void endingASessionDisconnectsThePlayer(ScenarioContext anvil) {
		Journey alice = online(anvil);

		Administrator.at(anvil, PROXY).endsSessionOf("Alice");

		alice.disconnected();
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = NoDisconnectMessages.class)
	void clearingAnAccountDisconnectsThePlayer(ScenarioContext anvil) {
		Journey alice = online(anvil);

		Administrator.at(anvil, PROXY).clears("Alice");

		alice.disconnected();
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = NoDisconnectMessages.class)
	void deletingAnAccountDisconnectsThePlayer(ScenarioContext anvil) {
		Journey alice = online(anvil);

		Administrator.at(anvil, PROXY).deletes("Alice");

		alice.disconnected();
	}

	private static Journey online(ScenarioContext anvil) {
		return Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);
	}

	private static final class NoDisconnectMessages implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			Messages.Commands.Admin admin = proxy.messages().getCommands().getAdmin();
			admin.getSessions().getEnd().setDisconnect(List.of());
			admin.getClear().setDisconnect(List.of());
			admin.getDelete().setDisconnect(List.of());
		}
	}
}
