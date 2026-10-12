package me.whereareiam.identica.testing.routing;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

/**
 * Players join networks whose step server does not exist. The registration scenario has a message of its own
 * for that; authentication keeps the shared {@code routing.missingServer}.
 */
class MissingRoutingTargetJoinTest {
	private static final String MISSING = "nowhere";
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, step = MISSING, configure = RegistrationMessage.class)
	void registeringPlayerSeesTheRegistrationMessage(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").attempt()
				.kickedWith(m -> m.identica().getScenarios().getRegistration().getRouting().getMissingServer());
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL, step = MISSING, configure = RegistrationMessage.class)
	void loggingInPlayerSeesTheSharedMessage(ScenarioContext anvil) {
		Accounts.credentialByAdmin(anvil, "Alice", PASSWORD);

		Journey.offline(anvil, "Alice").attempt()
				.kickedWith(m -> m.identica().getRouting().getMissingServer());
	}

	static final class RegistrationMessage implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.messages().getScenarios().getRegistration().getRouting()
					.setMissingServer(List.of("<white>Registration happens on a server that is not there.</white>"));
		}
	}
}
