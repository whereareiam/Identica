package me.whereareiam.identica.testing.administration;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * An administrator asks to delete or clear an account and then does not confirm it. Until a request is confirmed
 * in time, the player keeps the account and stays online.
 */
@Tag("operations")
class AccountRemovalConfirmationTest {
	private static final String PASSWORD = "Secret-123";
	private static final Duration CONFIRM_TTL = Duration.ofSeconds(2);

	@Test
	@Identica(providers = Provider.CREDENTIAL)
	void cancellingADeletionKeepsTheAccount(ScenarioContext anvil) {
		Journey alice = online(anvil);

		Administrator.at(anvil, PROXY)
				.requestsDeletionOf("Alice")
				.cancelsDeletion()
				.findsNoDeletionToConfirm();

		alice.remainsOn(LOBBY);
		logsInAgain(alice);
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL, configure = ShortConfirmation.class)
	void aDeletionConfirmedTooLateDeletesNothing(ScenarioContext anvil) {
		Journey alice = online(anvil);
		Administrator administrator = Administrator.at(anvil, PROXY).requestsDeletionOf("Alice");

		alice.waits(CONFIRM_TTL);
		administrator.confirmsExpiredDeletion().findsNoDeletionToConfirm();

		alice.remainsOn(LOBBY);
		logsInAgain(alice);
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL)
	void cancellingAClearKeepsTheAccount(ScenarioContext anvil) {
		Journey alice = online(anvil);

		Administrator.at(anvil, PROXY)
				.requestsClearOf("Alice")
				.cancelsClear()
				.findsNoClearToConfirm();

		alice.remainsOn(LOBBY);
		logsInAgain(alice);
	}

	private static Journey online(ScenarioContext anvil) {
		return Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);
	}

	/**
	 * The account is still there: the player is asked for the password, not to register.
	 */
	private static void logsInAgain(Journey alice) {
		alice.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);
	}

	static final class ShortConfirmation implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.commands().getBehavior().getClear().setConfirmTtl(CONFIRM_TTL);
		}
	}
}
