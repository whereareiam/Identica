package me.whereareiam.identica.provider.credential.database;

import com.google.inject.util.Providers;
import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.DefaultDatabaseService;
import me.whereareiam.identica.adapter.database.testing.SimultaneousCalls;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabase;
import me.whereareiam.identica.provider.credential.database.entity.CredentialAccountEntity;
import me.whereareiam.identica.provider.credential.database.entity.CredentialAccountHistoryEntity;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.model.CredentialAccountHistory;
import me.whereareiam.identica.provider.credential.type.PasswordChangeReason;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Credential persistence")
@Tag("database")
class DefaultCredentialAccountPersistenceServiceTest {
	private static final int SUBJECTS = 20;

	@DisplayName("Stores a credential, changes its password, records the change and removes it")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void storesCredentials(TestDatabase database) {
		withCredentials(database, (service, jdbi) -> {
			service.create(new CredentialAccount("credential", "alice", "hash", "ARGON2", 10L, 10L));
			service.create(new CredentialAccount("credential", "bob", "hash", "ARGON2", 10L, 10L));
			assertEquals("hash", service.create(new CredentialAccount("credential", "alice", "other", "BCRYPT", 20L, 20L)).getPasswordHash());

			service.updatePassword("credential", "alice", "changed", "BCRYPT", 30L);
			service.recordPasswordChange(new CredentialAccountHistory("credential", "alice", "BCRYPT", PasswordChangeReason.CHANGE, 30L));

			CredentialAccount stored = service.findBySubject("credential", "alice").orElseThrow();
			assertEquals("changed", stored.getPasswordHash());
			assertEquals("BCRYPT", stored.getHashingMethod());
			assertEquals(10L, stored.getCreatedAt());
			assertEquals(30L, stored.getUpdatedAt());
			CredentialAccount untouched = service.findBySubject("credential", "bob").orElseThrow();
			assertEquals("hash", untouched.getPasswordHash());
			assertEquals("ARGON2", untouched.getHashingMethod());
			assertEquals(10L, untouched.getUpdatedAt());
			service.updatePassword("credential", "nobody", "changed", "BCRYPT", 30L);
			assertTrue(service.findBySubject("credential", "nobody").isEmpty());
			long changes = jdbi.withHandle(handle -> handle.createQuery("SELECT COUNT(*) FROM identica_provider_credential_accounts_history")
					.mapTo(Long.class)
					.one());
			assertEquals(1L, changes);

			service.delete("credential", "alice");

			assertTrue(service.findBySubject("credential", "alice").isEmpty());
		});
	}

	@DisplayName("Two proxies creating the same credential at once both succeed and both see the one that was stored")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void createsTheSameCredentialAtOnce(TestDatabase database) {
		withCredentials(database, (service, jdbi) -> {
			for (int round = 0; round < SUBJECTS; round++) {
				String subject = "subject-" + round;
				List<String> written = List.of(subject + "-a", subject + "-b");

				List<CredentialAccount> created = SimultaneousCalls.run(2, party -> service.create(
						new CredentialAccount("credential", subject, written.get(party), "ARGON2", 10L, 10L)
				));

				String stored = service.findBySubject("credential", subject).orElseThrow().getPasswordHash();
				assertTrue(written.contains(stored), "stored " + stored + " of " + written);
				assertEquals(List.of(stored, stored), created.stream().map(CredentialAccount::getPasswordHash).toList());
			}
		});
	}

	private static void withCredentials(TestDatabase database, Scenario scenario) {
		database.reset();
		DefaultDatabaseService databaseService = database.open();
		try {
			databaseService.getSchemaInstaller().install(new CredentialSchema());
			Jdbi jdbi = databaseService.getJdbi();
			scenario.run(new DefaultCredentialAccountPersistenceService(
					Providers.of(EntityAccess.of(jdbi, Dialectica.entity(CredentialAccountEntity.class))),
					Providers.of(EntityAccess.of(jdbi, Dialectica.entity(CredentialAccountHistoryEntity.class)))
			), jdbi);
		} finally {
			TestDatabase.close(databaseService);
		}
	}

	@FunctionalInterface
	private interface Scenario {
		void run(DefaultCredentialAccountPersistenceService service, Jdbi jdbi);
	}
}
