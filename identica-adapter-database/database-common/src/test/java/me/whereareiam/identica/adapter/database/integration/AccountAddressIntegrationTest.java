package me.whereareiam.identica.adapter.database.integration;

import me.whereareiam.identica.adapter.database.DefaultDatabaseService;
import me.whereareiam.identica.adapter.database.account.DefaultAccountPersistenceService;
import me.whereareiam.identica.adapter.database.address.DefaultAccountAddressPersistenceService;
import me.whereareiam.identica.adapter.database.testing.PersistenceServices;
import me.whereareiam.identica.adapter.database.testing.TestDataFactory;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabase;
import me.whereareiam.identica.model.identity.address.AccountAddress;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The addresses accounts register and log in from, one row per account and address.
 */
@DisplayName("Account addresses")
@Tag("database")
class AccountAddressIntegrationTest {
	private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final String HOME = "203.0.113.7";
	private static final String HOME_NETWORK = "203.0.113.7/32";
	private static final String PHONE = "2001:db8:1:2::5";
	private static final String PHONE_NETWORK = "2001:db8:1:2::/64";

	@DisplayName("Keeps the first use and the registration of an address when the account logs in from it again")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void keepsTheRegistrationOfAnAddressUsedAgain(TestDatabase database) {
		withServices(database, (accounts, addresses) -> {
			createAccounts(accounts);
			addresses.record(address(ALICE, HOME, HOME_NETWORK, true, 100));

			addresses.record(address(ALICE, HOME, HOME_NETWORK, false, 200));

			AccountAddress stored = addresses.findAll().getFirst();
			assertEquals(true, stored.isRegistration());
			assertEquals(100, stored.getFirstSeenAt());
			assertEquals(200, stored.getLastSeenAt());
		});
	}

	@DisplayName("Finds the registrations from a group of addresses since a time, oldest first, one per account")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void findsTheRegistrationsFromAGroup(TestDatabase database) {
		withServices(database, (accounts, addresses) -> {
			createAccounts(accounts);
			addresses.record(address(BOB, PHONE, PHONE_NETWORK, true, 300));
			addresses.record(address(ALICE, "2001:db8:1:2::9", PHONE_NETWORK, true, 100));
			addresses.record(address(ALICE, HOME, HOME_NETWORK, false, 400));

			assertEquals(List.of(100L, 300L), addresses.findRegistrationTimes(PHONE_NETWORK, 0));
			assertEquals(List.of(300L), addresses.findRegistrationTimes(PHONE_NETWORK, 200));
			assertEquals(List.of(), addresses.findRegistrationTimes(HOME_NETWORK, 0));
		});
	}

	@DisplayName("Deletes the idle addresses an account did not register from")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void deletesIdleAddresses(TestDatabase database) {
		withServices(database, (accounts, addresses) -> {
			createAccounts(accounts);
			addresses.record(address(ALICE, HOME, HOME_NETWORK, true, 100));
			addresses.record(address(ALICE, PHONE, PHONE_NETWORK, false, 100));
			addresses.record(address(BOB, PHONE, PHONE_NETWORK, false, 300));

			assertEquals(1, addresses.deleteIdle(200));

			assertEquals(List.of(ALICE + " " + HOME, BOB + " " + PHONE), rows(addresses));
		});
	}

	@DisplayName("Lets registration addresses expire into ordinary ones")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void expiresRegistrations(TestDatabase database) {
		withServices(database, (accounts, addresses) -> {
			createAccounts(accounts);
			addresses.record(address(ALICE, HOME, HOME_NETWORK, true, 100));
			addresses.record(address(BOB, HOME, HOME_NETWORK, true, 300));

			assertEquals(1, addresses.expireRegistrations(200));

			assertEquals(List.of(300L), addresses.findRegistrationTimes(HOME_NETWORK, 0));
			assertEquals(1, addresses.deleteIdle(200));
		});
	}

