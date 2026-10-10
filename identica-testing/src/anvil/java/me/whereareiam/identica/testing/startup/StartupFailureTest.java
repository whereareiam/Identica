package me.whereareiam.identica.testing.startup;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

/**
 * Identica cannot finish its startup. The proxy keeps running, so Identica must keep everyone out.
 */
class StartupFailureTest {
	private static final String AUTHENTICATION_UNAVAILABLE = "multiplayer.disconnect.authservers_down";

	@Test
	@Identica(configure = UnreadableSettings.class)
	void refusesEveryLoginWhenAConfigurationFileCannotBeRead(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").attempt().refusedWith(AUTHENTICATION_UNAVAILABLE);
		Journey.offline(anvil, "Bob").attempt().refusedWith(AUTHENTICATION_UNAVAILABLE);
	}

	private static final class UnreadableSettings implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.corrupt("settings.yml", "level: [\n");
		}
	}
}
