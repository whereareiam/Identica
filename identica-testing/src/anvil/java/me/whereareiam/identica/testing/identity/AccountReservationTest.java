package me.whereareiam.identica.testing.identity;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.util.UniqueIdGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * An administrator reserves a UUID for a username. The next account registered under that username gets the
 * reserved UUID instead of the one {@code uniqueIdMode} would assign, here a random one, and the reservation is
 * used up. An account registered from the console gets it too, which is how accounts moved from another plugin keep
 * their UUIDs.
 */
@Tag("identity")
class AccountReservationTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(providers = Provider.CREDENTIAL)
	void givesANewPlayerTheReservedUuid(ScenarioContext anvil) {
		UUID reserved = offline("Alice");
		Administrator.at(anvil, PROXY)
				.reserves("alice", reserved)
				.findsReservation("Alice", reserved);

		Journey alice = registered(Journey.offline(anvil, "Alice").join());

		assertEquals(reserved, alice.identity().getObserved().getUniqueId());
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL)
	void givesAnAccountRegisteredFromTheConsoleTheReservedUuid(ScenarioContext anvil) {
		UUID reserved = offline("Alice");
		Administrator.at(anvil, PROXY)
				.reserves("Alice", reserved)
				.registersCredential("Alice", PASSWORD)
				.findsNoReservation("Alice");

		Journey alice = Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);

		assertEquals(reserved, alice.identity().getObserved().getUniqueId());
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL)
	void givesTheReservedUuidToThePlayerOfADeletedAccount(ScenarioContext anvil) {
		Journey alice = loggedIn(Accounts.credential(anvil, "Alice", PASSWORD));
		UUID deleted = alice.identity().getObserved().getUniqueId();
		UUID reserved = offline("Alice");
		assertNotEquals(reserved, deleted);

		Administrator admin = Administrator.at(anvil, PROXY).deletes("Alice");
		alice.kickedWith(m -> m.identica().getCommands().getAdmin().getDelete().getDisconnect());
		admin.reserves("Alice", reserved);

		registered(alice.rejoin());
		assertEquals(reserved, alice.identity().getObserved().getUniqueId());
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL)
	void usesTheReservationUpOnRegistration(ScenarioContext anvil) {
		UUID reserved = offline("Alice");
		Administrator admin = Administrator.at(anvil, PROXY).reserves("Alice", reserved);

		registered(Journey.offline(anvil, "Alice").join());

		admin.findsNoReservation("Alice");
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL)
	void forgetsADeletedReservation(ScenarioContext anvil) {
		UUID reserved = offline("Alice");
		Administrator.at(anvil, PROXY)
				.reserves("Alice", reserved)
				.deletesReservation("Alice")
				.findsNoReservation("Alice");

		Journey alice = registered(Journey.offline(anvil, "Alice").join());

		assertNotEquals(reserved, alice.identity().getObserved().getUniqueId());
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL)
	void leavesOtherUsernamesTheirOwnUuid(ScenarioContext anvil) {
		UUID reserved = offline("Alice");
		Administrator.at(anvil, PROXY).reserves("Alice", reserved);

		Journey bob = registered(Journey.offline(anvil, "Bob").join());

		assertNotEquals(reserved, bob.identity().getObserved().getUniqueId());
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
}
