package me.whereareiam.identica.testing.identity;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.identity.UniqueIdMode;
import me.whereareiam.identica.util.UniqueIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The UUID a backend sees for a player is the UUID of the player's Identica account, which {@code uniqueIdMode}
 * assigns when the account is created. Clearing an account keeps that UUID for the next registration under the
 * username; deleting it does not.
 */
class UniqueIdAssignmentTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(providers = Provider.CREDENTIAL)
	void assignsARandomUuidThatTheAccountKeeps(ScenarioContext anvil) {
		Journey alice = loggedIn(Accounts.credential(anvil, "Alice", PASSWORD));
		UUID assigned = alice.identity().getObserved().getUniqueId();

		assertNotEquals(offline("Alice"), assigned, "RANDOM must not assign the offline UUID");
		assertEquals(8, assigned.version(), "RANDOM assigns an Identica UUID");

		loggedIn(alice.leave());
		assertEquals(assigned, alice.identity().getObserved().getUniqueId());
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL, configure = OfflineUniqueIds.class)
	void assignsTheOfflineUuidInOfflineMode(ScenarioContext anvil) {
		Journey alice = registered(Journey.offline(anvil, "Alice").join());

		assertEquals(offline("Alice"), alice.identity().getObserved().getUniqueId());
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL)
	void keepsTheUuidOfAClearedAccountForItsNextRegistration(ScenarioContext anvil) {
		Journey alice = loggedIn(Accounts.credential(anvil, "Alice", PASSWORD));
		UUID assigned = alice.identity().getObserved().getUniqueId();

		Administrator.at(anvil, PROXY).clears("Alice");
		alice.kickedWith(m -> m.identica().getCommands().getAdmin().getClear().getDisconnect());

		registered(alice.rejoin());
		assertEquals(assigned, alice.identity().getObserved().getUniqueId());
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL)
	void assignsANewUuidAfterTheAccountIsDeleted(ScenarioContext anvil) {
		Journey alice = loggedIn(Accounts.credential(anvil, "Alice", PASSWORD));
		UUID deleted = alice.identity().getObserved().getUniqueId();

		Administrator.at(anvil, PROXY).deletes("Alice");
		alice.kickedWith(m -> m.identica().getCommands().getAdmin().getDelete().getDisconnect());

		registered(alice.rejoin());
		assertNotEquals(deleted, alice.identity().getObserved().getUniqueId(), "a deleted account's UUID must not come back");
	}

	private static Journey loggedIn(Journey player) {
		return player.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);
	}

	private static Journey registered(Journey joined) {
		return joined.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	private static UUID offline(String username) {
		return UniqueIdGenerator.offlinePlayerUniqueId(username);
	}

	private static final class OfflineUniqueIds implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.settings().getIdentity().setUniqueIdMode(UniqueIdMode.OFFLINE);
		}
	}
}
