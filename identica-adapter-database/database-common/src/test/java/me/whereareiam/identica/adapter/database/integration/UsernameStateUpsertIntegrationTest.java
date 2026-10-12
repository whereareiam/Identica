package me.whereareiam.identica.adapter.database.integration;

import com.google.inject.util.Providers;
import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.DefaultDatabaseService;
import me.whereareiam.identica.adapter.database.account.DefaultAccountPersistenceService;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import me.whereareiam.identica.adapter.database.entity.username.UsernameStateEntity;
import me.whereareiam.identica.adapter.database.repository.account.AccountRepository;
import me.whereareiam.identica.adapter.database.testing.SimultaneousCalls;
import me.whereareiam.identica.adapter.database.testing.TestDataFactory;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabase;
import me.whereareiam.identica.adapter.database.username.DefaultAccountUsernameStatePersistenceService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Username state upsert")
class UsernameStateUpsertIntegrationTest {
	private static final int ACCOUNTS = 20;
	private static final List<AccountUsernameSource> WRITTEN = List.of(AccountUsernameSource.MANUAL, AccountUsernameSource.SYSTEM);

	@DisplayName("Stores a new state and replaces a known one, leaving other accounts alone")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void storesAndReplacesStates(TestDatabase database) {
		withStates(database, 2, (states, accounts) -> {
			states.save(accounts.get(0), AccountUsernameSource.PROVIDER);
			states.save(accounts.get(1), AccountUsernameSource.SYSTEM);

			states.save(accounts.get(0), AccountUsernameSource.MANUAL);

			assertEquals(AccountUsernameSource.MANUAL, states.find(accounts.get(0)).orElseThrow().getSource());
			assertEquals(AccountUsernameSource.SYSTEM, states.find(accounts.get(1)).orElseThrow().getSource());
		});
	}

	@DisplayName("Two proxies saving the same new state at once both succeed, and one of their states is kept")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void savesTheSameStateAtOnce(TestDatabase database) {
		withStates(database, ACCOUNTS, (states, accounts) -> {
			for (UUID uniqueId : accounts) {
				SimultaneousCalls.run(2, party -> {
					states.save(uniqueId, WRITTEN.get(party));
					return null;
				});

				AccountUsernameSource kept = states.find(uniqueId).orElseThrow().getSource();
				assertTrue(WRITTEN.contains(kept), "kept " + kept);
			}
		});
	}

	private static void withStates(TestDatabase database, int count, Scenario scenario) {
		database.reset();
		DefaultDatabaseService service = database.open();
		try {
			Jdbi jdbi = service.getJdbi();
			DefaultAccountPersistenceService accountService = new DefaultAccountPersistenceService(
					Providers.of(EntityAccess.of(jdbi, Dialectica.entity(AccountEntity.class))),
					Providers.of(jdbi.onDemand(AccountRepository.class)),
					Mockito.mock(EventManager.class)
			);
			List<UUID> accounts = IntStream.range(0, count).mapToObj(index -> UUID.randomUUID()).toList();
			for (UUID uniqueId : accounts) accountService.create(TestDataFactory.account(uniqueId, "Player" + accounts.indexOf(uniqueId)));

			scenario.run(new DefaultAccountUsernameStatePersistenceService(
					Providers.of(EntityAccess.of(jdbi, Dialectica.entity(UsernameStateEntity.class))),
					Mockito.mock(EventManager.class)
			), accounts);
		} finally {
			TestDatabase.close(service);
		}
	}

	@FunctionalInterface
	private interface Scenario {
		void run(DefaultAccountUsernameStatePersistenceService states, List<UUID> accounts);
	}
}
