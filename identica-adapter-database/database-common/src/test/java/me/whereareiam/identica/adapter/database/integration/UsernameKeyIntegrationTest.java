package me.whereareiam.identica.adapter.database.integration;

import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.DefaultDatabaseService;
import me.whereareiam.identica.adapter.database.account.DefaultAccountPersistenceService;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import me.whereareiam.identica.adapter.database.entity.username.UsernameEntity;
import me.whereareiam.identica.adapter.database.mapper.account.AccountMapper;
import me.whereareiam.identica.adapter.database.mapper.username.UsernameMapper;
import me.whereareiam.identica.adapter.database.testing.PersistenceServices;
import me.whereareiam.identica.adapter.database.testing.SimultaneousCalls;
import me.whereareiam.identica.adapter.database.testing.TestDataFactory;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabase;
import me.whereareiam.identica.adapter.database.username.DefaultAccountUsernameDuplicatePersistenceService;
import me.whereareiam.identica.adapter.database.username.DefaultAccountUsernamePersistenceService;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.username.AccountUsername;
import me.whereareiam.identica.model.identity.username.AccountUsernameDuplicate;
import me.whereareiam.identica.model.identity.username.AccountUsernameHistoryEntry;
import me.whereareiam.identica.model.identity.username.UsernameWriteResult;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Usernames are unique without regard to case through the key each account's username holds, while accounts from
 * before that rule that share a username go on without one.
 */
@DisplayName("Username keys")
@Tag("database")
class UsernameKeyIntegrationTest {
	private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID LOWERCASE_ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000c");

	@DisplayName("Creates an account with its username, its source and the provider it registered through")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void createsAnAccountWithItsUsername(TestDatabase database) {
		withServices(database, (accounts, usernames, duplicates, jdbi) -> {
			UsernameWriteResult result = accounts.create(TestDataFactory.account(ALICE, "Alice"), AccountUsernameSource.SYSTEM, "vouched");

			assertTrue(result.isWritten());
			assertEquals("Alice", accounts.findByUniqueId(ALICE).orElseThrow().getUsername());
			AccountUsername username = usernames.find(ALICE).orElseThrow();
			assertEquals("Alice", username.getUsername());
			assertEquals(AccountUsernameSource.SYSTEM, username.getSource());
			assertEquals("vouched", username.getProviderId());
			assertEquals(Map.of(ALICE, "alice"), keys(jdbi));
		});
	}

	@DisplayName("Creates no account whose username another account holds in another case, and names the holder")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void refusesAnAccountWhoseUsernameIsHeld(TestDatabase database) {
		withServices(database, (accounts, usernames, duplicates, jdbi) -> {
			accounts.create(TestDataFactory.account(ALICE, "Alice"), AccountUsernameSource.PROVIDER, null);

			UsernameWriteResult result = accounts.create(TestDataFactory.account(LOWERCASE_ALICE, "alice"), AccountUsernameSource.PROVIDER, null);

			assertFalse(result.isWritten());
			assertEquals(ALICE, result.getHolderUniqueId());
			assertTrue(accounts.findByUniqueId(LOWERCASE_ALICE).isEmpty());
			assertEquals(Map.of(ALICE, "alice"), keys(jdbi));
		});
	}

	@DisplayName("Of two accounts created at once with one username in two cases, exactly one is created")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void createsOneOfTwoSimultaneousAccountsOfAUsername(TestDatabase database) {
		withServices(database, (accounts, usernames, duplicates, jdbi) -> {
			List<String> names = List.of("Alice", "alice");
			List<UUID> uniqueIds = List.of(ALICE, LOWERCASE_ALICE);

			List<UsernameWriteResult> results = SimultaneousCalls.run(2, party ->
					accounts.create(TestDataFactory.account(uniqueIds.get(party), names.get(party)), AccountUsernameSource.PROVIDER, null));

			assertEquals(1, results.stream().filter(UsernameWriteResult::isWritten).count(), results.toString());
			Map<UUID, String> keys = keys(jdbi);
			assertEquals(1, keys.size(), keys.toString());
			UUID created = keys.keySet().iterator().next();
			UsernameWriteResult refused = results.stream().filter(result -> !result.isWritten()).findFirst().orElseThrow();
			assertEquals(created, refused.getHolderUniqueId());
			assertTrue(duplicates.findAll().isEmpty());
		});
	}

