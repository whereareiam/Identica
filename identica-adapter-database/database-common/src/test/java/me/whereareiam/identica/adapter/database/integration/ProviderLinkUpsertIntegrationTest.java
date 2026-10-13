package me.whereareiam.identica.adapter.database.integration;

import com.google.inject.util.Providers;
import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.dialectica.exception.KeyConflictException;
import me.whereareiam.identica.adapter.database.DefaultDatabaseService;
import me.whereareiam.identica.adapter.database.account.DefaultAccountPersistenceService;
import me.whereareiam.identica.adapter.database.entity.account.AccountProviderLinkEntity;
import me.whereareiam.identica.adapter.database.provider.DefaultProviderLinkPersistenceService;
import me.whereareiam.identica.adapter.database.repository.provider.ProviderLinkRepository;
import me.whereareiam.identica.adapter.database.testing.SimultaneousCalls;
import me.whereareiam.identica.adapter.database.testing.PersistenceServices;
import me.whereareiam.identica.adapter.database.testing.TestDataFactory;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabase;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Provider link upsert")
class ProviderLinkUpsertIntegrationTest {
	private static final int ACCOUNTS = 20;

	@DisplayName("Saving a known link changes only whether it is primary and when it was last seen")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void updatesAKnownLink(TestDatabase database) {
		withLinks(database, 2, (links, accounts) -> {
			UUID alice = accounts.get(0);
			UUID bob = accounts.get(1);
			links.upsert(TestDataFactory.providerLink(alice, "premium", "alice", false));
			links.upsert(TestDataFactory.providerLink(bob, "premium", "bob", false));

			AccountProviderLink saved = links.upsert(TestDataFactory.providerLink(alice, "premium", "alice", true)
					.toBuilder()
					.linkedAt(1L)
					.lastSeenAt(9_000L)
					.build());

			AccountProviderLink stored = links.findBySubject("premium", "alice").orElseThrow();
			assertEquals(alice, stored.getUniqueId());
			assertTrue(stored.isPrimaryLink());
			assertEquals(TestDataFactory.LINKED_AT, stored.getLinkedAt());
			assertEquals(9_000L, stored.getLastSeenAt());
			assertEquals(alice, saved.getUniqueId());
			assertTrue(saved.isPrimaryLink());
			assertEquals(TestDataFactory.LINKED_AT, saved.getLinkedAt());
			assertEquals(9_000L, saved.getLastSeenAt());
			AccountProviderLink untouched = links.findBySubject("premium", "bob").orElseThrow();
			assertFalse(untouched.isPrimaryLink());
			assertEquals(TestDataFactory.LAST_SEEN_AT, untouched.getLastSeenAt());
		});
	}

	@DisplayName("Refuses a second link of an account to the same provider and keeps the first")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void refusesASecondLinkToTheSameProvider(TestDatabase database) {
		withLinks(database, 1, (links, accounts) -> {
			UUID alice = accounts.getFirst();
			links.upsert(TestDataFactory.providerLink(alice, "premium", "alice", true));

			assertThrows(KeyConflictException.class,
					() -> links.upsert(TestDataFactory.providerLink(alice, "premium", "other", false)));

			assertTrue(links.findBySubject("premium", "other").isEmpty());
			AccountProviderLink kept = links.findBySubject("premium", "alice").orElseThrow();
			assertEquals(alice, kept.getUniqueId());
			assertTrue(kept.isPrimaryLink());
		});
	}

	@DisplayName("Two proxies saving the same new link at once both succeed, and one link is stored")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void savesTheSameLinkAtOnce(TestDatabase database) {
		withLinks(database, ACCOUNTS, (links, accounts) -> {
			for (UUID uniqueId : accounts) {
				String subject = uniqueId.toString();
				List<Long> written = List.of(1_000L, 2_000L);

				List<AccountProviderLink> saved = SimultaneousCalls.run(2, party -> links.upsert(
						TestDataFactory.providerLink(uniqueId, "premium", subject, true)
								.toBuilder()
								.lastSeenAt(written.get(party))
								.build()
				));

				AccountProviderLink stored = links.findBySubject("premium", subject).orElseThrow();
				assertEquals(uniqueId, stored.getUniqueId());
				assertTrue(written.contains(stored.getLastSeenAt()), "kept " + stored.getLastSeenAt() + " of " + written);
				assertEquals(1, links.findByUniqueId(uniqueId).size());
				saved.forEach(link -> assertEquals(uniqueId, link.getUniqueId()));
			}
		});
	}

	@DisplayName("Of two proxies linking an account to one provider under different subjects at once, exactly one succeeds")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void linksAnAccountOnceAcrossProxies(TestDatabase database) {
		withLinks(database, ACCOUNTS, (links, accounts) -> {
			for (UUID uniqueId : accounts) {
				List<String> subjects = List.of(uniqueId + "-a", uniqueId + "-b");

				List<Boolean> linked = SimultaneousCalls.run(2, party -> {
					try {
						links.upsert(TestDataFactory.providerLink(uniqueId, "premium", subjects.get(party), true));
						return true;
					} catch (KeyConflictException e) {
						return false;
					}
				});

				List<Integer> winners = IntStream.range(0, 2).filter(linked::get).boxed().toList();
				assertEquals(1, winners.size(), "linked on " + winners);
				List<AccountProviderLink> stored = links.findByUniqueId(uniqueId);
				assertEquals(1, stored.size());
				assertEquals(subjects.get(winners.getFirst()), stored.getFirst().getProviderSubject());
			}
		});
	}

	private static void withLinks(TestDatabase database, int count, Scenario scenario) {
		database.reset();
		DefaultDatabaseService service = database.open();
		try {
			Jdbi jdbi = service.getJdbi();
			DefaultAccountPersistenceService accountService = PersistenceServices.accounts(jdbi);
			List<UUID> accounts = new ArrayList<>();
			for (int index = 0; index < count; index++) {
				UUID uniqueId = UUID.randomUUID();
				accountService.create(TestDataFactory.account(uniqueId, "Player" + index));
				accounts.add(uniqueId);
			}

			scenario.run(new DefaultProviderLinkPersistenceService(
					Providers.of(EntityAccess.of(jdbi, Dialectica.entity(AccountProviderLinkEntity.class))),
					Providers.of(jdbi.onDemand(ProviderLinkRepository.class)),
					Mockito.mock(EventManager.class)
			), accounts);
		} finally {
			TestDatabase.close(service);
		}
	}

	@FunctionalInterface
	private interface Scenario {
		void run(DefaultProviderLinkPersistenceService links, List<UUID> accounts);
	}
}
