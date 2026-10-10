package me.whereareiam.identica.testing.startup;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A proxy starts without files that an administrator would not have written yet.
 */
class FreshInstallTest {
	@Test
	@Identica(configure = NoProvidersFile.class)
	void writesTheOfficialProvidersWithTheirDefaultsAndLetsPlayersRegister(ScenarioContext anvil) {
		Providers providers = ProxyConfiguration.generated(anvil, "providers/providers.yml", Providers.class);

		assertEquals(List.of("credential", "premium"), providers.getProviders().stream().map(Providers.ProviderEntry::getId).sorted().toList());
		for (Providers.ProviderEntry provider : providers.getProviders()) {
			assertTrue(provider.isEnabled(), provider.getId() + " must be enabled");
			assertTrue(provider.getPriority() > 0, provider.getId() + " must keep its default priority");
			assertFalse(provider.getEntrypoints().isEmpty(), provider.getId() + " must keep its default entrypoint");
		}

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());
	}

	private static final class NoProvidersFile implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.omit("providers/providers.yml");
		}
	}
}