	@DisplayName("Finds every account of a username without regard to case, also outside ASCII")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void findsAUsernameInEveryCase(TestDatabase database) {
		withServices(database, (accounts, usernames, duplicates, jdbi) -> {
			accounts.create(TestDataFactory.account(ALICE, "Älice"), AccountUsernameSource.PROVIDER, null);
			shareUsername(jdbi, LOWERCASE_ALICE, "äLICE", 2_000L);
			accounts.create(TestDataFactory.account(BOB, "Bob"), AccountUsernameSource.PROVIDER, null);

			assertEquals(List.of(ALICE, LOWERCASE_ALICE), uniqueIds(accounts.findByUsername("ÄLICE")));
			assertEquals(List.of(BOB), uniqueIds(accounts.findByUsername("bOB")));
		});
	}

	@DisplayName("Finds the holder of a username: the account with its key, not one that shares it without a key")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void findsTheHolderOfAUsername(TestDatabase database) {
		withServices(database, (accounts, usernames, duplicates, jdbi) -> {
			shareUsername(jdbi, LOWERCASE_ALICE, "alice", 1_000L);
			accounts.create(TestDataFactory.account(ALICE, "Alice"), AccountUsernameSource.PROVIDER, null);

			assertEquals(ALICE, usernames.findHolder("ALICE").orElseThrow().getUniqueId());
			assertTrue(usernames.findHolder("Bob").isEmpty());
		});
	}

	@DisplayName("Renames an account with its key and history, and frees the old username for another account")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void renamesWithKeyAndHistory(TestDatabase database) {
		withServices(database, (accounts, usernames, duplicates, jdbi) -> {
			accounts.create(TestDataFactory.account(ALICE, "Alice"), AccountUsernameSource.PROVIDER, null);

			assertTrue(usernames.rename(change(ALICE, "Alice", "Alicia")).isWritten());
			accounts.create(TestDataFactory.account(LOWERCASE_ALICE, "alice"), AccountUsernameSource.PROVIDER, null);

			assertEquals(Map.of(ALICE, "alicia", LOWERCASE_ALICE, "alice"), keys(jdbi));
			AccountUsername renamed = usernames.find(ALICE).orElseThrow();
			assertEquals("Alicia", renamed.getUsername());
			assertEquals("vouched", renamed.getProviderId());
			assertEquals(5_000L, renamed.getChangedAt());
			assertEquals(List.of("Alice -> Alicia"), history(jdbi, ALICE));
			assertTrue(duplicates.findAll().isEmpty());
		});
	}

	@DisplayName("Leaves the username and the history as they are when the new username is held in another case")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void keepsTheUsernameOfARenameOntoAHeldOne(TestDatabase database) {
		withServices(database, (accounts, usernames, duplicates, jdbi) -> {
			accounts.create(TestDataFactory.account(ALICE, "Alice"), AccountUsernameSource.PROVIDER, null);
			accounts.create(TestDataFactory.account(BOB, "Bob"), AccountUsernameSource.PROVIDER, null);

			UsernameWriteResult result = usernames.rename(change(BOB, "Bob", "ALICE"));

			assertFalse(result.isWritten());
			assertEquals(ALICE, result.getHolderUniqueId());
			assertEquals("Bob", accounts.findByUniqueId(BOB).orElseThrow().getUsername());
			assertEquals(Map.of(ALICE, "alice", BOB, "bob"), keys(jdbi));
			assertEquals(List.of(), history(jdbi, BOB));
		});
	}

	@DisplayName("Changes the source of a username and keeps the username")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void changesTheSource(TestDatabase database) {
		withServices(database, (accounts, usernames, duplicates, jdbi) -> {
			accounts.create(TestDataFactory.account(ALICE, "Alice"), AccountUsernameSource.PROVIDER, null);

			usernames.setSource(ALICE, AccountUsernameSource.MANUAL);

			AccountUsername username = usernames.find(ALICE).orElseThrow();
			assertEquals(AccountUsernameSource.MANUAL, username.getSource());
			assertEquals("Alice", username.getUsername());
		});
	}

