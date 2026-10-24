package me.whereareiam.identica.testing.security;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelSettings;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * On a network whose sentinel allows two accounts per address, players register from one address until the limit
 * refuses the next registration before it asks for a password; another address still registers, and deleting an
 * account frees its slot.
 */
@Tag("account")
class AccountsPerAddressTest {
	private static final String PASSWORD = "Secret-123";
	private static final String OTHER_ADDRESS = "127.0.0.2";

	@Test
	@Identica(providers = Provider.CREDENTIAL, configure = TwoAccounts.class)
	void refusesARegistrationOnceTheAddressReachedTheLimit(ScenarioContext anvil) {
		registers(anvil, Client.offline("Alice"));
		registers(anvil, Client.offline("Bob"));

		Journey.offline(anvil, "Carol").attempt()
				.kickedWith(m -> m.sentinel().getAccountsPerAddress().getDenied());

		registers(anvil, Client.offline("Dave").from(OTHER_ADDRESS));
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL, configure = TwoAccounts.class)
	void letsAPlayerRegisterOnceAnAccountOfTheAddressIsDeleted(ScenarioContext anvil) {
		registers(anvil, Client.offline("Alice"));
		registers(anvil, Client.offline("Bob"));

		Administrator.at(anvil, PROXY).deletes("Alice");

		registers(anvil, Client.offline("Carol"));
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL, configure = AllowedLoopback.class)
	void neverLimitsAnAllowedAddress(ScenarioContext anvil) {
		registers(anvil, Client.offline("Alice"));
		registers(anvil, Client.offline("Bob"));
		registers(anvil, Client.offline("Carol"));
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL, configure = TwoAccounts.class)
	void letsAPlayerOfTheAddressLogInPastTheLimit(ScenarioContext anvil) {
		Journey alice = registers(anvil, Client.offline("Alice"));
		registers(anvil, Client.offline("Bob"));

		alice.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);
	}

	private static Journey registers(ScenarioContext anvil, Client client) {
		return Journey.of(anvil, client).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.leave();
	}

	private static final class TwoAccounts implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			SentinelSettings.AccountsPerAddress accountsPerAddress = proxy.sentinel().getSentinels().getAccountsPerAddress();
			accountsPerAddress.setEnabled(true);
			accountsPerAddress.setMaxAccounts(2);
		}
	}

	private static final class AllowedLoopback implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			new TwoAccounts().accept(proxy);
			proxy.sentinel().getSentinels().getAccountsPerAddress().setAllowlist(List.of("127.0.0.0/8"));
		}
	}
}