	@DisplayName("Keeps the most recently used addresses of an account and its registration address")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void trimsTheOldestAddresses(TestDatabase database) {
		withServices(database, (accounts, addresses) -> {
			createAccounts(accounts);
			addresses.record(address(ALICE, "198.51.100.1", "198.51.100.1/32", true, 50));
			addresses.record(address(ALICE, "198.51.100.2", "198.51.100.2/32", false, 100));
			addresses.record(address(ALICE, "198.51.100.3", "198.51.100.3/32", false, 300));
			addresses.record(address(ALICE, "198.51.100.4", "198.51.100.4/32", false, 200));
			addresses.record(address(BOB, "198.51.100.2", "198.51.100.2/32", false, 100));

			assertEquals(1, addresses.trim(ALICE, 2));

			assertEquals(List.of(
					ALICE + " 198.51.100.1",
					ALICE + " 198.51.100.3",
					ALICE + " 198.51.100.4",
					BOB + " 198.51.100.2"
			), rows(addresses));
		});
	}

	@DisplayName("Replaces the stored form of an address, merging it into a row of the new form")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void replacesTheStoredForm(TestDatabase database) {
		withServices(database, (accounts, addresses) -> {
			createAccounts(accounts);
			addresses.record(address(ALICE, HOME, HOME_NETWORK, true, 100));
			addresses.record(address(ALICE, "hash-of-home", "hash-of-network", false, 50));
			addresses.record(address(BOB, PHONE, "2001:db8:1:2::5/128", false, 300));

			addresses.replace(ALICE, HOME, address(ALICE, "hash-of-home", "hash-of-network", true, 100));
			addresses.replace(BOB, PHONE, address(BOB, PHONE, PHONE_NETWORK, false, 300));

			List<AccountAddress> stored = sorted(addresses);
			assertEquals(2, stored.size());
			AccountAddress alice = stored.getFirst();
			assertEquals("hash-of-home", alice.getAddress());
			assertEquals(true, alice.isRegistration());
			assertEquals(50, alice.getFirstSeenAt());
			assertEquals(100, alice.getLastSeenAt());
			assertEquals(PHONE_NETWORK, stored.get(1).getNetwork());
		});
	}

	@DisplayName("Deletes the addresses of an account with the account")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void deletesTheAddressesWithTheAccount(TestDatabase database) {
		withServices(database, (accounts, addresses) -> {
			createAccounts(accounts);
			addresses.record(address(ALICE, HOME, HOME_NETWORK, true, 100));
			addresses.record(address(BOB, HOME, HOME_NETWORK, true, 100));

			accounts.delete(ALICE);

			assertEquals(List.of(BOB + " " + HOME), rows(addresses));
		});
	}

	private static void createAccounts(DefaultAccountPersistenceService accounts) {
		accounts.create(TestDataFactory.account(ALICE, "Alice"), AccountUsernameSource.PROVIDER, null);
		accounts.create(TestDataFactory.account(BOB, "Bob"), AccountUsernameSource.PROVIDER, null);
	}

	private static AccountAddress address(UUID uniqueId, String address, String network, boolean registration, long at) {
		return AccountAddress.builder()
				.uniqueId(uniqueId)
				.address(address)
				.network(network)
				.registration(registration)
				.firstSeenAt(at)
				.lastSeenAt(at)
				.build();
	}

	private static List<AccountAddress> sorted(DefaultAccountAddressPersistenceService addresses) {
		return addresses.findAll().stream()
				.sorted(Comparator.comparing(AccountAddress::getUniqueId).thenComparing(AccountAddress::getAddress))
				.toList();
	}

	private static List<String> rows(DefaultAccountAddressPersistenceService addresses) {
		return sorted(addresses).stream().map(row -> row.getUniqueId() + " " + row.getAddress()).toList();
	}

	private static void withServices(TestDatabase database, Scenario scenario) {
		database.reset();
		DefaultDatabaseService service = database.open();
		try {
			Jdbi jdbi = service.getJdbi();
			scenario.run(PersistenceServices.accounts(jdbi), PersistenceServices.addresses(jdbi));
		} finally {
			TestDatabase.close(service);
		}
	}

	@FunctionalInterface
	private interface Scenario {
		void run(DefaultAccountPersistenceService accounts, DefaultAccountAddressPersistenceService addresses);
	}
}