	@DisplayName("Lists the accounts of each shared username, oldest first, and no longer once one is left")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void listsSharedUsernames(TestDatabase database) {
		withServices(database, (accounts, usernames, duplicates, jdbi) -> {
			accounts.create(account(LOWERCASE_ALICE, "alice", 1_000L), AccountUsernameSource.PROVIDER, null);
			shareUsername(jdbi, ALICE, "Alice", 2_000L);
			accounts.create(account(BOB, "Bob", 3_000L), AccountUsernameSource.PROVIDER, null);

			List<AccountUsernameDuplicate> shared = duplicates.findAll();
			assertEquals(1, shared.size());
			assertEquals("alice", shared.getFirst().getUsername());
			assertEquals(List.of(LOWERCASE_ALICE, ALICE), shared.getFirst().getAccounts().stream().map(Account::getUniqueId).toList());

			accounts.delete(LOWERCASE_ALICE);

			assertTrue(duplicates.findAll().isEmpty());
			assertTrue(usernames.find(LOWERCASE_ALICE).isEmpty());
		});
	}

	/**
	 * Stores an account from before usernames were unique, which shares its username with an older account and has
	 * no key.
	 */
	private static void shareUsername(Jdbi jdbi, UUID uniqueId, String username, long createdAt) {
		Account account = account(uniqueId, username, createdAt);
		EntityAccess.of(jdbi, Dialectica.entity(AccountEntity.class)).insert(AccountMapper.toEntity(account));
		EntityAccess.of(jdbi, Dialectica.entity(UsernameEntity.class)).insert(
				UsernameMapper.toEntity(account, AccountUsernameSource.PROVIDER, null).toBuilder().usernameKey(null).build());
	}

	private static AccountUsernameHistoryEntry change(UUID uniqueId, String oldUsername, String newUsername) {
		return AccountUsernameHistoryEntry.builder()
				.uniqueId(uniqueId)
				.providerId("vouched")
				.oldUsername(oldUsername)
				.newUsername(newUsername)
				.source(AccountUsernameSource.PROVIDER)
				.changedAt(5_000L)
				.build();
	}

	private static Account account(UUID uniqueId, String username, long createdAt) {
		return TestDataFactory.account(uniqueId, username).toBuilder().createdAt(createdAt).build();
	}

	private static List<UUID> uniqueIds(List<Account> accounts) {
		return accounts.stream().map(Account::getUniqueId).sorted().toList();
	}

	private static Map<UUID, String> keys(Jdbi jdbi) {
		Map<UUID, String> keys = new TreeMap<>();
		EntityAccess<UsernameEntity> rows = EntityAccess.of(jdbi, Dialectica.entity(UsernameEntity.class));
		for (UUID uniqueId : List.of(ALICE, LOWERCASE_ALICE, BOB))
			rows.find(uniqueId).ifPresent(row -> keys.put(uniqueId, row.getUsernameKey()));

		return keys;
	}

	private static List<String> history(Jdbi jdbi, UUID uniqueId) {
		return jdbi.withHandle(handle -> handle.createQuery(
						"SELECT old_username, new_username FROM identica_account_username_history WHERE unique_id = '" + uniqueId + "' ORDER BY changed_at")
				.map((row, context) -> row.getString(1) + " -> " + row.getString(2))
				.list());
	}

	private static void withServices(TestDatabase database, Scenario scenario) {
		database.reset();
		DefaultDatabaseService service = database.open();
		try {
			Jdbi jdbi = service.getJdbi();
			scenario.run(PersistenceServices.accounts(jdbi), PersistenceServices.usernames(jdbi), PersistenceServices.duplicates(jdbi), jdbi);
		} finally {
			TestDatabase.close(service);
		}
	}

	@FunctionalInterface
	private interface Scenario {
		void run(
				DefaultAccountPersistenceService accounts,
				DefaultAccountUsernamePersistenceService usernames,
				DefaultAccountUsernameDuplicatePersistenceService duplicates,
				Jdbi jdbi
		);
	}
}
