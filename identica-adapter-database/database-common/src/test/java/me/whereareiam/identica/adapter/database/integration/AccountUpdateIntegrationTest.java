package me.whereareiam.identica.adapter.database.integration;

import com.google.inject.util.Providers;
import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.DefaultDatabaseService;
import me.whereareiam.identica.adapter.database.account.DefaultAccountPersistenceService;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import me.whereareiam.identica.adapter.database.repository.account.AccountRepository;
import me.whereareiam.identica.adapter.database.testing.TestDataFactory;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabase;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.model.identity.Account;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Account updates")
class AccountUpdateIntegrationTest {
	@DisplayName("Changes the last-seen time of one account and nothing else")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void updatesLastSeen(TestDatabase database) {
		withAccounts(database, (accounts, alice, bob) -> {
			accounts.updateLastSeen(alice, 5_000L);

			Account updated = accounts.findByUniqueId(alice).orElseThrow();
			assertEquals(5_000L, updated.getLastSeenAt());
			assertEquals("Alice", updated.getUsername());
			assertEquals(TestDataFactory.CREATED_AT, updated.getCreatedAt());
			assertEquals(TestDataFactory.LAST_SEEN_AT, accounts.findByUniqueId(bob).orElseThrow().getLastSeenAt());
		});
	}

	@DisplayName("Changes the username of one account and nothing else")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void updatesUsername(TestDatabase database) {
		withAccounts(database, (accounts, alice, bob) -> {
			accounts.updateUsername(alice, "Alicia");

			Account updated = accounts.findByUniqueId(alice).orElseThrow();
			assertEquals("Alicia", updated.getUsername());
			assertEquals(TestDataFactory.CREATED_AT, updated.getCreatedAt());
			assertEquals(TestDataFactory.LAST_SEEN_AT, updated.getLastSeenAt());
			assertEquals("Bob", accounts.findByUniqueId(bob).orElseThrow().getUsername());
		});
	}

	@DisplayName("Creates no account when updating one that does not exist")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void ignoresMissingAccounts(TestDatabase database) {
		withAccounts(database, (accounts, alice, bob) -> {
			UUID missing = UUID.randomUUID();

			accounts.updateLastSeen(missing, 5_000L);
			accounts.updateUsername(missing, "Nobody");

			assertTrue(accounts.findByUniqueId(missing).isEmpty());
			assertEquals(2L, accounts.count());
		});
	}

	private static void withAccounts(TestDatabase database, Scenario scenario) {
		database.reset();
		DefaultDatabaseService service = database.open();
		try {
			Jdbi jdbi = service.getJdbi();
			DefaultAccountPersistenceService accounts = new DefaultAccountPersistenceService(
					Providers.of(EntityAccess.of(jdbi, Dialectica.entity(AccountEntity.class))),
					Providers.of(jdbi.onDemand(AccountRepository.class)),
					Mockito.mock(EventManager.class)
			);
			UUID alice = UUID.randomUUID();
			UUID bob = UUID.randomUUID();
			accounts.create(TestDataFactory.account(alice, "Alice"));
			accounts.create(TestDataFactory.account(bob, "Bob"));

			scenario.run(accounts, alice, bob);
		} finally {
			TestDatabase.close(service);
		}
	}

	@FunctionalInterface
	private interface Scenario {
		void run(DefaultAccountPersistenceService accounts, UUID alice, UUID bob);
	}
}
