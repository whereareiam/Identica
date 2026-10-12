package me.whereareiam.identica.adapter.database.integration;

import com.google.inject.util.Providers;
import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.DefaultDatabaseService;
import me.whereareiam.identica.adapter.database.account.DefaultAccountPersistenceService;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import me.whereareiam.identica.adapter.database.entity.account.AccountProviderLinkEntity;
import me.whereareiam.identica.adapter.database.entity.account.AccountProviderProfileEntity;
import me.whereareiam.identica.adapter.database.provider.DefaultProviderLinkPersistenceService;
import me.whereareiam.identica.adapter.database.provider.DefaultProviderProfilePersistenceService;
import me.whereareiam.identica.adapter.database.repository.account.AccountRepository;
import me.whereareiam.identica.adapter.database.repository.provider.ProviderLinkRepository;
import me.whereareiam.identica.adapter.database.testing.SimultaneousCalls;
import me.whereareiam.identica.adapter.database.testing.TestDataFactory;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabase;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
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

@DisplayName("Provider profile upsert")
class ProviderProfileUpsertIntegrationTest {
	private static final int SUBJECTS = 20;

	@DisplayName("Stores a new profile and replaces a known one, leaving other subjects alone")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void storesAndReplacesProfiles(TestDatabase database) {
		withProfiles(database, List.of("alice", "bob"), profiles -> {
			profiles.upsert(TestDataFactory.providerProfile("premium", "alice", "Alice"));
			profiles.upsert(TestDataFactory.providerProfile("premium", "bob", "Bob"));

			AccountProviderProfile replaced = profiles.upsert(TestDataFactory.providerProfile("premium", "alice", "Alicia"));

			assertEquals("Alicia", replaced.getProviderUsername());
			assertEquals("Alicia", profiles.findBySubject("premium", "alice").orElseThrow().getProviderUsername());
			assertEquals("Bob", profiles.findBySubject("premium", "bob").orElseThrow().getProviderUsername());
		});
	}

	@DisplayName("Two proxies saving the same new profile at once both succeed, and one of their profiles is kept")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void savesTheSameProfileAtOnce(TestDatabase database) {
		List<String> subjects = IntStream.range(0, SUBJECTS).mapToObj(index -> "subject-" + index).toList();
		withProfiles(database, subjects, profiles -> {
			for (String subject : subjects) {
				List<String> written = List.of(subject + "-a", subject + "-b");

				SimultaneousCalls.run(2, party -> profiles.upsert(
						TestDataFactory.providerProfile("premium", subject, written.get(party))
				));

				String kept = profiles.findBySubject("premium", subject).orElseThrow().getProviderUsername();
				assertTrue(written.contains(kept), "kept " + kept + " of " + written);
			}
		});
	}

	private static void withProfiles(TestDatabase database, List<String> subjects, Scenario scenario) {
		database.reset();
		DefaultDatabaseService service = database.open();
		try {
			Jdbi jdbi = service.getJdbi();
			DefaultAccountPersistenceService accounts = new DefaultAccountPersistenceService(
					Providers.of(EntityAccess.of(jdbi, Dialectica.entity(AccountEntity.class))),
					Providers.of(jdbi.onDemand(AccountRepository.class)),
					Mockito.mock(EventManager.class)
			);
			DefaultProviderLinkPersistenceService links = new DefaultProviderLinkPersistenceService(
					Providers.of(EntityAccess.of(jdbi, Dialectica.entity(AccountProviderLinkEntity.class))),
					Providers.of(jdbi.onDemand(ProviderLinkRepository.class)),
					Mockito.mock(EventManager.class)
			);
			for (String subject : subjects) {
				UUID uniqueId = UUID.randomUUID();
				accounts.create(TestDataFactory.account(uniqueId, subject));
				links.upsert(TestDataFactory.providerLink(uniqueId, "premium", subject, true));
			}

			scenario.run(new DefaultProviderProfilePersistenceService(
					Providers.of(EntityAccess.of(jdbi, Dialectica.entity(AccountProviderProfileEntity.class)))
			));
		} finally {
			TestDatabase.close(service);
		}
	}

	@FunctionalInterface
	private interface Scenario {
		void run(DefaultProviderProfilePersistenceService profiles);
	}
}
